# Vitr Android

Vitr Android packages the live Vitr player in a small native Android WebView shell.

- App ID: `app.vitr.android`
- Version name: **0.1.0**
- Version code: **1**
- Minimum Android API: **26**
- Target/compile API: **35**
- Player URL: `https://vitr.nont.me/music`
- APK type: debug-signed, installable APK for direct testing

## Build

With JDK 17, Android SDK 35 and Gradle 8.11.1 installed:

```sh
gradle -p mobile/android :app:assembleDebug
```

The APK is generated at:

```
mobile/android/app/build/outputs/apk/debug/app-debug.apk
```
