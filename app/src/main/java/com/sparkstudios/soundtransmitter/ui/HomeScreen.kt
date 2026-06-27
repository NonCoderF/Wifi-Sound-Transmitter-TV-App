package com.sparkstudios.soundtransmitter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.sparkstudios.soundtransmitter.ui.theme.SoundTransmitterTheme
import com.sparkstudios.soundtransmitter.utils.NetworkUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    isStreaming: Boolean = false,
    onStartStreaming: () -> Unit = {},
    onStopStreaming: () -> Unit = {}
) {
    val ipAddress = remember { NetworkUtils.getIPAddress() }
    val focusRequester = remember { FocusRequester() }

    // Request focus on launch to resolve Android TV navigation requiring two clicks
    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    // Modern TV dark gradient background
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0C0A1A), // Deep dark indigo-black
            Color(0xFF140E2D), // Rich dark purple
            Color(0xFF080612)  // Near-black
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Pane: Title, Status and Action Button
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Sound Transmitter",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Stream system audio over local Wi-Fi with ultra-low latency.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = if (isStreaming) Color(0xFF1B3D23) else Color(0xFF23203F),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isStreaming) Color(0xFF2E7D32) else Color(0xFF4A4475),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isStreaming) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                shape = RoundedCornerShape(5.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isStreaming) "STREAMING ACTIVE" else "SERVER IDLE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (isStreaming) Color(0xFF81C784) else Color(0xFFB0BEC5)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = if (isStreaming) onStopStreaming else onStartStreaming,
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
                    Text(
                        text = if (isStreaming) "Stop Transmitting" else "Start Transmitting",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Right Pane: Connection Details & Guide Card
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF18152E).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "Connection Guide",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "1. Connect receiver device to the same Wi-Fi network.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "2. Connect to the WebSocket endpoint below:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // WebSocket URL Terminal Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0C091A), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "ws://$ipAddress:8080/audio",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFFD0BCFF)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Audio Info Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Format",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "16-bit PCM",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Column {
                                Text(
                                    text = "Sample Rate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "48,000 Hz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Column {
                                Text(
                                    text = "Channels",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "Stereo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(
    device = Devices.TABLET
)
@Composable
private fun HomeScreenPreview() {
    SoundTransmitterTheme {
        HomeScreen()
    }
}

