# Nigga Tekst — Android project

Luxury Dark interface (onyx/graphite/gold), Russian speech recognition, draft summaries, transcript sharing.

## Privacy and safety baseline
- The app requests microphone permission only.
- It does not contain app-authored network calls, analytics, ads, trackers, or dynamic code loading.
- It stops recognition when the app goes to the background; it does not secretly record in the background.
- Speech recognition is delegated to the Android speech-recognition service. That service may process audio according to the provider's own policy and may require internet access.
- The summary is rule-based, not an AI-generated verified transcript. Review important details.

## Build test APK
GitHub Actions → Build Android APK → Run workflow. Download artifact `Nigga-Tekst-Android-test-APK`; it contains `app-debug.apk`.

## Important release note
This workflow creates a **debug/test APK**, not a Play Store release. Do not publish a debug APK. A production release should be built as a signed release and distributed through Google Play internal testing / Play App Signing. Never commit a signing keystore or passwords to the repository. A Play Protect warning must be investigated using the exact warning and APK; changing the app name or signing it does not guarantee the warning disappears.
