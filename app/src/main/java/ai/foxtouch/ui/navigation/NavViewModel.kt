package ai.foxtouch.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.foxtouch.data.preferences.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NavViewModel @Inject constructor(
    appSettings: AppSettings,
) : ViewModel() {

    val isSetupComplete: StateFlow<Boolean> = appSettings.isSetupComplete
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
}
