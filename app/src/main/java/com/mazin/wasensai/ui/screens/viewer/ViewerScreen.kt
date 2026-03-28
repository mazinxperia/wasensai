package com.mazin.wasensai.ui.screens.viewer

import android.net.Uri
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.mazin.wasensai.R
import com.mazin.wasensai.ui.navigation.Screen
import com.mazin.wasensai.ui.theme.viewerColors
import com.mazin.wasensai.viewmodel.ViewerLoadState
import com.mazin.wasensai.viewmodel.ViewerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Ease-out expo: fast start, very smooth landing — feels like iPhone/WhatsApp
private val SlideEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private const val ViewerNavAnimMs = 420

private enum class ViewerNavDirection {
    Forward,
    Backward,
    None
}

private val ViewerRootRouteOrder = mapOf(
    "viewer_chats" to 0,
    "viewer_calls" to 1,
    "viewer_starred" to 2,
    "viewer_account" to 3
)

private fun viewerRouteLevel(route: String?): Int = when (route) {
    "viewer_chats",
    "viewer_calls",
    "viewer_starred",
    "viewer_account" -> 0
    Screen.ChatScreen.route -> 1
    Screen.MediaGallery.route,
    Screen.ContactInfo.route -> 2
    Screen.FullScreenMedia.route -> 3
    else -> 0
}

private fun resolveViewerNavDirection(
    fromRoute: String?,
    toRoute: String?
): ViewerNavDirection {
    if (fromRoute == null || toRoute == null || fromRoute == toRoute) {
        return ViewerNavDirection.None
    }

    val fromRootOrder = ViewerRootRouteOrder[fromRoute]
    val toRootOrder = ViewerRootRouteOrder[toRoute]
    if (fromRootOrder != null && toRootOrder != null) {
        return if (toRootOrder > fromRootOrder) {
            ViewerNavDirection.Forward
        } else {
            ViewerNavDirection.Backward
        }
    }

    val fromLevel = viewerRouteLevel(fromRoute)
    val toLevel = viewerRouteLevel(toRoute)
    return if (toLevel > fromLevel) {
        ViewerNavDirection.Forward
    } else {
        ViewerNavDirection.Backward
    }
}

private fun viewerEnterOffset(direction: ViewerNavDirection, fullWidth: Int): Int = when (direction) {
    ViewerNavDirection.Forward -> fullWidth
    ViewerNavDirection.Backward -> -fullWidth
    ViewerNavDirection.None -> 0
}

private fun viewerExitOffset(direction: ViewerNavDirection, fullWidth: Int): Int = when (direction) {
    ViewerNavDirection.Forward -> -fullWidth
    ViewerNavDirection.Backward -> fullWidth
    ViewerNavDirection.None -> 0
}

// File-level: created once, never recreated on recompose
private val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)
private val FFPathFamily = FontFamily(Font(R.font.led_dot_matrix, FontWeight.Normal))

