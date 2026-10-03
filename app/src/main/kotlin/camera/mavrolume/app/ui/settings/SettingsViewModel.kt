package camera.mavrolume.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import camera.mavrolume.app.data.UserPreferences
import camera.mavrolume.app.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    fun setSaveOriginal(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setSaveOriginal(enabled) }
    }

    fun setSaveDng(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setSaveDng(enabled) }
    }
}
