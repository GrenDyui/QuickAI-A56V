# Quick AI A56

Android app optimized for Samsung Galaxy A56 / One UI: enter questions directly from the notification shade using Android Direct Reply, send them to Gemini API, and receive a compact answer back in the same notification.

## Requirements
- Android 7.0+ (minSdk 24)
- Samsung Galaxy A56 is supported.
- Android notification permission must be granted on Android 13+.
- Gemini API key.

## Build without Android Studio
This repository is designed for GitHub Actions. Push the project to GitHub, then open **Actions → Build Android APK**. The workflow installs JDK 17, Android SDK 35, Gradle 8.9, builds `assembleDebug`, and uploads the APK as an artifact.

## App flow
1. Open Quick AI.
2. Enter Gemini API key and model (default `gemini-3.8-flash`).
3. Press **LƯU & BẬT TRỢ LÝ**.
4. Pull down the notification shade.
5. Tap **Nhập câu hỏi**.
6. Paste the question.
7. Send. WorkManager calls Gemini in the background and updates the same notification with the answer.

## Security
The API key is encrypted locally using Android Keystore. Do not commit API keys into GitHub.

## Notes
The notification uses Android's Direct Reply / RemoteInput. This is the supported Android mechanism for direct text input from a notification; Samsung's One UI decides the exact visual layout.
