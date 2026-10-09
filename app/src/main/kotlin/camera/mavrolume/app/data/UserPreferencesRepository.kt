package camera.mavrolume.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserPreferences(
    val filmSettings: String? = null,
    val saveOriginal: Boolean = true,
    val saveDng: Boolean = false,
    val lastFilmSim: String = "DAYBREAK",
    val lastGrid: String = "NONE",
    val peakingEnabled: Boolean = false,
    val peakingColor: String = "RED",
    val histogramEnabled: Boolean = false,
    val lastGrainLevel: String = "OFF",
    val lastAspectRatio: String = "FULL",
    val lastWhiteBalance: String = "AUTO",
    val lastMeteringMode: String = "MULTI",
    val lastLens: String = "WIDE",
    val levelEnabled: Boolean = false,
    val lastFilmCategory: String = "RECIPES",
    val lastPickerMode: String = "FILM",
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val FILM_SETTINGS = stringPreferencesKey("film_settings")
        val SAVE_ORIGINAL = booleanPreferencesKey("save_original")
        val SAVE_DNG = booleanPreferencesKey("save_dng")
        val LAST_FILM_SIM = stringPreferencesKey("last_film_sim")
        val LAST_GRID = stringPreferencesKey("last_grid")
        val PEAKING_ENABLED = booleanPreferencesKey("peaking_enabled")
        val PEAKING_COLOR = stringPreferencesKey("peaking_color")
        val HISTOGRAM_ENABLED = booleanPreferencesKey("histogram_enabled")
        val LAST_GRAIN_LEVEL = stringPreferencesKey("last_grain_level")
        val LAST_ASPECT_RATIO = stringPreferencesKey("last_aspect_ratio")
        val LAST_WHITE_BALANCE = stringPreferencesKey("last_white_balance")
        val LAST_METERING_MODE = stringPreferencesKey("last_metering_mode")
        val LAST_LENS = stringPreferencesKey("last_lens")
        val LEVEL_ENABLED = booleanPreferencesKey("level_enabled")
        val LAST_FILM_CATEGORY = stringPreferencesKey("last_film_category")
        val LAST_PICKER_MODE = stringPreferencesKey("last_picker_mode")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            filmSettings = prefs[Keys.FILM_SETTINGS],
            saveOriginal = prefs[Keys.SAVE_ORIGINAL] ?: true,
            saveDng = prefs[Keys.SAVE_DNG] ?: false,
            lastFilmSim = prefs[Keys.LAST_FILM_SIM] ?: "DAYBREAK",
            lastGrid = prefs[Keys.LAST_GRID] ?: "NONE",
            peakingEnabled = prefs[Keys.PEAKING_ENABLED] ?: false,
            peakingColor = prefs[Keys.PEAKING_COLOR] ?: "RED",
            histogramEnabled = prefs[Keys.HISTOGRAM_ENABLED] ?: false,
            lastGrainLevel = prefs[Keys.LAST_GRAIN_LEVEL] ?: "OFF",
            lastAspectRatio = prefs[Keys.LAST_ASPECT_RATIO] ?: "FULL",
            lastWhiteBalance = prefs[Keys.LAST_WHITE_BALANCE] ?: "AUTO",
            lastMeteringMode = prefs[Keys.LAST_METERING_MODE] ?: "MULTI",
            lastLens = prefs[Keys.LAST_LENS] ?: "WIDE",
            levelEnabled = prefs[Keys.LEVEL_ENABLED] ?: false,
            lastFilmCategory = prefs[Keys.LAST_FILM_CATEGORY] ?: "RECIPES",
            lastPickerMode = prefs[Keys.LAST_PICKER_MODE] ?: "FILM",
        )
    }

    suspend fun setFilmSettings(value: String) { context.dataStore.edit { it[Keys.FILM_SETTINGS] = value } }

    suspend fun setSaveOriginal(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SAVE_ORIGINAL] = enabled }
    }

    suspend fun setSaveDng(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SAVE_DNG] = enabled }
    }

    suspend fun setLastFilmSim(simName: String) {
        context.dataStore.edit { it[Keys.LAST_FILM_SIM] = simName }
    }

    suspend fun setLastGrid(gridName: String) {
        context.dataStore.edit { it[Keys.LAST_GRID] = gridName }
    }

    suspend fun setPeaking(enabled: Boolean, color: String) {
        context.dataStore.edit {
            it[Keys.PEAKING_ENABLED] = enabled
            it[Keys.PEAKING_COLOR] = color
        }
    }

    suspend fun setHistogramEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HISTOGRAM_ENABLED] = enabled }
    }

    suspend fun setLastGrainLevel(name: String) {
        context.dataStore.edit { it[Keys.LAST_GRAIN_LEVEL] = name }
    }

    suspend fun setLastAspectRatio(name: String) {
        context.dataStore.edit { it[Keys.LAST_ASPECT_RATIO] = name }
    }

    suspend fun setLastWhiteBalance(name: String) {
        context.dataStore.edit { it[Keys.LAST_WHITE_BALANCE] = name }
    }

    suspend fun setLastMeteringMode(name: String) {
        context.dataStore.edit { it[Keys.LAST_METERING_MODE] = name }
    }

    suspend fun setLastLens(name: String) {
        context.dataStore.edit { it[Keys.LAST_LENS] = name }
    }

    suspend fun setLevelEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LEVEL_ENABLED] = enabled }
    }

    suspend fun setLastFilmCategory(name: String) {
        context.dataStore.edit { it[Keys.LAST_FILM_CATEGORY] = name }
    }

    suspend fun setLastPickerMode(name: String) {
        context.dataStore.edit { it[Keys.LAST_PICKER_MODE] = name }
    }
}
