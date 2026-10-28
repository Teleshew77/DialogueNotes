# Nigga Tekst — Android test build

This project includes a GitHub Actions workflow that builds the debug APK and then launches the actual app in an Android emulator using an instrumentation smoke test. The APK artifact is uploaded only if the launch test passes.

## GitHub
Upload the contents of this project to the root of your repository, preserving `.github/workflows` and `app/src/androidTest`. Open Actions and inspect the latest run.

## Important
A successful emulator launch test confirms that the main screen starts in the CI emulator; it does not prove compatibility with every Xiaomi/MIUI device or resolve Google Play Protect classification. Keep Play Protect enabled.
