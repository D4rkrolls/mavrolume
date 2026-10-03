package camera.mavrolume.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object FilmSimModule {
    // LutLoader and ImageProcessor use @Inject constructor + @Singleton,
    // so Hilt discovers them automatically. This module is a placeholder
    // for any manual @Provides bindings needed in the future.
}
