package com.mazin.wasensai.ui.screens.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mazin.wasensai.ui.theme.viewerColors
import com.mazin.wasensai.ui.theme.ThemeMode
import com.mazin.wasensai.viewmodel.HomeViewModel
import com.mazin.wasensai.viewmodel.ViewerViewModel

@Composable
fun SettingsScreen(viewModel: ViewerViewModel) {
    val viewerColors = MaterialTheme.viewerColors
    val homeViewModel: HomeViewModel = hiltViewModel()
    val themeMode by homeViewModel.themeMode.collectAsStateWithLifecycle()
    val exportInfo by viewModel.exportInfo.collectAsStateWithLifecycle()

    val phone = exportInfo?.phoneNumber?.removePrefix("+") ?: ""
    val isPhoneUnknown = phone.isEmpty() || phone == "0"

    // Avatar: try own JID first, then "me" key, then leave null for Person icon fallback
    val accountAvatarFile = rememberAvatarFile(
        viewModel = viewModel,
        jid = if (!isPhoneUnknown) "$phone@s.whatsapp.net" else "me"
    )
    val myAvatarFile = accountAvatarFile ?: rememberAvatarFile(viewModel, "me")

    val rawPhone = exportInfo?.phoneNumber ?: ""
    val displayName = when {
        isPhoneUnknown -> "Your Account"
        rawPhone.startsWith("+") -> rawPhone
        rawPhone.isNotEmpty() -> "+$rawPhone"
        else -> "Your Account"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(viewerColors.header)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(viewerColors.header)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {}) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = viewerColors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                "Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = viewerColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Search, null, tint = viewerColors.textPrimary, modifier = Modifier.size(22.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Profile Section ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(viewerColors.background)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                if (isValidViewerFile(myAvatarFile)) {
                    AsyncImage(
                        model = myAvatarFile,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(viewerColors.placeholder),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = viewerColors.textSecondary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Name + status pill
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = viewerColors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, viewerColors.textSecondary, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "😊 What's happening?",
                            fontSize = 13.sp,
                            color = viewerColors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // QR icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.dp, viewerColors.divider, RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.QrCode2,
                        contentDescription = null,
                        tint = viewerColors.icon,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = viewerColors.divider)
            Spacer(modifier = Modifier.height(8.dp))

            // ── Settings List ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(viewerColors.background)
            ) {
                ThemeModeSection(
                    themeMode = themeMode,
                    onThemeModeChange = homeViewModel::setThemeMode
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Rounded.PersonAdd,
                    label = "Invite a contact",
                    subtitle = "Invite people to chat on WhatsApp"
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Rounded.VpnKey,
                    label = "Account",
                    subtitle = "Security notifications, change number"
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Rounded.Lock,
                    label = "Privacy",
                    subtitle = "Block contacts, disappearing messages"
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Rounded.Face,
                    label = "Avatar",
                    subtitle = "Create, edit, profile photo"
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Rounded.FavoriteBorder,
                    label = "Favourites",
                    subtitle = "Add, reorder, remove"
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    label = "Chats",
                    subtitle = "Theme, wallpapers, chat history"
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Rounded.NotificationsNone,
                    label = "Notifications",
                    subtitle = "Message, group & call tones"
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Rounded.Storage,
                    label = "Storage and data",
                    subtitle = "Network usage, auto-download"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── App version footer ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "WA Sensai v1.0.0",
                    fontSize = 12.sp,
                    color = viewerColors.textSecondary.copy(alpha = 0.6f)
                )
                Text(
                    "Developed by Mazin Ruknuddin",
                    fontSize = 12.sp,
                    color = viewerColors.textSecondary.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeModeSection(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val viewerColors = MaterialTheme.viewerColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Theme", fontSize = 15.sp, color = viewerColors.textPrimary, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark",
                ThemeMode.SYSTEM to "System"
            ).forEach { (mode, label) ->
                val selected = themeMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = viewerColors.surface,
                        labelColor = viewerColors.textSecondary,
                        selectedContainerColor = viewerColors.selectedPill,
                        selectedLabelColor = viewerColors.textPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    label: String,
    subtitle: String
) {
    val viewerColors = MaterialTheme.viewerColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = viewerColors.icon,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = viewerColors.textPrimary)
            Text(subtitle, fontSize = 13.sp, color = viewerColors.textSecondary)
        }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = viewerColors.chevron,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.viewerColors.divider
    )
}
