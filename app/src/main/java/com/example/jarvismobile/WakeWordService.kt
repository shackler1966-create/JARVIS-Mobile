package com.example.jarvismobile

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class WakeWordService : Service(), TextToSpeech.OnInitListener {
    private var porcupine: PorcupineManager? = null
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val worker = Executors.newSingleThreadExecutor()
    private var ttsReady = false
    private val channelId = "jarvis_always_on"

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, notification("Starting wake-word engine"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(7, notification("Starting wake-word engine"))
        }
        tts = TextToSpeech(this, this)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::WakeWord").apply {
            setReferenceCounted(false)
            acquire()
        }
        initializeWakeWord()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun initializeWakeWord() {
        val key = getSharedPreferences("jarvis", MODE_PRIVATE).getString("pv_key", "").orEmpty()
        if (key.isBlank()) {
            updateNotification("Missing Picovoice AccessKey — open JARVIS app")
            stopSelf()
            return
        }
        try {
            porcupine = PorcupineManager.Builder()
                .setAccessKey(key)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setSensitivity(0.65f)
                .build(this) {
                    runOnMain {
                        updateNotification("JARVIS detected — listening for your command")
                        porcupine?.stop()
                        speak("I'm listening.")
                        beginCommandRecognition()
                    }
                }
            porcupine?.start()
            updateNotification("Listening for “Jarvis” — microphone active")
        } catch (e: Exception) {
            Log.e("JARVIS", "Wake engine initialization failed", e)
            updateNotification("Wake engine error: ${e.message}")
            stopSelf()
        }
    }

    private fun beginCommandRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition is not available on this phone.")
            restartWakeWord()
            return
        }
        try {
            speech?.destroy()
            speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    updateNotification("Speak now — JARVIS is listening")
                }
                override fun onResults(results: android.os.Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) {
                        speak("I didn't catch that.")
                        restartWakeWord()
                    } else askAI(text)
                }
                override fun onError(error: Int) {
                    Log.w("JARVIS", "Speech recognition error: $error")
                    speak("I couldn't hear the command. Say Jarvis again.")
                    restartWakeWord()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speech?.startListening(intent)
        } catch (e: Exception) {
            Log.e("JARVIS", "Could not start speech recognition", e)
            restartWakeWord()
        }
    }


    private fun handlePhoneCommand(raw: String): Boolean {
        val text = raw.lowercase(Locale.getDefault()).trim()
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager

        fun launchPackage(packageName: String, spokenName: String): Boolean {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch == null) {
                speak("$spokenName is not installed on this phone.")
                restartWakeWord()
            } else {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
                speak("Opening $spokenName.")
                restartWakeWord()
            }
            return true
        }

        when {
            text.contains("افتح واتساب") || text.contains("open whatsapp") || text.contains("open whats app") ->
                return launchPackage("com.whatsapp", "WhatsApp")
            text.contains("افتح يوتيوب") || text.contains("open youtube") ->
                return launchPackage("com.google.android.youtube", "YouTube")
            text.contains("افتح الكاميرا") || text.contains("شغل الكاميرا") || text.contains("open camera") ->
                return try {
                    startActivity(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    speak("Opening camera.")
                    restartWakeWord()
                    true
                } catch (e: Exception) {
                    speak("I could not open the camera.")
                    restartWakeWord()
                    true
                }
            text.contains("ارفع الصوت") || text.contains("زود الصوت") || text.contains("volume up") ->
                return adjustVolume(audio, AudioManager.ADJUST_RAISE)
            text.contains("اخفض الصوت") || text.contains("قلل الصوت") || text.contains("volume down") ->
                return adjustVolume(audio, AudioManager.ADJUST_LOWER)
            text.contains("اقرأ الاشعارات") || text.contains("اقرأ الإشعارات") || text.contains("read notifications") ->
                return readNotifications()
            text.startsWith("اتصل ") || text.startsWith("call ") ->
                return dialContact(raw.substringAfter(' ').trim())
        }
        return false
    }

    private fun adjustVolume(audio: AudioManager, direction: Int): Boolean {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        speak(if (direction == AudioManager.ADJUST_RAISE) "تم رفع مستوى الصوت." else "تم خفض مستوى الصوت.")
        restartWakeWord()
        return true
    }

    private fun readNotifications(): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ).orEmpty().contains(packageName)
        if (!enabled) {
            speak("فعّل صلاحية قراءة الإشعارات من إعدادات جارفس أولاً.")
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            speak(JarvisNotificationListener.summary())
        }
        restartWakeWord()
        return true
    }

    private fun dialContact(name: String): Boolean {
        if (name.isBlank()) {
            speak("اذكر اسم جهة الاتصال.")
            restartWakeWord()
            return true
        }
        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$name%"),
                null
            )
            val number = cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
            if (number.isNullOrBlank()) {
                speak("لم أجد جهة الاتصال. تأكد من منح صلاحية جهات الاتصال.")
            } else {
                speak("سأفتح شاشة الاتصال. راجع الرقم واضغط اتصال.")
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: SecurityException) {
            speak("اسمح لجارفس بالوصول إلى جهات الاتصال أولاً.")
        } catch (e: Exception) {
            speak("تعذر فتح جهة الاتصال.")
        }
        restartWakeWord()
        return true
    }

    private fun askAI(userText: String) {
        if (handlePhoneCommand(userText)) return
        updateNotification("Processing your request")
        worker.execute {
            try {
                val prefs = getSharedPreferences("jarvis", MODE_PRIVATE)
                val apiKey = prefs.getString("ai_key", "").orEmpty()
                if (apiKey.isBlank()) throw Exception("AI API key is missing. Open JARVIS and save it.")
                val url = URL("https://api.openai.com/v1/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                }
                val payload = JSONObject().apply {
                    put("model", "gpt-4o-mini")
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are JARVIS, a concise and helpful personal voice assistant. Reply naturally and briefly for speech.")
                    }).put(JSONObject().apply {
                        put("role", "user"); put("content", userText)
                    }))
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val response = stream.bufferedReader().use { it.readText() }
                if (conn.responseCode !in 200..299) throw Exception("AI provider error ${conn.responseCode}: $response")
                val reply = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                runOnMain {
                    speak(reply)
                    updateNotification("Ready — say “Jarvis”")
                    restartWakeWord()
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("JARVIS", "AI request failed", e)
                runOnMain {
                    speak(e.message ?: "There was a connection problem.")
                    updateNotification("Ready — say “Jarvis”")
                    restartWakeWord()
                }
            }
        }
    }

    private fun restartWakeWord() {
        try {
            speech?.cancel()
            speech?.destroy()
            speech = null
            porcupine?.start()
            updateNotification("Listening for “Jarvis” — microphone active")
        } catch (e: Exception) {
            Log.e("JARVIS", "Could not restart wake word engine", e)
            stopSelf()
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
        else Log.i("JARVIS", text)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.language = Locale.getDefault()
        }
    }

    private fun runOnMain(block: () -> Unit) = android.os.Handler(mainLooper).post(block)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "JARVIS always-on assistant", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 2, Intent(this, WakeWordService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
                .setContentTitle("JARVIS is active").setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(open).addAction(Notification.Action.Builder(null, "Stop", stop).build())
                .setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("JARVIS is active").setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(open).setOngoing(true).build()
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(7, notification(text))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the explicitly enabled foreground wake-word service running.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try { porcupine?.stop(); porcupine?.delete() } catch (_: Exception) {}
        try { speech?.cancel(); speech?.destroy() } catch (_: Exception) {}
        tts?.stop(); tts?.shutdown()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null
}
