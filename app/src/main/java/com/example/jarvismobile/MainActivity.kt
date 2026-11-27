package com.example.jarvismobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var pvKey: EditText
    private lateinit var aiKey: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("jarvis", MODE_PRIVATE)
        val pad = (18 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(android.graphics.Color.rgb(5, 11, 18))
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(84, 231, 255))
            setPadding(0, pad, 0, pad / 2)
        }
        fun field(hint: String, secret: Boolean = false) = EditText(this).apply {
            this.hint = hint
            setSingleLine(true)
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            if (secret) inputType = 0x81
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
        }
        val title = label("J.A.R.V.I.S.  //  MOBILE ASSISTANT").apply { textSize = 21f }
        layout.addView(title)
        layout.addView(label("Picovoice AccessKey — required for offline wake-word detection"))
        pvKey = field("Paste your Picovoice AccessKey", true)
        pvKey.setText(prefs.getString("pv_key", ""))
        layout.addView(pvKey)
        layout.addView(label("AI API Key — required for intelligent answers"))
        aiKey = field("Paste your private AI API key", true)
        aiKey.setText(prefs.getString("ai_key", ""))
        layout.addView(aiKey)
        layout.addView(label("Wake phrase: JARVIS  •  Built-in offline keyword"))
        status = label("Status: stopped").apply { textSize = 13f }
        layout.addView(status)
        val start = Button(this).apply { text = "START ALWAYS-ON JARVIS" }
        val stop = Button(this).apply { text = "STOP LISTENING" }
        val save = Button(this).apply { text = "SAVE KEYS ON THIS PHONE" }
        val sample = Button(this).apply { text = "PLAY JARVIS VOICE SAMPLE" }
        val notifAccess = Button(this).apply { text = "ENABLE NOTIFICATION READING" }
        listOf(save, start, stop, notifAccess, sample).forEach { layout.addView(it) }
        notifAccess.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        val info = TextView(this).apply {
            text = "When enabled, Android shows an ongoing microphone notification. Say “Jarvis”, wait for the listening prompt, then speak. The wake detector runs on-device; recognized commands and AI requests are processed as configured. Keep your API keys private."
            setTextColor(android.graphics.Color.LTGRAY)
            textSize = 12f
            setPadding(0, pad, 0, 0)
        }
        layout.addView(info)
        setContentView(layout)

        save.setOnClickListener {
            prefs.edit().putString("pv_key", pvKey.text.toString().trim())
                .putString("ai_key", aiKey.text.toString().trim()).apply()
            Toast.makeText(this, "Keys saved on this phone", Toast.LENGTH_SHORT).show()
        }
        start.setOnClickListener {
            prefs.edit().putString("pv_key", pvKey.text.toString().trim())
                .putString("ai_key", aiKey.text.toString().trim()).apply()
            if (pvKey.text.isBlank()) {
                Toast.makeText(this, "Add your Picovoice AccessKey first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            val missing = permissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), 51)
            } else startWakeService()
        }
        stop.setOnClickListener {
            stopService(Intent(this, WakeWordService::class.java))
            status.text = "Status: stopped"
        }
        sample.setOnClickListener {
            Toast.makeText(this, "Voice sample playback can be added to this native build from your supplied MP3.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startWakeService() {
        val intent = Intent(this, WakeWordService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        status.text = "Status: wake-word service requested"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 51 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startWakeService()
        } else {
            Toast.makeText(this, "Microphone and notification permissions are needed", Toast.LENGTH_LONG).show()
        }
    }
}
