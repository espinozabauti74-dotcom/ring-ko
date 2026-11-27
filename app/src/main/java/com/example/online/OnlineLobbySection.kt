package com.example.online

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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import com.google.firebase.auth.FirebaseUser

@Composable
fun OnlineLobbySection(
    currentUser: FirebaseUser?,
    playerName: String,
    equippedGloveId: String,
    onEnterOnlineMatch: (String) -> Unit
) {
    if (currentUser == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            GoogleSignInGateCard(onAuthSuccess = {})
        }
        return
    }

    val uid = currentUser.uid
    key(uid) {
        val lobbyVm: OnlineLobbyViewModel = viewModel(
            key = uid,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    OnlineLobbyViewModel(
                        repository = OnlineRepository(app),
                        currentUserId = uid
                    )
                }
            }
        )

        LaunchedEffect(uid, playerName, equippedGloveId) {
            lobbyVm.syncProfile(playerName, equippedGloveId)
        }

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }

        val profileState by lobbyVm.profileState.collectAsStateWithLifecycle()
        val waitingRoomsState by lobbyVm.waitingRoomsState.collectAsStateWithLifecycle()
        val busy by lobbyVm.busy.collectAsStateWithLifecycle()
        val actionError by lobbyVm.actionError.collectAsStateWithLifecycle()

        var roomCodeInput by remember { mutableStateOf("") }
        var selectedRounds by remember { mutableIntStateOf(3) }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Online Boxer Account & Record Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArenaCard),
                    border = BorderStroke(1.dp, ArenaBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = StaminaEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = playerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ChalkWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Conectado con Google",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StaminaEmerald
                                    )
                                }
                            }
                            IconButton(
                                onClick = { signOutFromFirebase(credentialManager, scope) },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("online_sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Cerrar sesión",
                                    tint = MutedSteel
                                )
                            }
                        }

                        when (val prof = profileState) {
                            is OnlineUiState.Success -> {
                                val data = prof.data
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OnlineStatChip("VICTORIAS", (data?.onlineWins ?: 0L).toString(), ChampionshipGold)
                                    OnlineStatChip("DERROTAS", (data?.onlineLosses ?: 0L).toString(), CrimsonCorner)
                                    OnlineStatChip("KOs", (data?.onlineKos ?: 0L).toString(), CobaltGlow)
                                }
                            }
                            OnlineUiState.Loading -> {
                                Text("Cargando récord online...", style = MaterialTheme.typography.labelSmall, color = MutedSteel)
                            }
                            is OnlineUiState.Error -> {
                                Text(prof.message, style = MaterialTheme.typography.labelSmall, color = CrimsonCorner)
                            }
                        }
                    }
                }
            }

            if (actionError != null) {
                item {
                    Surface(
                        color = CrimsonCorner.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, CrimsonCorner),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = actionError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChalkWhite,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Create Room Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArenaCard),
                    border = BorderStroke(1.5.dp, ChampionshipGold.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "CREAR SALA ONLINE 1v1",
                            style = MaterialTheme.typography.titleLarge,
                            color = ChampionshipGold
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
                                        .height(46.dp)
                                ) {
                                    Text("$rounds ROUNDS", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                lobbyVm.createRoom(
                                    hostName = playerName,
                                    hostGloveId = equippedGloveId,
                                    maxRounds = selectedRounds,
                                    onRoomReady = onEnterOnlineMatch
                                )
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChampionshipGold,
                                contentColor = ArenaObsidian
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("create_online_room_button")
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = ArenaObsidian, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CREAR SALA Y ESPERAR RIVAL", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        HorizontalDivider(color = ArenaBorder)

                        Text(
                            text = "UNIRSE CON CÓDIGO",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedSteel
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = roomCodeInput,
                                onValueChange = { if (it.length <= 12) roomCodeInput = it.uppercase() },
                                placeholder = { Text("Ej. K8M4P2", color = MutedSteel) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ChampionshipGold,
                                    unfocusedBorderColor = ArenaBorder,
                                    focusedTextColor = ChalkWhite,
                                    unfocusedTextColor = ChalkWhite,
                                    cursorColor = ChampionshipGold
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("join_room_code_input")
                            )
                            Button(
                                onClick = {
                                    lobbyVm.joinRoom(
                                        roomCode = roomCodeInput,
                                        guestName = playerName,
                                        guestGloveId = equippedGloveId,
                                        onRoomReady = onEnterOnlineMatch
                                    )
                                },
                                enabled = !busy && roomCodeInput.trim().length >= 4,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CobaltGlow,
                                    contentColor = ArenaObsidian
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("join_room_button")
                            ) {
                                Text("UNIRSE", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Open Public Rooms
            item {
                Text(
                    text = "SALAS ABIERTAS EN VIVO",
                    style = MaterialTheme.typography.titleMedium,
                    color = ChalkWhite
                )
            }

            when (val roomsState = waitingRoomsState) {
                OnlineUiState.Loading -> {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = ChampionshipGold, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Buscando rivales...", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                        }
                    }
                }
                is OnlineUiState.Error -> {
                    item {
                        Text(roomsState.message, style = MaterialTheme.typography.bodyMedium, color = CrimsonCorner)
                    }
                }
                is OnlineUiState.Success -> {
                    val joinable = roomsState.data.filter { it.hostUid != uid }
                    if (joinable.isEmpty()) {
                        item {
                            Surface(
                                color = ArenaCard.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, ArenaBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No hay salas esperando ahora mismo. ¡Crea una arriba y comparte tu código!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    } else {
                        items(joinable, key = { it.roomCode }) { room ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ArenaCard),
                                border = BorderStroke(1.dp, ArenaBorder),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = room.hostName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = ChalkWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Sala ${room.roomCode} • ${room.maxRounds} rounds",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedSteel
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            lobbyVm.joinRoom(
                                                roomCode = room.roomCode,
                                                guestName = playerName,
                                                guestGloveId = equippedGloveId,
                                                onRoomReady = onEnterOnlineMatch
                                            )
                                        },
                                        enabled = !busy,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ChampionshipGold,
                                            contentColor = ArenaObsidian
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Icon(Icons.Default.SportsMma, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("¡PELEAR!", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineStatChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedSteel)
    }
}
