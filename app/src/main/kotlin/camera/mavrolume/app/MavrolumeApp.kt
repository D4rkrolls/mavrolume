package camera.mavrolume.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class — the entry point for the whole app.
 *
 * Think of this like your root index.js in a React app.
 * @HiltAndroidApp triggers Hilt's code generation for dependency injection
 * (similar to wrapping your React app in context providers).
 */
@HiltAndroidApp
class MavrolumeApp : Application()
