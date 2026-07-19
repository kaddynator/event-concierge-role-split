package com.example.ui.components

import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceOrb(
    state: String, // "idle", "listening", "thinking", "speaking"
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }
    val infiniteTransition = if (animationsEnabled) rememberInfiniteTransition(label = "orb_pulse") else null
    
    // Scale pulse animation
    val pulseScale = if (infiniteTransition != null) {
        val value by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "scale"
        )
        value
    } else 1f
    
    // Wave alpha animation for listening / speaking
    val waveAlpha = if (infiniteTransition != null) {
        val value by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = "alpha"
        )
        value
    } else 0f
    
    // Wave scale animation for waves radiating outwards
    val waveScale = if (infiniteTransition != null) {
        val value by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = "wave_scale"
        )
        value
    } else 1f

    // Rotation for thinking state
    val rotationAngle = if (infiniteTransition != null) {
        val value by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
            label = "rotation"
        )
        value
    } else 0f

    val orbColors = when (state) {
        "listening" -> listOf(Color(0xFF386A4C), Color(0xFF6E9F7D)) // Sage Greens
        "thinking" -> listOf(Color(0xFF2C5282), Color(0xFF4299E1)) // Muted Blues
        "speaking" -> listOf(Color(0xFFD97706), Color(0xFFFBBF24)) // Glowing Amber/Gold
        else -> listOf(Color(0xFFB45309), Color(0xFFF59E0B)) // Primary Amber
    }

    val orbText = when (state) {
        "listening" -> "LISTENING..."
        "thinking" -> "THINKING..."
        "speaking" -> "SPEAKING"
        else -> "TAP TO TALK"
    }

    val icon = when (state) {
        "listening" -> Icons.Default.Hearing
        "thinking" -> Icons.Default.HourglassEmpty
        "speaking" -> Icons.AutoMirrored.Filled.VolumeUp
        else -> Icons.Default.Mic
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .testTag("voice_orb")
                .clickable(onClick = onClick)
        ) {
            // Background waves for listening & speaking
            if (state == "listening" || state == "speaking") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = waveScale,
                            scaleY = waveScale,
                            alpha = waveAlpha
                        )
                        .background(
                            brush = Brush.radialGradient(
                                colors = orbColors.map { it.copy(alpha = 0.4f) }
                            ),
                            shape = CircleShape
                        )
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = waveScale * 0.8f,
                            scaleY = waveScale * 0.8f,
                            alpha = waveAlpha * 1.2f
                        )
                        .background(
                            brush = Brush.radialGradient(
                                colors = orbColors.map { it.copy(alpha = 0.25f) }
                            ),
                            shape = CircleShape
                        )
                )
            }

            // Main Orb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer(
                        scaleX = if (state == "thinking") 1.0f else pulseScale,
                        scaleY = if (state == "thinking") 1.0f else pulseScale,
                        rotationZ = if (state == "thinking") rotationAngle else 0f
                    )
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(colors = orbColors)
                    )
            ) {
                // Outer ring decoration
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = size.minDimension / 2f - 4f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                }

                Icon(
                    imageVector = icon,
                    contentDescription = orbText,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = orbText,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp
        )
    }
}
