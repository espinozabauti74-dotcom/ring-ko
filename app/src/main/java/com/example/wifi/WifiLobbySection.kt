package com.example.wifi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ArenaBorder
import com.example.ui.theme.ArenaCard
import com.example.ui.theme.ArenaCardElevated
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CobaltGlow
import com.example.ui.theme.CrimsonCorner
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.StaminaEmerald

@Composable
fun WifiLobbySection(
    playerName: String,
    onHostMatch: (rounds: Int) -> Unit,
    onJoinMatch: (hostIp: String, port: Int) -> Unit
) {
    val context = LocalContext.current
    val discoveryManager = remember(context) { WifiDiscoveryManager(context) }

    DisposableEffect(discoveryManager) {
        discoveryManager.startDiscovery()
        onDispose { discoveryManager.stopDiscovery() }
    }

    val rooms by discoveryManager.rooms.collectAsStateWithLifecycle()
    val isScanning by discoveryManager.isScanning.collectAsStateWithLifecycle()

    var selectedRounds by rememberSaveable { mutableIntStateOf(3) }
    var manualAddress by rememberSaveable { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ArenaCard),
                border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Surface(
                        color = StaminaEmerald.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Wi-Fi Local",
                            tint = StaminaEmerald,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "DUELO WI-FI LOCAL",
                                style = MaterialTheme.typography.titleMedium,
                                color = ChampionshipGold
                            )
                            Surface(
                                color = StaminaEmerald.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "SIN CUENTA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StaminaEmerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Jugador: $playerName • Conecten ambos celulares al mismo Wi-Fi o Zona Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSteel
                        )
                    }
                }
            }
        }

        // Create Room Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ArenaCard),
                border = BorderStroke(1.dp, ArenaBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "CREAR SALA WI-FI",
                        style = MaterialTheme.typography.titleMedium,
                        color = ChalkWhite
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(3, 10).forEach { rounds ->
                            val active = selectedRounds == rounds
                            OutlinedButton(
                                onClick = { selectedRounds = rounds },
                                border = BorderStroke(1.5.dp, if (active) ChampionshipGold else ArenaBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (active) ChampionshipGold.copy(alpha = 0.18f) else ArenaCardElevated,
                                    contentColor = if (active) ChampionshipGold else ChalkWhite
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("wifi_rounds_$rounds")
                            ) {
                                Text("$rounds ROUNDS", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    Button(
                        onClick = { onHostMatch(selectedRounds) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChampionshipGold,
                            contentColor = ArenaObsidian
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("wifi_create_room_button")
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CREAR SALA WI-FI", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Auto-discovered rooms header
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SALAS EN TU WI-FI",
                        style = MaterialTheme.typography.titleMedium,
                        color = ChalkWhite
                    )
                    if (isScanning) {
                        CircularProgressIndicator(
                            color = StaminaEmerald,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        discoveryManager.stopDiscovery()
                        discoveryManager.startDiscovery()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("wifi_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Buscar salas de nuevo",
                        tint = ChampionshipGold
                    )
                }
            }
        }

        if (rooms.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArenaCard),
                    border = BorderStroke(1.dp, ArenaBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiFind,
                            contentDescription = null,
                            tint = MutedSteel,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Buscando rivales en tu red Wi-Fi. Cuando el otro celular cree una sala aparecerá aquí automáticamente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                }
            }
        } else {
            items(rooms, key = { it.serviceName }) { room ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArenaCardElevated),
                    border = BorderStroke(1.5.dp, StaminaEmerald.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = room.hostName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = ChalkWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${room.rounds} ROUNDS • ${room.hostIp}:${room.port}",
                                style = MaterialTheme.typography.labelMedium,
                                color = StaminaEmerald
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onJoinMatch(room.hostIp, room.port) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChampionshipGold,
                                contentColor = ArenaObsidian
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("wifi_join_${room.serviceName}")
                        ) {
                            Icon(Icons.Default.SportsMma, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("UNIRSE", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Manual IP fallback (works even on mobile hotspots with mDNS disabled)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ArenaCard),
                border = BorderStroke(1.dp, ArenaBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "CONECTAR POR IP DIRECTA",
                        style = MaterialTheme.typography.titleMedium,
                        color = CobaltGlow
                    )
                    Text(
                        text = "Si no aparece arriba, escribe la IP que muestra el celular anfitrión (ej. 192.168.1.42).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSteel
                    )
                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonCorner
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = manualAddress,
                            onValueChange = {
                                manualAddress = it
                                inputError = null
                            },
                            label = { Text("IP (ej. 192.168.43.1)", color = MutedSteel) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CobaltGlow,
                                unfocusedBorderColor = ArenaBorder,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite,
                                cursorColor = CobaltGlow
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wifi_ip_input")
                        )
                        Button(
                            onClick = {
                                val parsed = parseHostAndPort(manualAddress)
                                if (parsed == null) {
                                    inputError = "Ingresa una IP válida (ej. 192.168.1.42)"
                                } else {
                                    onJoinMatch(parsed.first, parsed.second)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CobaltGlow,
                                contentColor = ArenaObsidian
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("wifi_connect_ip_button")
                        ) {
                            Text("UNIRSE", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun parseHostAndPort(raw: String): Pair<String, Int>? {
    val trimmed = raw.trim().replace(" ", "")
    if (trimmed.isBlank()) return null
    val parts = trimmed.split(":")
    return when (parts.size) {
        1 -> {
            val ip = parts[0]
            if (ip.count { it == '.' } >= 1) ip to WIFI_DEFAULT_PORT else null
        }
        2 -> {
            val ip = parts[0]
            val port = parts[1].toIntOrNull() ?: return null
            if (ip.isNotBlank() && port in 1..65535) ip to port else null
        }
        else -> null
    }
}
