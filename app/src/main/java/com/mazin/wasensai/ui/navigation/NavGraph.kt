package com.mazin.wasensai.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.mazin.wasensai.ui.screens.about.AboutScreen
import com.mazin.wasensai.ui.screens.extract.*
import com.mazin.wasensai.ui.screens.home.HomeScreen
import com.mazin.wasensai.ui.screens.settings.AccentColorPickerScreen
import com.mazin.wasensai.ui.screens.settings.SettingsScreen
import com.mazin.wasensai.ui.screens.viewer.*
import com.mazin.wasensai.ui.theme.ThemeMode
import com.mazin.wasensai.viewmodel.HomeViewModel
import com.mazin.wasensai.viewmodel.ViewerViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazin.wasensai.ui.theme.ViewerTheme

sealed class Screen(val route: String) {
    object Home            : Screen("home")
    object Settings        : Screen("settings")
    object About           : Screen("about")
    object Extract         : Screen("extract_flow")
    object Viewer          : Screen("viewer")
    object ChatScreen      : Screen("chat_screen/{chatId}") {
        fun createRoute(chatId: Long) = "chat_screen/$chatId"
    }
    object MediaGallery    : Screen("media_gallery/{chatId}") {
        fun createRoute(chatId: Long) = "media_gallery/$chatId"
    }
    object FullScreenMedia : Screen("full_screen_media/{chatId}/{mediaIndex}") {
        fun createRoute(chatId: Long, mediaIndex: Int) = "full_screen_media/$chatId/$mediaIndex"
    }
    object AccentPicker    : Screen("accent_picker")
    object ContactInfo     : Screen("contact_info/{chatId}") {
        fun createRoute(chatId: Long) = "contact_info/$chatId"
    }
}

private val SlideEasing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
private const val ANIM_MS = 350

@Composable
fun WaSensaiNavGraph(navController: NavHostController) {
    NavHost(
        navController      = navController,
        startDestination   = Screen.Home.route,
        enterTransition    = { slideInHorizontally(initialOffsetX = { it },  animationSpec = tween(ANIM_MS, easing = SlideEasing)) },
        exitTransition     = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(ANIM_MS, easing = SlideEasing)) },
        popEnterTransition  = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(ANIM_MS, easing = SlideEasing)) },
        popExitTransition   = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(ANIM_MS, easing = SlideEasing)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onExtractClick  = { navController.navigate(Screen.Extract.route) },
                onViewClick     = { navController.navigate(Screen.Viewer.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick        = { navController.popBackStack() },
                onAboutClick       = { navController.navigate(Screen.About.route) },
                onAccentColorClick = { navController.navigate(Screen.AccentPicker.route) }
            )
        }
        composable(Screen.AccentPicker.route) {
            AccentColorPickerScreen(onBackClick = { navController.popBackStack() })
        }
        composable(Screen.About.route) {
            AboutScreen(onBackClick = { navController.popBackStack() })
        }
        composable(Screen.Extract.route) {
            ExtractFlowScreen(
                onBackToHome = { navController.popBackStack() },
                onOpenViewer = { navController.navigate(Screen.Viewer.route) { popUpTo(Screen.Home.route) } }
            )
        }

        // Viewer root — owns the shared ViewerViewModel instance
        composable(Screen.Viewer.route) { viewerEntry ->
            val viewerViewModel: ViewerViewModel = hiltViewModel(viewerEntry)
            val homeViewModel: HomeViewModel = hiltViewModel()
            val themeMode by homeViewModel.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            ViewerTheme(darkTheme = isDark) {
                ViewerScreen(
                    onBackClick = { navController.popBackStack() },
                    viewModel   = viewerViewModel
                )
            }
        }
    }
}
