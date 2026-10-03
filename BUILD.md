# Build verification

Run `./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest` with JDK 21 and Android SDK/Build Tools 35.0.0. The instrumented test APK can be compiled on a host; execute it with `./gradlew connectedDebugAndroidTest` on a compatible Android device.

The source archive excludes build outputs, local SDK paths, signing keys and the debug APK. The debug APK is provided separately for testing. Physical foldable camera behavior and 50 MP availability remain unverified until tested on hardware.

Verified on 4 October 2026: `clean`, `assembleDebug`, `testDebugUnitTest` and `lintDebug` passed with JDK 21/API 35. The Android test APK was compiled for version 1.1.0 but not run on a device. The 1.1.1 debug APK has a verified v2 signature. Hardware checks on a physical Fold 8 remain pending.

Debug APK SHA-256: `10103bb924acab307a969b0c96c1acf8c7ffffcac1a81eeb4477af3ec4dc1534`.
