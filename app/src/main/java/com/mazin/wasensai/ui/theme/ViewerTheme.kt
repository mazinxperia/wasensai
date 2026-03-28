package com.mazin.wasensai.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ViewerDarkBackground = Color(0xFF0A1016)
private val ViewerLightBackground = Color(0xFFFDFEFE)
private val ViewerLightHeader = Color(0xFFFFFFFF)
private val ViewerLightFooter = Color(0xFFFFFFFF)
private val ViewerLightSurface = Color(0xFFF6F8FA)
private val ViewerLightSurfaceVariant = Color(0xFFEFF3F6)
private val ViewerLightDivider = Color(0xFFDCE3E8)
private val ViewerLightTextPrimary = Color(0xFF111B21)
private val ViewerLightTextSecondary = Color(0xFF667781)
private val ViewerLightBubblePrimary = Color(0xFFFFFFFF)
private val ViewerLightBubbleSecondary = Color(0xFFF7F9FA)
private val ViewerLightChatBackground = Color(0xFFF5F1EB)
private val ViewerLightChatDoodle = Color(0xFFF5F1EB)
private val ViewerLightChatPattern = Color(0xFFF5F1EB)
private val ViewerLightInput = Color(0xFFF0F2F5)
private val ViewerLightPlaceholder = Color(0xFFC0C8D0)
private val ViewerLightSelectedPill = Color(0xFFE2E8EA)
private val ViewerLightNavBar = Color(0xFFF6F8FA)

private val ViewerDarkPrimaryText = Color(0xFFF3F5F8)
private val ViewerDarkSecondaryText = Color(0xFF9AA4AE)
private val ViewerDarkSurface = Color(0xFF0A1016)
private val ViewerDarkSurfaceVariant = Color(0xFF1A2229)
private val ViewerDarkDivider = Color(0xFF1F2A33)
private val ViewerDarkBubblePrimary = Color(0xFF1F272B)
private val ViewerDarkBubbleSecondary = Color(0xFF1E2428)
private val ViewerDarkChatBackground = Color(0xFF1F272B)
private val ViewerDarkChatDoodle = Color(0xFF1F272B)
private val ViewerDarkChatPattern = Color(0xFF1F272B)
private val ViewerDarkInput = Color(0xFF1F272B)
private val ViewerDarkPlaceholder = Color(0xFF34414A)
private val ViewerDarkSelectedPill = Color(0xFF1B252C)
private val ViewerDarkNavBar = Color(0xFF000000)

private val ViewerAction = Color(0xFF008069)
private val ViewerHighlight = Color(0xFFFFEB3B)
private val ViewerSuccess = Color(0xFF81C784)
private val ViewerWarning = Color(0xFFFF7043)
private val ViewerError = Color(0xFFFF6B6B)
private val ViewerInfo = Color(0xFF64B5F6)
private val ViewerVerified = Color(0xFF1DA1F2)
private val ViewerSeen = Color(0xFF53BDEB)
private val ViewerFullScreenBackground = Color(0xFF000000)
private val ViewerFullScreenOverlay = Color(0xCC000000)
private val ViewerFullScreenOnColor = Color(0xFFF3F5F8)

@Immutable
data class ViewerColors(
    val background: Color,
    val header: Color,
    val footer: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val icon: Color,
    val chevron: Color,
    val placeholder: Color,
    val selectedPill: Color,
    val chatBackground: Color,
    val chatDoodleBackground: Color,
    val chatDoodlePattern: Color,
    val bubblePrimary: Color,
    val bubbleSecondary: Color,
    val inputBackground: Color,
    val action: Color,
    val highlight: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val verified: Color,
    val seen: Color,
    val navBar: Color,
    val fullScreenBackground: Color,
    val fullScreenOverlay: Color,
    val fullScreenOnColor: Color,
    val avatarFallbackColors: List<Color>,
)

val ViewerLightColors = ViewerColors(
    background = ViewerLightBackground,
    header = ViewerLightHeader,
    footer = ViewerLightFooter,
    surface = ViewerLightSurface,
    surfaceVariant = ViewerLightSurfaceVariant,
    divider = ViewerLightDivider,
    textPrimary = ViewerLightTextPrimary,
    textSecondary = ViewerLightTextSecondary,
    icon = Color(0xFF54656F),
    chevron = Color(0xFFC4C4C4),
    placeholder = ViewerLightPlaceholder,
    selectedPill = ViewerLightSelectedPill,
    chatBackground = ViewerLightChatBackground,
    chatDoodleBackground = ViewerLightChatDoodle,
    chatDoodlePattern = ViewerLightChatPattern,
    bubblePrimary = ViewerLightBubblePrimary,
    bubbleSecondary = ViewerLightBubbleSecondary,
    inputBackground = ViewerLightInput,
    action = ViewerAction,
    highlight = ViewerHighlight,
    success = ViewerSuccess,
    warning = ViewerWarning,
    error = ViewerError,
    info = ViewerInfo,
    verified = ViewerVerified,
    seen = ViewerSeen,
    navBar = ViewerLightNavBar,
    fullScreenBackground = ViewerFullScreenBackground,
    fullScreenOverlay = ViewerFullScreenOverlay,
    fullScreenOnColor = ViewerFullScreenOnColor,
    avatarFallbackColors = listOf(
        Color(0xFF00BCD4), Color(0xFFE91E8C), Color(0xFF9C27B0),
        Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF2196F3)
    ),
)

