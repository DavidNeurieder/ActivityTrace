# ActivityTrace

## Find something you saw on your phone.

You know you saw it.

A notification. A message. A link. A piece of text. A file.

But now you can't remember where.

**ActivityTrace makes your Android activity searchable.**

Search across your notifications, captured screen content, and indexed files — entirely on your device.

> "tracking number DHL"
>
> "message from Sarah yesterday"
>
> "that link I saw last week"
>
> "error from Signal"

No cloud. No account. No telemetry. **No Internet permission.**

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="60">](https://f-droid.org/en/packages/com.activitytrace/)

---

## Why ActivityTrace?

Android gives you lots of information, but finding something you saw earlier can be surprisingly difficult.

ActivityTrace creates a private, searchable memory of information your phone has already shown you.

### Find what you saw

Everything ActivityTrace captures is combined into one searchable timeline:

- Notifications
- Screen content (via Accessibility)
- Indexed files (documents, PDFs, images)

### Search naturally

Use ordinary language or precise filters to narrow results:

- `yesterday`
- `last week`
- `in:signal`
- `type:notification`
- `tracking number`

Prefix and wildcard matching (`test` matches `testing`, `*error` matches `fatal error`) and result highlighting are built in.

### Private by design

Your ActivityTrace database stays on your device.

- No Internet permission
- No cloud
- No account
- No telemetry, analytics, or crash reporting
- Encrypted at rest with SQLCipher + Android Keystore

**ActivityTrace cannot send your data anywhere.**

### You control retention

Choose how long captured information is kept — from 7 days to 90 days. Block individual apps from being captured entirely.

### Statistics

See when and where things appeared: activity over time, top apps, and content type breakdown.

---

## Privacy isn't a promise. It's an architecture.

ActivityTrace has **no Internet permission**.

That means there is no hidden analytics endpoint, no cloud sync, and no telemetry waiting to be disabled.

Your data stays on your device because the app has no network access to send it.

---

## Screenshots

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="180" alt="Screenshot 1"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="180" alt="Screenshot 2"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="180" alt="Screenshot 3"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="180" alt="Screenshot 4"> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="180" alt="Screenshot 5">

---

## Get ActivityTrace

### F-Droid

[Install from F-Droid](https://f-droid.org/en/packages/com.activitytrace/)

### Source code

[GitHub](https://github.com/DavidNeurieder/ActivityTrace)

---

## Help shape ActivityTrace

ActivityTrace is still early, and the most useful features come from real users.

**What were you trying to find?**

[Open an issue](https://github.com/DavidNeurieder/ActivityTrace/issues) and tell us what you were looking for, what happened, and what would have made ActivityTrace useful.

---

## Open source

ActivityTrace is free and open source software under the [GPL-3.0-only](LICENSE) license.

- No Google Play Services
- No proprietary dependencies
- No AI or ML components
- No cloud features

Contributions are welcome.

---

## Technical details

- Android 8.0+ (API 26)
- Jetpack Compose + Material 3
- Room + SQLCipher (AES-256-CBC) + Android Keystore
- WorkManager for retention cleanup and file indexing
- NotificationListener + AccessibilityService capture
- Storage Access Framework file indexing

### Build

```bash
./gradlew assembleDebug     # debug build (unminified)
./gradlew assembleRelease   # release build (minified, ProGuard)
./gradlew test              # unit tests
./gradlew lint              # static analysis
```

Prerequisites: JDK 17, Android SDK 36. See [`AGENTS.md`](AGENTS.md) for architecture details.
