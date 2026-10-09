# Contributing to Mavrolume

Issues, reproducible device reports and pull requests are welcome. Include the device model, Android/One UI version, selected lens and steps to reproduce a camera issue. For image-quality reports, include the chosen recipe and settings; avoid sharing private photos unless you intend to publish them.

Before submitting code, run `./gradlew assembleFoldDebug assemblePhoneDebug testFoldDebugUnitTest testPhoneDebugUnitTest lintFoldDebug lintPhoneDebug` with JDK 21 and Android SDK 35. Camera and folding behavior also needs a physical-device check where possible; describe what you tested and what remains untested. Use [DEVICE-TESTING.md](DEVICE-TESTING.md) as a guide.

Contributions are accepted under this repository's MIT license. By submitting a contribution, you represent that you have the right to provide it under those terms. Keep existing copyright and license notices. For new code, images, fonts, sounds, LUTs or other outside assets, identify the source and compatible license in the pull request. Do not submit proprietary camera-app presets, extracted LUTs, logos or material you cannot license for redistribution.

The project is independently maintained by d4rkrolls. A contribution does not make its author a representative of FilmFrame or any device or camera brand.
