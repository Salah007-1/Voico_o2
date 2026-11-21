package com.sudan.voicekeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class VoiceImeService : InputMethodService() {

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private lateinit var micButton: Button
    private lateinit var statusText: TextView

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(24, 24, 24, 32)
        }

        statusText = TextView(this).apply {
            text = "جاهز للتسجيل (سوداني + English)"
            setTextColor(Color.LTGRAY)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val micBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 40f
            setColor(Color.parseColor("#0B57D0"))
        }

        micButton = Button(this).apply {
            text = "🎙️ اضغط للتحدث"
            setTextColor(Color.WHITE)
            textSize = 18f
            background = micBg
            setPadding(32, 36, 32, 36)
            setOnClickListener { toggleRecording() }
        }

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        val switchBtn = createControlBtn("🌐 كيبورد") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        val spaceBtn = createControlBtn("مسافة") {
            currentInputConnection?.commitText(" ", 1)
        }
        val backspaceBtn = createControlBtn("⌫ مسح") {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        val enterBtn = createControlBtn("↵ إرسال") {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }

        controlsRow.addView(switchBtn)
        controlsRow.addView(spaceBtn)
        controlsRow.addView(backspaceBtn)
        controlsRow.addView(enterBtn)

        root.addView(statusText)
        root.addView(micButton)
        root.addView(controlsRow)

        return root
    }

    private fun createControlBtn(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            setBackgroundColor(Color.parseColor("#333333"))
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(6, 0, 6, 0)
            }
            layoutParams = params
            setOnClickListener { onClick() }
        }
    }

    private fun toggleRecording() {
        if (!isRecording) startRecording() else stopAndTranscribe()
    }

    private fun startRecording() {
        try {
            audioFile = File(cacheDir, "voice_input.m4a")
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            micButton.text = "⏹️ إيقاف وإرسال"
            (micButton.background as GradientDrawable).setColor(Color.parseColor("#B3261E"))
            statusText.text = "جاري الاستماع..."
        } catch (e: Exception) {
            statusText.text = "خطأ في المايك: تأكد من الصلاحية في التطبيق"
        }
    }

    private fun stopAndTranscribe() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        isRecording = false

        micButton.text = "⏳ جاري التفريغ..."
        (micButton.background as GradientDrawable).setColor(Color.parseColor("#555555"))
        micButton.isEnabled = false
        statusText.text = "يتم الإرسال إلى Gemini..."

        val file = audioFile ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { sendToGemini(file) }
            if (result.startsWith("ERROR:")) {
                statusText.text = result
            } else {
                currentInputConnection?.commitText("$result ", 1)
                statusText.text = "تم الإدخال بنجاح"
            }
            micButton.text = "🎙️ اضغط للتحدث"
            (micButton.background as GradientDrawable).setColor(Color.parseColor("#0B57D0"))
            micButton.isEnabled = true
        }
    }

    private fun sendToGemini(file: File): String {
        val prefs = getSharedPreferences("sudan_voice_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val systemPrompt = prefs.getString("system_prompt", "") ?: ""

        if (apiKey.isEmpty()) return "ERROR: أدخل API Key في التطبيق أولاً"

        return try {
            val audioBytes = file.readBytes()
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            val jsonBody = JSONObject().apply {
                if (systemPrompt.isNotEmpty()) {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })
                }
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "audio/mp4")
                                put("data", base64Audio)
                            })
                        })
                    })
                }))
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return "ERROR: ${response.code} - تأكد من المفتاح والإنترنت"
                }
                val rootObj = JSONObject(bodyStr)
                val text = rootObj
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
                text
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