@Composable
fun ViewerScreen(
    onBackClick: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel()
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.loadWaView(it) }
    }
    when (loadState) {
        is ViewerLoadState.Idle -> ViewerFilePicker(onPickFile = { filePicker.launch(arrayOf("*/*")) }, onBackClick = onBackClick)
        is ViewerLoadState.Loading -> ViewerLoadingScreen(viewModel = viewModel)
        is ViewerLoadState.Error -> ViewerErrorScreen(message = (loadState as ViewerLoadState.Error).message, onRetry = { filePicker.launch(arrayOf("*/*")) }, onBackClick = onBackClick)
        is ViewerLoadState.Success -> ViewerMainScreen(viewModel = viewModel, onBackClick = onBackClick)
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun ViewerFilePicker(onPickFile: () -> Unit, onBackClick: () -> Unit) {
    val viewerColors = MaterialTheme.viewerColors
    val bgTop = viewerColors.background
    val bgBot = viewerColors.surface
    val cardTop = viewerColors.surface
    val cardBot = viewerColors.surfaceVariant
    val textOnBg = viewerColors.textPrimary
    val textOnCard = viewerColors.textPrimary
    val btnTop = cardTop
    val btnBot = cardBot
    val btnIcon = textOnCard
    val btnTextColor = textOnCard
    val btnTextBrush = Brush.verticalGradient(listOf(btnTextColor, btnTextColor))
    val shadowElev = 20f

    val FolderShape = RoundedCornerShape(40.dp)
    val ButtonShape = RoundedCornerShape(50.dp)

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBot)))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp)
                        .graphicsLayer { shadowElevation = shadowElev; shape = CircleShape; clip = true }
                        .background(Brush.linearGradient(listOf(btnTop, btnBot)))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onBackClick() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = btnIcon, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.weight(1f))
                Text("SENSAI", fontFamily = FFPathFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 4.sp, color = textOnBg)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.weight(0.8f))

            Box(
                modifier = Modifier.size(140.dp)
                    .graphicsLayer { shadowElevation = shadowElev; shape = FolderShape; clip = true }
                    .background(Brush.linearGradient(listOf(cardTop, cardBot)), FolderShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.FolderOpen, null, tint = textOnCard, modifier = Modifier.size(72.dp)) }

            Spacer(Modifier.height(28.dp))
            Text("OPEN A .WAVIEW FILE", fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.5).sp, color = textOnBg, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("Select the .waview archive you exported from this device", fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = textOnBg.copy(alpha = 0.55f), textAlign = TextAlign.Center, lineHeight = 20.sp)

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier.fillMaxWidth().height(72.dp)
                    .graphicsLayer { shadowElevation = shadowElev; shape = ButtonShape; clip = true }
                    .background(Brush.linearGradient(listOf(btnTop, btnBot)), ButtonShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onPickFile() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CHOOSE FILE", fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 3.sp, style = TextStyle(brush = btnTextBrush))
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Rounded.UploadFile, null, tint = btnIcon, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ViewerLoadingScreen(viewModel: ViewerViewModel) {
    val viewerColors = MaterialTheme.viewerColors
    val bgTop = viewerColors.background
    val bgBot = viewerColors.surface
    val textOnBg = viewerColors.textPrimary
    val accent = viewerColors.action
    val loadingUi by viewModel.loadingUiState.collectAsStateWithLifecycle()
    val animatedProgress by animateFloatAsState(
        targetValue = (loadingUi.overallPercent.coerceIn(0, 100) / 100f),
        animationSpec = tween(durationMillis = 220, easing = SlideEasing),
        label = "viewerLoadingProgress"
    )

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBot)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${loadingUi.overallPercent.coerceIn(0, 100)}%",
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 52.sp,
                color = textOnBg
            )
            Spacer(modifier = Modifier.height(28.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(50.dp)),
                color = accent,
                trackColor = viewerColors.surfaceVariant
            )
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = loadingUi.currentPhase.ifEmpty { "Loading archive..." },
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 1.5.sp,
                color = textOnBg,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = loadingUi.currentLogLine.ifEmpty { "Preparing viewer..." },
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                color = textOnBg.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(1.dp)
                    .background(textOnBg.copy(alpha = 0.12f))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "SENSAI V1.0",
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                letterSpacing = 3.sp,
                color = textOnBg.copy(alpha = 0.35f)
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun ViewerErrorScreen(message: String, onRetry: () -> Unit, onBackClick: () -> Unit) {
    val viewerColors = MaterialTheme.viewerColors
    val bgTop = viewerColors.background
    val bgBot = viewerColors.surface
    val cardTop = viewerColors.surface
    val cardBot = viewerColors.surfaceVariant
    val textOnBg = viewerColors.textPrimary
    val textOnCard = viewerColors.textPrimary
    val mutedOnCard = textOnCard.copy(alpha = 0.6f)
    val btnTop = cardTop
    val btnBot = cardBot
    val btnIcon = textOnCard
    val btnTextColor = textOnCard
    val btnTextBrush = Brush.verticalGradient(listOf(btnTextColor, btnTextColor))
    val shadowElev = 24f
    val recessed = textOnCard.copy(alpha = 0.1f)
    val SoftRed = viewerColors.error


    val CardShape = RoundedCornerShape(28.dp)
    val ButtonShape = RoundedCornerShape(50.dp)

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgTop, bgBot)))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp)
                        .graphicsLayer { shadowElevation = shadowElev; shape = CircleShape; clip = true }
                        .background(Brush.linearGradient(listOf(btnTop, btnBot)))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onBackClick() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = btnIcon, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.weight(1f))
                Text("SENSAI", fontFamily = FFPathFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 4.sp, color = textOnBg)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.weight(0.15f))

            Box(
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer { shadowElevation = shadowElev; shape = CardShape; clip = true }
                    .background(Brush.linearGradient(listOf(cardTop, cardBot)), CardShape)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(SoftRed.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = SoftRed, modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("FAILED TO LOAD FILE", fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = textOnCard, textAlign = TextAlign.Center, letterSpacing = (-0.5).sp)
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(recessed).padding(16.dp)) {
                        Text(message, fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = mutedOnCard, textAlign = TextAlign.Center, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(24.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(58.dp)
                            .graphicsLayer { shadowElevation = shadowElev; shape = ButtonShape; clip = true }
                            .background(Brush.linearGradient(listOf(btnTop, btnBot)), ButtonShape)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onRetry() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("TRY AGAIN", fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 2.sp, style = TextStyle(brush = btnTextBrush))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.Refresh, null, tint = btnIcon, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("GO BACK", fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 3.sp, color = mutedOnCard,
                        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onBackClick() })
                }
            }
            Spacer(Modifier.weight(0.2f))
        }
    }
}

