/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0
 * BD-Music-Player - NomaTunes Style Home - BanglaTune
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen() {
    // তোমার Screenshot এর মতো State
    var selectedChip by remember { mutableStateOf("Relax") }
    val chips = listOf("Relax", "Sad", "Sleep", "Energize", "Workout", "Focus")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F0FF))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 90.dp) // Mini Player এর জন্য
    ) {
        // Top Bar - NomaTunes এর মতো
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFE8DEF8)))
                Spacer(Modifier.width(8.dp))
                Text("BanglaTune", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("⏰", fontSize = 20.sp)
                Text("📋", fontSize = 20.sp)
                Text("⚙️", fontSize = 20.sp)
            }
        }

        // Chips - Relax Sad Sleep Energize
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chips) { chip ->
                val isSelected = chip == selectedChip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) Color(0xFF4F378A) else Color.White)
                        .clickable { selectedChip = chip }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        chip,
                        color = if (isSelected) Color.White else Color.Black,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Big Cards - EL CONTROL Style
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(5) { index ->
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .height(280.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF1C1C1E))
                        .clickable { }
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
                    ) {
                        Text(
                            "EL CONTROL (Slowed)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text("M4GN • BanglaTune", color = Color.Gray, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                            .size(36.dp).clip(CircleShape).background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶", color = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Keep Listening - তোমার Screenshot এর মতো
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Keep listening", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF4F378A))
            Text(">", color = Color.Gray)
        }

        Spacer(Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(6) {
                Column(modifier = Modifier.width(150.dp)) {
                    Box(
                        modifier = Modifier
                            .height(150.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black)
                    ) {
                        Box(
                            modifier = Modifier.align(Alignment.Center)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x99FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("NUNCA MUDA", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text("Scythermane, NXG", fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Recent & More Sections
        Text("Made for you", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

// Floating Mini Player - Screenshot এর নিচেরটা
@Composable
fun NomaFloatingMiniPlayer() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(50.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("NUNCA MUDA", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Scythermane, NXG", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Text("⏮") }
                IconButton(onClick = {}) { Text("▶️", fontSize = 18.sp) }
                IconButton(onClick = {}) { Text("⏭") }
            }
        }
    }
}
