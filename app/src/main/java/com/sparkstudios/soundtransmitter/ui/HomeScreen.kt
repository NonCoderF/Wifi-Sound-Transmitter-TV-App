package com.sparkstudios.soundtransmitter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.sparkstudios.soundtransmitter.ui.theme.SoundTransmitterTheme
import com.sparkstudios.soundtransmitter.utils.NetworkUtils

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    isStreaming: Boolean = false,
    onStartStreaming: () -> Unit = {},
    onStopStreaming: () -> Unit = {}
) {
    val ipAddress = remember { NetworkUtils.getIPAddress() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sound Transmitter",
            style = MaterialTheme.typography.displayMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Server IP: $ipAddress",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
//        Text(
//            text = "WebSocket URL: ws://$ipAddress:8080/audio",
//            style = MaterialTheme.typography.bodyMedium
//        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = if (isStreaming) onStopStreaming else onStartStreaming) {
            Text(
                text = if (isStreaming)
                    "🟢 Transmitting"
                else
                    "🟢 Idle"
            )
        }
    }
}

@Preview(
    device = Devices.TV_720p
)
@Composable
private fun HomeScreenPreview() {
    SoundTransmitterTheme{
        HomeScreen(

        )
    }
}
