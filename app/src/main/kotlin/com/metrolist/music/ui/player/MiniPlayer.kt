package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.metrolist.music.ui.player.PlayerTopSwitch

// BanglaTune - YouTube Music Style Audio/Video Toggle
// তোমার সবুজ দাগ করা জায়গায় বসবে
@Composable
fun PlayerTopSwitch(
    isVideoMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF2A2A2A))
                .padding(4.dp)
        ) {
            // Audio Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (!isVideoMode) Color.White else Color.Transparent)
                    .clickable { onToggle(false) }
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Audio",
                    color = if (!isVideoMode) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            // Video Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isVideoMode) Color.White else Color.Transparent)
                    .clickable { onToggle(true) }
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Video",
                    color = if (isVideoMode) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// Main Player Wrapper - Lyra Style
@Composable
fun Player(
    // তোমার আগের Player এর Parameter গুলো এখানে থাকবে
) {
    var isVideoMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // ✅ YouTube Music Style