@Composable
private fun ViewerMainScreen(viewModel: ViewerViewModel, onBackClick: () -> Unit) {
    val innerNav = rememberNavController()
    val navBackStack by innerNav.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val viewerColors = MaterialTheme.viewerColors
    var isNavigating by remember { mutableStateOf(false) }
    val rootRoutes = remember {
        setOf("viewer_chats", "viewer_calls", "viewer_starred", "viewer_account")
    }
    LaunchedEffect(currentRoute) {
        delay(ViewerNavAnimMs.toLong() + 30L)
        isNavigating = false
    }
    val openChatRoute: (Long) -> Unit = remember(innerNav, viewModel, currentRoute, isNavigating) {
        { chatId ->
            val targetRoute = Screen.ChatScreen.createRoute(chatId)
            val alreadyOnTargetChat = currentRoute == targetRoute
            if (!isNavigating && !alreadyOnTargetChat) {
                isNavigating = true
                innerNav.navigate(targetRoute) {
                    launchSingleTop = true
                }
                if (!viewModel.showPreloadedChatIfAvailable(chatId)) {
                    viewModel.openChat(chatId)
                }
            }
        }
    }
    val openRootRoute: (String) -> Unit = remember(innerNav, currentRoute, isNavigating) {
        { route ->
            if (!isNavigating && currentRoute != route) {
                isNavigating = true
                innerNav.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(innerNav.graph.startDestinationId) {
                        saveState = true
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute !in rootRoutes) return@Scaffold
            NavigationBar(containerColor = viewerColors.footer, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding(), windowInsets = WindowInsets(0)) {
                NavigationBarItem(selected = currentRoute == "viewer_chats", onClick = { openRootRoute("viewer_chats") }, icon = { Icon(Icons.AutoMirrored.Rounded.Chat, null) }, label = { Text("Chats") })
                NavigationBarItem(selected = currentRoute == "viewer_calls", onClick = { openRootRoute("viewer_calls") }, icon = { Icon(Icons.Rounded.Phone, null) }, label = { Text("Calls") })
                NavigationBarItem(selected = currentRoute == "viewer_starred", onClick = { openRootRoute("viewer_starred") }, icon = { Icon(Icons.Rounded.Star, null) }, label = { Text("Starred") })
                NavigationBarItem(selected = currentRoute == "viewer_account", onClick = { openRootRoute("viewer_account") }, icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("Settings") })
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                color = viewerColors.background
            ) {
                NavHost(
                    navController = innerNav,
                    startDestination = "viewer_chats",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        val direction = resolveViewerNavDirection(
                            fromRoute = initialState.destination.route,
                            toRoute = targetState.destination.route
                        )
                        if (direction == ViewerNavDirection.None) {
                            androidx.compose.animation.EnterTransition.None
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { viewerEnterOffset(direction, it) },
                                animationSpec = tween(ViewerNavAnimMs, easing = SlideEasing)
                            )
                        }
                    },
                    exitTransition = {
                        val direction = resolveViewerNavDirection(
                            fromRoute = initialState.destination.route,
                            toRoute = targetState.destination.route
                        )
                        if (direction == ViewerNavDirection.None) {
                            androidx.compose.animation.ExitTransition.None
                        } else {
                            // Old screen slides out at 30% of its width — subtle push feel
                            slideOutHorizontally(
                                targetOffsetX = { (viewerExitOffset(direction, it) * 0.30f).toInt() },
                                animationSpec = tween(ViewerNavAnimMs, easing = SlideEasing)
                            )
                        }
                    },
                    popEnterTransition = {
                        val direction = resolveViewerNavDirection(
                            fromRoute = initialState.destination.route,
                            toRoute = targetState.destination.route
                        )
                        if (direction == ViewerNavDirection.None) {
                            androidx.compose.animation.EnterTransition.None
                        } else {
                            // Returning screen slides back in from 30% — matches exit push
                            slideInHorizontally(
                                initialOffsetX = { (viewerEnterOffset(direction, it) * 0.30f).toInt() },
                                animationSpec = tween(ViewerNavAnimMs, easing = SlideEasing)
                            )
                        }
                    },
                    popExitTransition = {
                        val direction = resolveViewerNavDirection(
                            fromRoute = initialState.destination.route,
                            toRoute = targetState.destination.route
                        )
                        if (direction == ViewerNavDirection.None) {
                            androidx.compose.animation.ExitTransition.None
                        } else {
                            slideOutHorizontally(
                                targetOffsetX = { viewerExitOffset(direction, it) },
                                animationSpec = tween(ViewerNavAnimMs, easing = SlideEasing)
                            )
                        }
                    }
                ) {
                composable("viewer_chats") {
                    ChatListScreen(
                        viewModel = viewModel,
                        onChatClick = openChatRoute
                    )
                }
                composable("viewer_calls") {
                    CallsScreen(
                        viewModel = viewModel,
                        onChatClick = openChatRoute
                    )
                }
                composable("viewer_starred") {
                    StarredScreen(
                        viewModel = viewModel,
                        onChatClick = openChatRoute
                    )
                }
                composable("viewer_account") { SettingsScreen(viewModel = viewModel) }
                composable(
                    route = Screen.ChatScreen.route,
                    arguments = listOf(navArgument("chatId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
                    ChatScreen(
                        chatId = chatId,
                        onBackClick = { innerNav.popBackStack() },
                        onMediaClick = { idx -> innerNav.navigate(Screen.FullScreenMedia.createRoute(chatId, idx)) },
                        onGalleryClick = { innerNav.navigate(Screen.MediaGallery.createRoute(chatId)) },
                        onContactInfoClick = { innerNav.navigate(Screen.ContactInfo.createRoute(chatId)) },
                        viewModel = viewModel
                    )
                }
                composable(
                    route = Screen.MediaGallery.route,
                    arguments = listOf(navArgument("chatId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
                    MediaGalleryScreen(
                        chatId = chatId,
                        onBackClick = { innerNav.popBackStack() },
                        onMediaClick = { idx -> innerNav.navigate(Screen.FullScreenMedia.createRoute(chatId, idx)) },
                        viewModel = viewModel
                    )
                }
                composable(
                    route = Screen.FullScreenMedia.route,
                    arguments = listOf(
                        navArgument("chatId") { type = NavType.LongType },
                        navArgument("mediaIndex") { type = NavType.IntType }
                    )
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
                    val mediaIndex = backStackEntry.arguments?.getInt("mediaIndex") ?: 0
                    FullScreenMediaScreen(
                        chatId = chatId,
                        startIndex = mediaIndex,
                        onBackClick = { innerNav.popBackStack() },
                        viewModel = viewModel
                    )
                }
                composable(
                    route = Screen.ContactInfo.route,
                    arguments = listOf(navArgument("chatId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
                    ContactInfoScreen(
                        chatId = chatId,
                        onBackClick = { innerNav.popBackStack() },
                        onChatClick = {
                            innerNav.popBackStack()
                            openChatRoute(chatId)
                        },
                        viewModel = viewModel
                    )
                }
                }
            }
        }
    }
}
