package com.example.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BoxerAppearance
import com.example.model.FighterRoster
import com.example.model.GloveItem
import com.example.ui.components.drawPixelArtBoxer
import com.example.ui.theme.ArenaBorder
import com.example.ui.theme.ArenaCard
import com.example.ui.theme.ArenaCardElevated
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.ArenaSlate
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CobaltGlow
import com.example.ui.theme.CrimsonCorner
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.StaminaEmerald
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatLobbySection(
    state: ChatRoomUiState,
    playerName: String,
    appearance: BoxerAppearance,
    equippedGlove: GloveItem,
    onCreateRoom: (roomTitle: String) -> Unit,
    onJoinRoom: (code: String) -> Unit,
    onLeaveRoom: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSelectCharacter: (String) -> Unit,
    onSelectRounds: (Int) -> Unit,
    onSendChallenge: (targetPeerId: String?, targetName: String?) -> Unit,
    onCancelChallenge: () -> Unit,
    onAcceptChallenge: () -> Unit,
    onDeclineChallenge: () -> Unit
) {
    if (state.roomCode.isEmpty()) {
        ChatCreateOrJoinView(
            playerName = playerName,
            errorMessage = state.errorMessage,
            onCreateRoom = onCreateRoom,
            onJoinRoom = onJoinRoom
        )
    } else {
        ChatActiveRoomView(
            state = state,
            appearance = appearance,
            equippedGlove = equippedGlove,
            onLeaveRoom = onLeaveRoom,
            onSendMessage = onSendMessage,
            onSelectCharacter = onSelectCharacter,
            onSelectRounds = onSelectRounds,
            onSendChallenge = onSendChallenge,
            onCancelChallenge = onCancelChallenge,
            onAcceptChallenge = onAcceptChallenge,
            onDeclineChallenge = onDeclineChallenge
        )
    }
}

