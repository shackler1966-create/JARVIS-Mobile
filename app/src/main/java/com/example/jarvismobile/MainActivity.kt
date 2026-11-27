package com.example.jarvismobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var aiKey: EditText
    private lateinit var status: TextView
    private lateinit var commandInput: EditText
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("jarvis", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val pad = (18 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(android.graphics.Color.rgb(5, 11, 18))
        }
        fun label(text: String, size: Float = 14f) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(android.graphics.Color.rgb(84, 231, 255))
            setPadding(0, pad / 2, 0, pad / 2)
        }
        fun button(text: String) = Button(this).apply { this.text = text }
        val title = label("J.A.R.V.I.S.  //  MOBILE ASSISTANT", 22f)
        layout.addView(title)
        layout.addView(label("No Picovoice account required. Tap the microphone button when you want to speak."))
        layout.addView(label("OpenAI API Key — required for intelligent answers"))
        aiKey = EditText(this).apply {
            hint = "Paste your private AI API key"
            setSingleLine(true)
            inputType = 0x81
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            setText(prefs.getString("ai_key", ""))
        }
        layout.addView(aiKey)
        status = label("Status: ready")
        layout.addView(status)
        val save = button("SAVE API KEY ON THIS PHONE")
        val listen = button("🎙 TAP TO TALK TO JARVIS")
        val send = button("SEND TEXT COMMAND")
        val stop = button("STOP SPEAKING")
        val notif = button("ENABLE NOTIFICATION READING")
        layout.addView(save)
        layout.addView(listen)
        commandInput = EditText(this).apply {
            hint = "Or type a command here"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }
        layout.addView(commandInput)
        layout.addView(send)
        layout.addView(stop)
        layout.addView(notif)
        layout.addView(label("Commands: افتح واتساب • افتح يوتيوب • افتح الكاميرا • ارفع الصوت • اخفض الصوت • اقرأ الإشعارات • اتصل [اسم جهة الاتصال]"))
        layout.addView(TextView(this).apply {
            text = "Important: this edition does not listen continuously in the background. Android starts speech recognition only after you tap the microphone button. This avoids Picovoice registration and reduces background microphone use. Speech recognition may use your phone's speech service and internet."
            setTextColor(android.graphics.Color.LTGRAY)
            textSize = 12f
            setPadding(0, pad, 0, 0)
        })
        val scroll = ScrollView(this).apply { addView(layout) }
        setContentView(scroll)

        save.setOnClickListener { saveKey(); Toast.makeText(this, "API key saved on this phone", Toast.LENGTH_SHORT).show() }
        listen.setOnClickListener { saveKey(); startVoiceInput() }
        send.setOnClickListener { saveKey(); val text = commandInput.text.toString().trim(); if (text.isNotEmpty()) processCommand(text) }
        stop.setOnClickListener { tts?.stop(); speech?.cancel(); status.text = "Status: stopped" }
        notif.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    private fun saveKey() { prefs.edit().putString("ai_key", aiKey.text.toString().trim()).apply() }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Status: speech recognition unavailable"
            speak("Speech recognition is not available on this phone.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 72)
            return
        }
        try {
            speech?.destroy()
            speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "Status: listening — speak now" }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) { status.text = "Status: no speech detected"; speak("لم أسمع أمراً واضحاً.") }
                    else { commandInput.setText(text); processCommand(text) }
                }
                override fun onError(error: Int) { status.text = "Status: speech error ($error) — tap to try again" }
                override fun onBeginningOfSpeech() { status.text = "Status: hearing you" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "Status: processing speech" }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speech?.startListening(intent)
        } catch (e: Exception) {
            status.text = "Status: could not start microphone"
            Toast.makeText(this, e.message ?: "Speech error", Toast.LENGTH_LONG).show()
        }
    }

    private fun processCommand(text: String) {
        if (handlePhoneCommand(text)) return
        val key = prefs.getString("ai_key", "").orEmpty()
        if (key.isBlank()) {
            status.text = "Status: add your AI API key first"
            speak("أضف مفتاح الذكاء الاصطناعي أولاً.")
            return
        }
        status.text = "Status: contacting AI…"
        worker.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json")
                }
                val payload = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "You are JARVIS, a concise helpful personal voice assistant. Reply naturally and briefly."))
                        .put(JSONObject().put("role", "user").put("content", text)))
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) throw Exception("AI API error $code: $body")
                val reply = JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                runOnUiThread { status.text = "Status: ready"; speak(reply) }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Status: request failed"; speak(e.message ?: "حدث خطأ في الاتصال.") }
            } finally { conn?.disconnect() }
        }
    }

    private fun handlePhoneCommand(raw: String): Boolean {
        val text = raw.lowercase(Locale.getDefault()).trim()
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        when {
            text.contains("افتح واتساب") || text.contains("open whatsapp") -> return launchApp("com.whatsapp", "WhatsApp")
            text.contains("افتح يوتيوب") || text.contains("open youtube") -> return launchApp("com.google.android.youtube", "YouTube")
            text.contains("افتح الكاميرا") || text.contains("شغل الكاميرا") || text.contains("open camera") -> {
                return try { startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)); speak("Opening camera."); true }
                catch (_: Exception) { speak("تعذر فتح الكاميرا."); true }
            }
            text.contains("ارفع الصوت") || text.contains("زود الصوت") || text.contains("volume up") -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); speak("تم رفع الصوت."); return true
            }
            text.contains("اخفض الصوت") || text.contains("قلل الصوت") || text.contains("volume down") -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); speak("تم خفض الصوت."); return true
            }
            text.contains("اقرأ الاشعارات") || text.contains("اقرأ الإشعارات") || text.contains("read notifications") -> {
                val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty().contains(packageName)
                if (!enabled) { speak("فعّل صلاحية قراءة الإشعارات أولاً."); startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                else speak(JarvisNotificationListener.summary())
                return true
            }
            text.startsWith("اتصل ") || text.startsWith("call ") -> {
                val name = raw.substringAfter(' ').trim()
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 73)
                    speak("اسمح بالوصول إلى جهات الاتصال ثم أعد الأمر.")
                    return true
                }
                val cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"), null)
                val number = cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
                if (number.isNullOrBlank()) speak("لم أجد جهة الاتصال.")
                else { speak("سأفتح شاشة الاتصال. راجع الرقم واضغط اتصال."); startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
                return true
            }
        }
        return false
    }

    private fun launchApp(packageName: String, name: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) speak("$name غير مثبت على الهاتف.")
        else { startActivity(intent); speak("Opening $name.") }
        return true
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
        else Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) { ttsReady = true; tts?.language = Locale.getDefault() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 72 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoiceInput()
    }

    override fun onDestroy() {
        speech?.cancel(); speech?.destroy(); tts?.stop(); tts?.shutdown(); worker.shutdownNow()
        super.onDestroy()
    }
}
