package com.mazin.wasensai.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazin.wasensai.data.datastore.SettingsDataStore
import com.mazin.wasensai.ui.theme.AccentColor
import com.mazin.wasensai.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val detectedWhatsAppPackage by lazy {
        when {
            context.packageManager.safeHasPackage("com.whatsapp") -> "com.whatsapp"
            context.packageManager.safeHasPackage("com.whatsapp.w4b") -> "com.whatsapp.w4b"
            else -> null
        }
    }

    val themeMode: StateFlow<ThemeMode> = settingsDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val accentColor: StateFlow<AccentColor> = settingsDataStore.accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccentColor.YELLOW)

    val isWhatsAppInstalled: Boolean
        get() = detectedWhatsAppPackage != null

    val whatsAppPackage: String
        get() = detectedWhatsAppPackage ?: "com.whatsapp.w4b"

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setAccentColor(accent: AccentColor) {
        viewModelScope.launch { settingsDataStore.setAccentColor(accent) }
    }
}

private fun PackageManager.safeHasPackage(packageName: String): Boolean = try {
    getPackageInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}
