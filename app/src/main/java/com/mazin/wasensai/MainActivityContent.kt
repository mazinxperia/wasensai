package com.mazin.wasensai

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.mazin.wasensai.ui.navigation.WaSensaiNavGraph
import com.mazin.wasensai.ui.theme.LocalAppPalette
import com.mazin.wasensai.ui.theme.ThemeMode
import com.mazin.wasensai.ui.theme.WASensaiTheme
import com.mazin.wasensai.viewmodel.HomeViewModel

@Composable
fun MainActivityContent() {
    val viewModel: HomeViewModel = hiltViewModel()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    WASensaiTheme(themeMode = themeMode, accentColor = accentColor) {
        val palette = LocalAppPalette.current
        val bgBrush = remember(palette, isDark) {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(palette.darkBgTop, palette.darkBgBot)
                } else {
                    listOf(palette.lightBgTop, palette.lightBgBot)
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize().background(bgBrush)) {
            WaSensaiNavGraph(navController = navController)
        }
    }
}
