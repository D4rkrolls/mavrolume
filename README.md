# Mavrolume

<p align="center">
  <img src="docs/screenshots/cover.jpg" alt="Mavrolume cover-screen camera" width="260">
  <img src="docs/screenshots/inner.jpg" alt="Mavrolume unfolded pro controls" width="580">
</p>

**A film-minded Android camera built around a foldable screen.** Mavrolume keeps the cover-screen viewfinder simple and uses the unfolded display for hands-on control. It is a native Kotlin, Jetpack Compose, CameraX and OpenGL ES project requiring Android 15 (API 35) or newer.

> Leaving the iOS ecosystem, I wanted a camera that felt at home on my Fold 8: quick to use on the cover screen, but with real creative control when I opened it. [FilmFrame](https://github.com/ryuheiyokokawa/FilmFrame), created by **Ryuhei Yokokawa**, was the open-source starting point I admired. I built on its camera foundation, redesigned the experience for a foldable, and gave the film processing and interface my own spin.
>
> — **d4rkrolls**, project creator

Mavrolume is an independent project. It is not made, sponsored or endorsed by FilmFrame's author, Samsung, Fujifilm, Leica, Mood.camera or any other camera or film brand. Its recipes are independent interpretations, not copies of another app's LUTs or a promise to match a physical film stock.

## Features

- **Cover-screen camera:** viewfinder, film selection, lens and zoom choices, EV compensation, flash, tap-to-focus, aspect ratio and shutter access.
- **Unfolded workspace:** Film, WB, Exposure, Focus, Frame and More pages beside the viewfinder. The inner-screen command dial switches between film look, EV, white balance and manual focus.
- **Live film processing:** 34 adjustable color and monochrome recipes. Controls include strength, saturation, warmth, tint, grain amount and size, bloom, halation, brightness, contrast, dynamic range, midtones, fade and mute. [Browse the recipes](PRESETS.md).
- **Photo output:** processed JPEG, optional original JPEG, and RAW/DNG when the selected camera configuration exposes it.
- **Framing:** 4:3, 3:2, 16:9, 1:1 and 65:24/XPan crop options.
- **Camera control:** camera switching, flash, EV compensation, and supported manual ISO, shutter and focus controls.

The app requests camera access and saves photos under `Pictures/Mavrolume`. It has no network permission and does not ask for broad storage access.

## Install on your phone

1. Download `mavrolume-v1.1.1-debug.apk` from this project's GitHub release, if available, or build the APK from source below. Download APKs only from sources you trust.
2. Open the APK on your phone. If Android blocks installation, allow **Install unknown apps** for the browser or file manager you used, then retry. The wording and location vary by Android version.
3. Open **Mavrolume** and allow camera access.
4. If an older debug build cannot be updated because it used a different development signing key, back up anything important and uninstall that build before installing this one.

This is a **debug build for testing**, not a production-signed Play Store release. The app has not yet been verified on physical Fold 8 hardware. See [device testing](DEVICE-TESTING.md) before relying on it for important photos.

## Use the camera

### Cover screen

Point the camera and tap the subject to focus. Choose a film look along the bottom, use the zoom choices to frame, adjust EV in automatic exposure, and tap the shutter button. The top row gives access to flash, resolution where supported, and the editor. Swipe up on the viewfinder or tap **Edit film** for deeper processing controls. **Library** opens saved processed photos.

### Inner screen

Unfold the phone for a larger viewfinder and pro workspace. Choose one page at a time:

| Page | Controls |
| --- | --- |
| **Film** | Choose an original recipe; adjust strength, saturation, grain, bloom and halation. |
| **WB** | Choose a camera white-balance preset, then fine-tune warmth and green–magenta tint in the film rendering. |
| **Exposure** | Use EV in auto mode. If supported, switch to manual ISO and shutter; adjust brightness, contrast and tone separately. |
| **Focus** | Select a camera, zoom, switch to manual focus if supported and enable focus peaking. |
| **Frame** | Set crop, request 12 or 50 MP when available, and choose original JPEG or DNG saving. |
| **More** | Adjust fade, mute, softness and color fringing, or reset the film look. |

The **DIAL** above the pages controls film, EV, WB or manual focus. It runs on the inner screen. The physical cover display is **not** used as a second control surface while unfolded; that depends on Samsung exposing it concurrently to third-party apps, which has not been verified here.

### Saving and camera limits

The chosen recipe and adjustments affect the live preview and processed JPEG. Turn on **Save original JPEG** for an unprocessed companion image. RAW/DNG, flash, manual settings, lens choices and 50 MP depend on what the device and current camera session expose. A 50 MP sensor does not guarantee a 50 MP third-party capture. Cropping can reduce the final pixel count; the app reports capture resolution before crop.

## Build from source

Install **JDK 21** and **Android SDK Platform and Build Tools 35.0.0**, set `JAVA_HOME` and `ANDROID_HOME`, then run:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

The APK will be `app/build/outputs/apk/debug/app-debug.apk`. With Android Debug Bridge and a connected phone:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Android test APK is compiled by that command; running instrumented tests requires a connected device. See [build verification](BUILD.md) and the [physical-device checklist](DEVICE-TESTING.md). No production signing key is included.

## Device scope

The interface targets a Galaxy Fold-class device and keeps a conventional-phone layout in smaller windows. The pro workspace appears at a window size of at least 650 dp wide and 600 dp high. CameraX and the device determine exposed cameras, resolution modes, RAW streams and manual controls. Rotation, fold continuity, lens routing, preview performance and output quality still need physical Fold 8 testing. This project is not certified or optimized by Samsung.

## Origins, credit and contributions

Mavrolume is **derived from FilmFrame by Ryuhei Yokokawa**, under the [MIT license](LICENSE). FilmFrame supplied the original CameraX/OpenGL camera foundation and portions of the gallery, crop and EXIF infrastructure. d4rkrolls directed this project and its foldable interface, film recipes, icon and reworked processing and capture flow. The provenance breakdown is in [NOTICE.md](NOTICE.md). The exact upstream notice is also bundled in the APK at `app/src/main/assets/LICENSE-FilmFrame.txt`.

Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md). Keep upstream attribution, document the source and license of new assets or code, and do not submit proprietary camera-app LUTs or branding. The project is MIT licensed. No public software release can be guaranteed free of every legal or compatibility risk.
