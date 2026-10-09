# Build verification

Run `./gradlew assembleFoldDebug assemblePhoneDebug testFoldDebugUnitTest testPhoneDebugUnitTest lintFoldDebug lintPhoneDebug` with JDK 21 and Android SDK/Build Tools 35.0.0. Run instrumented tests on a connected compatible device with `connectedFoldDebugAndroidTest` or `connectedPhoneDebugAndroidTest`.

The source archive excludes build outputs, local SDK paths, signing keys and APKs. The debug APKs are provided separately for testing. Physical camera behavior and 50 MP availability depend on the device.

Verified on 4 October 2026: `clean`, `assembleDebug`, `testDebugUnitTest` and `lintDebug` passed with JDK 21/API 35. The Android test APK was compiled for version 1.1.0 but not run on a device. The 1.1.1 debug APK has a verified v2 signature. Hardware checks on a physical Fold 8 remain pending.

Version 1.2.0 builds two variants: Fold (API 35+) and Phone (API 29+, OpenGL ES 3.1). Both variants compiled, passed unit tests and lint on 9 October 2026. Neither was run on a Huawei P60 or other non-Fold physical device here. Their APK hashes are listed in the release notes.
