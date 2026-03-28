package com.mazin.wasensai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.mazin.wasensai.R
import com.mazin.wasensai.ui.theme.viewerColors

@Composable
fun ChatWallpaper(modifier: Modifier = Modifier.fillMaxSize()) {
    val viewerColors = MaterialTheme.viewerColors
    val wallpaperRes = if (viewerColors.background.luminance() < 0.5f) {
        R.drawable.chat_wallpaper_dark
    } else {
        R.drawable.chat_wallpaper_light
    }

    Box(
        modifier = modifier
    ) {
        Image(
            painter = painterResource(id = wallpaperRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