val ViewerDarkColors = ViewerColors(
    background = ViewerDarkBackground,
    header = ViewerDarkBackground,
    footer = ViewerDarkBackground,
    surface = ViewerDarkSurface,
    surfaceVariant = ViewerDarkSurfaceVariant,
    divider = ViewerDarkDivider,
    textPrimary = ViewerDarkPrimaryText,
    textSecondary = ViewerDarkSecondaryText,
    icon = ViewerDarkSecondaryText,
    chevron = Color(0xFF5A6771),
    placeholder = ViewerDarkPlaceholder,
    selectedPill = ViewerDarkSelectedPill,
    chatBackground = ViewerDarkChatBackground,
    chatDoodleBackground = ViewerDarkChatDoodle,
    chatDoodlePattern = ViewerDarkChatPattern,
    bubblePrimary = ViewerDarkBubblePrimary,
    bubbleSecondary = ViewerDarkBubbleSecondary,
    inputBackground = ViewerDarkInput,
    action = ViewerAction,
    highlight = ViewerHighlight,
    success = ViewerSuccess,
    warning = ViewerWarning,
    error = ViewerError,
    info = ViewerInfo,
    verified = ViewerVerified,
    seen = ViewerSeen,
    navBar = ViewerDarkNavBar,
    fullScreenBackground = ViewerFullScreenBackground,
    fullScreenOverlay = ViewerFullScreenOverlay,
    fullScreenOnColor = ViewerFullScreenOnColor,
    avatarFallbackColors = listOf(
        Color(0xFF32515B), Color(0xFF5A3851), Color(0xFF514366),
        Color(0xFF355243), Color(0xFF6B4C2F), Color(0xFF35506A)
    ),
)

val LocalViewerColors = compositionLocalOf { ViewerLightColors }

val MaterialTheme.viewerColors: ViewerColors
    @Composable
    @ReadOnlyComposable
    get() = LocalViewerColors.current

private fun viewerColorScheme(colors: ViewerColors, darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        darkColorScheme(
            primary = colors.action,
            onPrimary = colors.textPrimary,
            secondary = colors.surfaceVariant,
            onSecondary = colors.textPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.divider,
            outlineVariant = colors.divider.copy(alpha = 0.6f),
            error = colors.error,
            onError = colors.textPrimary,
            scrim = colors.fullScreenOverlay,
        )
    } else {
        lightColorScheme(
            primary = colors.action,
            onPrimary = Color.White,
            secondary = colors.surfaceVariant,
            onSecondary = colors.textPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.divider,
            outlineVariant = colors.divider.copy(alpha = 0.7f),
            error = colors.error,
            onError = Color.White,
            scrim = colors.fullScreenOverlay,
        )
    }

@Composable
fun ViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val targetColors = if (darkTheme) ViewerDarkColors else ViewerLightColors
    val view = LocalView.current
    val colors = targetColors.copy(
        background = animateColorAsState(targetColors.background, label = "viewer_background").value,
        surface = animateColorAsState(targetColors.surface, label = "viewer_surface").value,
        textPrimary = animateColorAsState(targetColors.textPrimary, label = "viewer_text_primary").value,
        header = targetColors.header,
        footer = targetColors.footer,
        surfaceVariant = targetColors.surfaceVariant,
        divider = targetColors.divider,
        textSecondary = targetColors.textSecondary,
        icon = targetColors.icon,
        chevron = targetColors.chevron,
        placeholder = targetColors.placeholder,
        selectedPill = targetColors.selectedPill,
        chatBackground = targetColors.chatBackground,
        chatDoodleBackground = targetColors.chatDoodleBackground,
        chatDoodlePattern = targetColors.chatDoodlePattern,
        bubblePrimary = targetColors.bubblePrimary,
        bubbleSecondary = targetColors.bubbleSecondary,
        inputBackground = targetColors.inputBackground,
        action = targetColors.action,
        highlight = targetColors.highlight,
        success = targetColors.success,
        warning = targetColors.warning,
        error = targetColors.error,
        info = targetColors.info,
        verified = targetColors.verified,
        seen = targetColors.seen,
        navBar = targetColors.navBar,
        fullScreenBackground = targetColors.fullScreenBackground,
        fullScreenOverlay = targetColors.fullScreenOverlay,
        fullScreenOnColor = targetColors.fullScreenOnColor,
    )
    val colorScheme = viewerColorScheme(colors, darkTheme)

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalViewerColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}
