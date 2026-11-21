package com.sudan.voicekeyboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("sudan_voice_prefs", Context.MODE_PRIVATE)
        val defaultPrompt = "Transcribe the audio verbatim. Keep the exact Sudanese Arabic dialect (NEVER convert to Modern Standard Arabic / الفصحى). Keep all English words in English script. Clean up stutters and filler sounds only. Output ONLY the final text."

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "إعدادات كيبورد الصوت السوداني"
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }

        val apiLabel = TextView(this).apply { text = "مفتاح Google AI Studio API Key:" }
        val apiInput = EditText(this).apply {
            hint = "الصق المفتاح هنا (AQ... أو AIza...)"
            setText(prefs.getString("api_key", ""))
        }

        val promptLabel = TextView(this).apply {
            text = "تعليمات التفريغ (System Instructions):"
            setPadding(0, 24, 0, 0)
        }
        val promptInput = EditText(this).apply {
            minLines = 4
            gravity = Gravity.TOP
            setText(prefs.getString("system_prompt", defaultPrompt))
        }

        val saveBtn = Button(this).apply {
            text = "1. حفظ الإعدادات"
            setOnClickListener {
                prefs.edit()
                    .putString("api_key", apiInput.text.toString().trim())
                    .putString("system_prompt", promptInput.text.toString().trim())
                    .apply()
                Toast.makeText(this@MainActivity, "تم الحفظ بنجاح!", Toast.LENGTH_SHORT).show()
            }
        }

        val permBtn = Button(this).apply {
            text = "2. السماح بالمايكروفون"
            setOnClickListener {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
                } else {
                    Toast.makeText(this@MainActivity, "المايكروفون مفعّل مسبقاً", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val enableBtn = Button(this).apply {
            text = "3. تفعيل الكيبورد من إعدادات النظام"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val switchBtn = Button(this).apply {
            text = "4. اختيار الكيبورد الآن"
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        layout.addView(title)
        layout.addView(apiLabel)
        layout.addView(apiInput)
        layout.addView(promptLabel)
        layout.addView(promptInput)
        layout.addView(saveBtn)
        layout.addView(permBtn)
        layout.addView(enableBtn)
        layout.addView(switchBtn)

        setContentView(ScrollView(this).apply { addView(layout) })
    }
}