@Composable
private fun ChatCreateOrJoinView(
    playerName: String,
    errorMessage: String?,
    onCreateRoom: (String) -> Unit,
    onJoinRoom: (String) -> Unit
) {
    var roomTitleDraft by rememberSaveable { mutableStateOf("") }
    var joinCodeDraft by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

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
                        color = CobaltGlow.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = "Chats online por código",
                            tint = CobaltGlow,
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
                                text = "CHATS ONLINE POR CÓDIGO",
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
                            text = "Jugador: $playerName • Crea un chat, comparte el código y reta a tus amigos a pelear 1v1 online sin iniciar sesión.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSteel
                        )
                    }
                }
            }
        }

        val displayError = localError ?: errorMessage
        if (!displayError.isNullOrBlank()) {
            item {
                Surface(
                    color = CrimsonCorner.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, CrimsonCorner.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkWhite,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Create chat room card
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
                        text = "CREAR CHAT NUEVO",
                        style = MaterialTheme.typography.titleMedium,
                        color = ChalkWhite
                    )
                    Text(
                        text = "Se generará un código único de 6 caracteres para que tus amigos entren desde cualquier lugar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSteel
                    )
                    OutlinedTextField(
                        value = roomTitleDraft,
                        onValueChange = { if (it.length <= 24) roomTitleDraft = it },
                        label = { Text("Nombre del chat (opcional)", color = MutedSteel) },
                        placeholder = { Text("Ej. Club de Boxeo", color = MutedSteel.copy(alpha = 0.7f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampionshipGold,
                            unfocusedBorderColor = ArenaBorder,
                            focusedTextColor = ChalkWhite,
                            unfocusedTextColor = ChalkWhite,
                            cursorColor = ChampionshipGold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_room_title_input")
                    )
                    Button(
                        onClick = {
                            localError = null
                            onCreateRoom(roomTitleDraft)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChampionshipGold,
                            contentColor = ArenaObsidian
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("chat_create_room_button")
                    ) {
                        Icon(Icons.Default.AddComment, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CREAR CHAT Y GENERAR CÓDIGO", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Join existing chat room by code
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
                        text = "ENTRAR A UN CHAT CON CÓDIGO",
                        style = MaterialTheme.typography.titleMedium,
                        color = CobaltGlow
                    )
                    Text(
                        text = "Escribe el código que creó tu amigo para unirte al chat y pelear 1v1.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSteel
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = joinCodeDraft,
                            onValueChange = {
                                joinCodeDraft = ChatCodeGenerator.normalizeCode(it)
                                localError = null
                            },
                            label = { Text("Código (ej. K7M2P9)", color = MutedSteel) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = CobaltGlow
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    if (joinCodeDraft.length < 4) {
                                        localError = "Ingresa un código válido de al menos 4 caracteres."
                                    } else {
                                        onJoinRoom(joinCodeDraft)
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CobaltGlow,
                                unfocusedBorderColor = ArenaBorder,
                                focusedTextColor = ChalkWhite,
                                unfocusedTextColor = ChalkWhite,
                                cursorColor = CobaltGlow
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_join_code_input")
                        )
                        Button(
                            onClick = {
                                if (joinCodeDraft.length < 4) {
                                    localError = "Ingresa un código válido de al menos 4 caracteres."
                                } else {
                                    localError = null
                                    onJoinRoom(joinCodeDraft)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CobaltGlow,
                                contentColor = ArenaObsidian
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("chat_join_room_button")
                        ) {
                            Text("ENTRAR", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatActiveRoomView(
    state: ChatRoomUiState,
    appearance: BoxerAppearance,
    equippedGlove: GloveItem,
    onLeaveRoom: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSelectCharacter: (String) -> Unit,
    onSelectRounds: (Int) -> Unit,
    onSendChallenge: (targetPeerId: String?, targetName: String?) -> Unit,
    onCancelChallenge: () -> Unit,
    onAcceptChallenge: () -> Unit,
    onDeclineChallenge: () -> Unit
) {
    val context = LocalContext.current
    var messageDraft by rememberSaveable { mutableStateOf("") }
    var copiedCode by remember { mutableStateOf(false) }
    var showFighterPicker by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(copiedCode) {
        if (copiedCode) {
            kotlinx.coroutines.delay(1800L)
            copiedCode = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        // Room header with copyable code & status
        Card(
            colors = CardDefaults.cardColors(containerColor = ArenaCard),
            border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.roomTitle.ifBlank { "Sala ${state.roomCode}" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = ChalkWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isConnected) StaminaEmerald else WarningAmber)
                            )
                            Text(
                                text = "${state.connectionLabel} • ${state.members.size} en sala",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.isConnected) StaminaEmerald else WarningAmber
                            )
                        }
                    }

                    // Copyable room code pill
                    Surface(
                        color = ChampionshipGold.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ChampionshipGold),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Código de sala", state.roomCode))
                                copiedCode = true
                            }
                            .testTag("chat_copy_code_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = if (copiedCode) "COPIADO" else state.roomCode,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = ChampionshipGold
                            )
                            Icon(
                                imageVector = if (copiedCode) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copiar código",
                                tint = ChampionshipGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onLeaveRoom,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ArenaCardElevated)
                            .testTag("chat_leave_room_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Salir del chat",
                            tint = CrimsonCorner
                        )
                    }
                }

                // Friends strip + direct challenge buttons
                if (state.members.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.members, key = { it.peerId }) { member ->
                            val isMe = member.peerId == state.myPeerId
                            Surface(
                                color = ArenaCardElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isMe) ChampionshipGold.copy(alpha = 0.6f) else CobaltGlow.copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Box(modifier = Modifier.size(28.dp)) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawPixelArtBoxer(
                                                centerX = size.width * 0.5f,
                                                bottomY = size.height * 0.95f,
                                                spriteHeight = size.height * 0.9f,
                                                facingRight = true,
                                                skinBase = Color(member.skinArgb),
                                                hairColor = Color(member.hairArgb),
                                                trunksColor = Color(member.trunksArgb),
                                                gloveColor = Color(member.gloveArgb)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = if (isMe) "${member.name} (Tú)" else member.name,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = ChalkWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 96.dp)
                                        )
                                        val fighterLabel = if (member.characterId == "custom") {
                                            "Skin propia"
                                        } else {
                                            FighterRoster.byId(member.characterId).name
                                        }
                                        Text(
                                            text = fighterLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedSteel,
                                            maxLines = 1
                                        )
                                    }
                                    if (!isMe) {
                                        Surface(
                                            color = ChampionshipGold,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onSendChallenge(member.peerId, member.name) }
                                                .testTag("chat_challenge_member_${member.peerId}")
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Text(
                                                    text = "RETAR",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = ArenaObsidian
                                                )
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

        // Incoming challenge banner
        AnimatedVisibility(visible = state.incomingChallenge != null) {
            val challenge = state.incomingChallenge
            if (challenge != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArenaCardElevated),
                    border = BorderStroke(2.dp, ChampionshipGold),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsMma,
                                contentDescription = null,
                                tint = ChampionshipGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "¡${challenge.fromName} te reta a pelear 1v1 (${challenge.rounds} rounds)!",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = ChalkWhite,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onAcceptChallenge,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StaminaEmerald,
                                    contentColor = ArenaObsidian
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("chat_accept_challenge_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ACEPTAR PELEA", style = MaterialTheme.typography.labelLarge)
                            }
                            OutlinedButton(
                                onClick = onDeclineChallenge,
                                border = BorderStroke(1.dp, CrimsonCorner),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonCorner),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("chat_decline_challenge_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("NO", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        // Collapsible fighter & rounds bar
        Surface(
            color = ArenaCard,
            border = BorderStroke(1.dp, ArenaBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showFighterPicker = !showFighterPicker }
                        .testTag("chat_toggle_fighter_picker")
                ) {
                    val chosenName = if (state.selectedCharacterId == "custom") {
                        "Mi Skin"
                    } else {
                        FighterRoster.byId(state.selectedCharacterId).name
                    }
                    Text(
                        text = "Boxeador: $chosenName • ${state.selectedRounds} Rounds",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ChampionshipGold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (showFighterPicker) "Ocultar" else "Cambiar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSteel
                        )
                        Icon(
                            imageVector = if (showFighterPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Cambiar boxeador",
                            tint = MutedSteel
                        )
                    }
                }

                AnimatedVisibility(visible = showFighterPicker) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 10).forEach { rounds ->
                                val active = state.selectedRounds == rounds
                                OutlinedButton(
                                    onClick = { onSelectRounds(rounds) },
                                    border = BorderStroke(1.dp, if (active) ChampionshipGold else ArenaBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (active) ChampionshipGold.copy(alpha = 0.16f) else ArenaCardElevated,
                                        contentColor = if (active) ChampionshipGold else ChalkWhite
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("chat_rounds_$rounds")
                                ) {
                                    Text("$rounds ROUNDS", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item(key = "custom") {
                                ChatFighterChip(
                                    id = "custom",
                                    label = "Mi Skin",
                                    skinColor = appearance.resolvedSkinColor(),
                                    hairColor = appearance.resolvedHairColor(),
                                    trunksColor = appearance.resolvedTrunksColor(),
                                    gloveColor = equippedGlove.primaryColor,
                                    selected = state.selectedCharacterId == "custom",
                                    onSelect = { onSelectCharacter("custom") }
                                )
                            }
                            items(FighterRoster.fighters, key = { it.id }) { fighter ->
                                ChatFighterChip(
                                    id = fighter.id,
                                    label = fighter.name,
                                    skinColor = fighter.skinTone,
                                    hairColor = fighter.hairColor,
                                    trunksColor = fighter.trunksColor,
                                    gloveColor = fighter.gloveColor,
                                    selected = state.selectedCharacterId == fighter.id,
                                    onSelect = { onSelectCharacter(fighter.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!state.errorMessage.isNullOrBlank()) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = CrimsonCorner,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Chat message history
        Card(
            colors = CardDefaults.cardColors(containerColor = ArenaCard.copy(alpha = 0.94f)),
            border = BorderStroke(1.dp, ArenaBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        isMine = msg.senderId == state.myPeerId
                    )
                }
            }
        }

        // Message composer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = messageDraft,
                onValueChange = { if (it.length <= 280) messageDraft = it },
                placeholder = { Text("Escribe un mensaje al chat...", color = MutedSteel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (messageDraft.isNotBlank()) {
                            onSendMessage(messageDraft)
                            messageDraft = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ChampionshipGold,
                    unfocusedBorderColor = ArenaBorder,
                    focusedTextColor = ChalkWhite,
                    unfocusedTextColor = ChalkWhite,
                    cursorColor = ChampionshipGold,
                    focusedContainerColor = ArenaCard,
                    unfocusedContainerColor = ArenaCard
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )

            IconButton(
                onClick = {
                    if (messageDraft.isNotBlank()) {
                        onSendMessage(messageDraft)
                        messageDraft = ""
                    }
                },
                enabled = messageDraft.isNotBlank(),
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (messageDraft.isNotBlank()) ChampionshipGold else ArenaCardElevated
                    )
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar mensaje",
                    tint = if (messageDraft.isNotBlank()) ArenaObsidian else MutedSteel
                )
            }
        }

        // Bottom 1v1 Fight Challenge Bar
        if (state.outgoingChallengeId != null) {
            Surface(
                color = ArenaCardElevated,
                border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            color = ChampionshipGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Esperando a ${state.outgoingChallengeTargetName ?: "tu amigo"}...",
                            style = MaterialTheme.typography.labelMedium,
                            color = ChalkWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = onCancelChallenge,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("chat_cancel_challenge_button")
                    ) {
                        Text("CANCELAR", color = CrimsonCorner, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Button(
                onClick = { onSendChallenge(null, null) },
                enabled = state.isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonCorner,
                    contentColor = ChalkWhite,
                    disabledContainerColor = ArenaCardElevated,
                    disabledContentColor = MutedSteel
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("chat_challenge_all_button")
            ) {
                Icon(Icons.Default.SportsMma, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "¡RETAR A PELEAR 1v1 ONLINE! (${state.selectedRounds}R)",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ChatFighterChip(
    id: String,
    label: String,
    skinColor: Color,
    hairColor: Color,
    trunksColor: Color,
    gloveColor: Color,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        color = if (selected) ChampionshipGold.copy(alpha = 0.2f) else ArenaCardElevated,
        border = BorderStroke(1.5.dp, if (selected) ChampionshipGold else ArenaBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .testTag("chat_char_$id")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Box(modifier = Modifier.size(26.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPixelArtBoxer(
                        centerX = size.width * 0.5f,
                        bottomY = size.height * 0.95f,
                        spriteHeight = size.height * 0.9f,
                        facingRight = true,
                        skinBase = skinColor,
                        hairColor = hairColor,
                        trunksColor = trunksColor,
                        gloveColor = gloveColor
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (selected) ChampionshipGold else ChalkWhite,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatRoomMessage,
    isMine: Boolean
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = remember(message.timestampMs) { timeFormat.format(Date(message.timestampMs)) }

    if (message.isSystem) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = ArenaSlate,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSteel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        return
    }

    Row(
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = if (isMine) ChampionshipGold.copy(alpha = 0.16f) else ArenaCardElevated,
            border = BorderStroke(
                1.dp,
                if (isMine) ChampionshipGold.copy(alpha = 0.45f) else CobaltGlow.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isMine) 14.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 14.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isMine) "Tú" else message.senderName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isMine) ChampionshipGold else CobaltGlow
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSteel
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChalkWhite
                )
            }
        }
    }
}
