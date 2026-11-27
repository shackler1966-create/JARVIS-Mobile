# JARVIS Native Android — source project

This is a native Android Studio project scaffold for an always-on wake-word assistant.
It uses Picovoice Porcupine's built-in **JARVIS** keyword and an Android microphone
foreground service. On detection it opens Android speech recognition, sends the recognized
command to the OpenAI Chat Completions API, and reads the answer aloud using Android TTS.

## Before building
1. Create a Picovoice account and obtain an AccessKey from the Picovoice Console.
2. Keep that key private. Enter it only inside the app on your own phone.
3. Add your AI provider API key in the app settings. The app stores both keys in private
   app preferences on the device. Do not share screenshots or backups containing secrets.
4. Open this folder in Android Studio, allow Gradle sync, then Build > Build APK(s).
5. Install the generated APK on your Android phone.

## Start it
- Open JARVIS Mobile.
- Enter Picovoice AccessKey and AI API key, then tap Save.
- Grant microphone and notification permissions.
- Tap START ALWAYS-ON JARVIS while the app is visible.
- A persistent notification indicates the microphone foreground service is active.
- Lock the screen and say “Jarvis”. After the listening prompt, speak a command.
- Stop using the app button or the notification's Stop action.

## Important Android limitations
- Android 14+ requires a microphone foreground service to be started while the app is visible
  and microphone permission is granted. This project starts it from the visible activity.
- The service uses a persistent notification and a partial wake lock. Battery use will be higher.
- Some phone manufacturers aggressively stop background apps. You may need to allow
  unrestricted battery use for JARVIS in Android Settings.
- Force-stop, reboot, revoked microphone permission, battery restrictions, or OS policy can
  stop the service. This is not a replacement for Google Assistant and cannot bypass Android
  privacy controls.
- Porcupine's built-in keyword is **“JARVIS”**, not the two-word phrase “Hey Jarvis”.
  For the exact phrase “Hey Jarvis”, create a custom wake-word model in Picovoice Console
  and use its `.ppn` file in the project.
- Speech recognition after wake relies on the Android device's recognition provider and may
  require internet. The wake-word detector itself runs on-device.
- The provided MP3 sample has not been embedded into this native APK scaffold yet; the
  app's spoken replies use Android's installed TTS voice, not a clone of the recording.
- The AI API key is stored locally in app-private preferences but a production app should
  use a secured backend proxy rather than embedding a provider key in a client app.

## Dependencies
- Android Gradle Plugin 8.7.3
- Kotlin Android plugin 2.0.21
- Picovoice Porcupine Android 4.0.2
- minSdk 26 / targetSdk 35


## Phone actions added in this source update
- "افتح واتساب" / "Open WhatsApp"
- "افتح يوتيوب" / "Open YouTube"
- "افتح الكاميرا" / "Open camera"
- "ارفع الصوت" / "Volume up"
- "اخفض الصوت" / "Volume down"
- "اقرأ الإشعارات" / "Read notifications" (after explicitly enabling JARVIS notification access)
- "اتصل [اسم جهة الاتصال]" / "Call [contact]" opens the dialer with the matching number; it does not place a call automatically.

The app launcher uses installed Android app package names. Some OEM or region variants may use a different package.
Notification access is sensitive: the user must explicitly enable it in Android Settings. JARVIS reads notification text locally to speak it.
