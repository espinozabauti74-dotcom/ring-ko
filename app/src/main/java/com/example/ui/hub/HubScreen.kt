package com.example.ui.hub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.UserSettings
import com.example.model.FighterProfile
import com.example.model.FighterRoster
import com.example.model.GloveItem
import com.example.model.GloveShopCatalog
import com.example.model.PlayerEconomy
import com.example.model.SessionRecord
import com.example.online.OnlineLobbySection
import com.example.wifi.WifiLobbySection
import com.example.ui.components.FighterAvatarBadge
import com.example.ui.components.GlovePreviewBadge
import com.example.ui.theme.ArenaBorder
import com.example.ui.theme.ArenaCard
import com.example.ui.theme.ArenaCardElevated
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.ArenaSlate
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CrimsonCorner
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.StaminaEmerald
import com.google.firebase.auth.FirebaseUser

@Composable
fun HubScreen(
    currentUser: FirebaseUser?,
    sessionRecord: SessionRecord,
    economy: PlayerEconomy,
    settings: UserSettings,
    onAuthSuccess: () -> Unit,
    onStartFight: (String) -> Unit,
    onStartVersus: (String, Int) -> Unit,
    onEnterOnlineMatch: (String) -> Unit,
    onHostWifiMatch: (Int) -> Unit,
    onJoinWifiMatch: (String, Int) -> Unit,
    onBuyOrEquipGlove: (GloveItem) -> Unit,
    onSavePlayerName: (String) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit
) {
    var activeTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Combate, 1 = Versus, 2 = Tienda
    var selectedFighterId by rememberSaveable { mutableStateOf(FighterRoster.fighters.first().id) }
    var showSettings by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showEditName by remember { mutableStateOf(false) }

    val selectedFighter = remember(selectedFighterId) { FighterRoster.byId(selectedFighterId) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArenaObsidian)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_arena_bg),
            contentDescription = "Estadio de boxeo nocturno",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ArenaObsidian.copy(alpha = 0.45f),
                            ArenaObsidian.copy(alpha = 0.75f),
                            ArenaObsidian
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top Bar with Coin Balance + Guide + Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CAMPEONATO • 10 ROUNDS",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChampionshipGold
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayLarge,
                        color = ChalkWhite
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Username chip strictly in the top-right corner of the menu
                    Surface(
                        color = ArenaCard.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, ArenaBorder),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { showEditName = true }
                            .testTag("username_corner_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Nombre de usuario",
                                tint = ChampionshipGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = settings.playerName,
                                style = MaterialTheme.typography.labelMedium,
                                color = ChalkWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 125.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar nombre",
                                tint = MutedSteel,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = ArenaCard.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.65f)),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier
                                .clickable { activeTab = 2 }
                                .testTag("coins_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MonetizationOn,
                                    contentDescription = "Monedas",
                                    tint = ChampionshipGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = economy.coins.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ChampionshipGold
                                )
                            }
                        }

                        IconButton(
                            onClick = { showGuide = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ArenaCard.copy(alpha = 0.85f))
                                .testTag("guide_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = stringResource(R.string.btn_controls),
                                tint = ChalkWhite
                            )
                        }
                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ArenaCard.copy(alpha = 0.85f))
                                .testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.btn_settings),
                                tint = ChalkWhite
                            )
                        }
                    }
                }
            }

            // Segmented Main Menu Navigation (Combate / 1v1 Local / Tienda)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                MenuTabButton(
                    label = "COMBATE",
                    icon = Icons.Default.SportsMma,
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tab_combate")
                )
                MenuTabButton(
                    label = "VERSUS",
                    icon = Icons.Default.Groups,
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tab_versus")
                )
                MenuTabButton(
                    label = "TIENDA",
                    icon = Icons.Default.ShoppingBag,
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tab_tienda")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (activeTab) {
                0 -> FightHubTab(
                    sessionRecord = sessionRecord,
                    economy = economy,
                    selectedFighter = selectedFighter,
                    onSelectFighter = { selectedFighterId = it },
                    onStartFight = onStartFight,
                    onOpenShop = { activeTab = 2 }
                )
                1 -> MultiplayerHubTab(
                    currentUser = currentUser,
                    player1Name = settings.playerName,
                    equippedGloveId = economy.equippedGloveId,
                    onAuthSuccess = onAuthSuccess,
                    onStartVersus = onStartVersus,
                    onEnterOnlineMatch = onEnterOnlineMatch,
                    onHostWifiMatch = onHostWifiMatch,
                    onJoinWifiMatch = onJoinWifiMatch
                )
                else -> GloveShopTab(
                    economy = economy,
                    onBuyOrEquipGlove = onBuyOrEquipGlove
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onToggleSound = onToggleSound,
            onToggleHaptics = onToggleHaptics,
            onDismiss = { showSettings = false }
        )
    }

    if (showGuide) {
        RingGuideDialog(onDismiss = { showGuide = false })
    }

    if (showEditName) {
        UsernameDialog(
            initialName = settings.playerName,
            onSave = { newName ->
                onSavePlayerName(newName)
                showEditName = false
            },
            onDismiss = { showEditName = false }
        )
    }
}

@Composable
private fun MenuTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) ChampionshipGold else ArenaCard.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, if (selected) ChampionshipGold else ArenaBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ArenaObsidian else ChalkWhite,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) ArenaObsidian else ChalkWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FightHubTab(
    sessionRecord: SessionRecord,
    economy: PlayerEconomy,
    selectedFighter: FighterProfile,
    onSelectFighter: (String) -> Unit,
    onStartFight: (String) -> Unit,
    onOpenShop: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Surface(
                color = ArenaCard.copy(alpha = 0.92f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ArenaBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RecordStatCell(
                        label = stringResource(R.string.hub_wins_label),
                        value = sessionRecord.wins.toString(),
                        accent = ChampionshipGold
                    )
                    RecordStatCell(
                        label = stringResource(R.string.hub_kos_label),
                        value = sessionRecord.kos.toString(),
                        accent = CrimsonCorner
                    )
                    RecordStatCell(
                        label = stringResource(R.string.hub_streak_label),
                        value = sessionRecord.currentStreak.toString(),
                        accent = StaminaEmerald
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ArenaCardElevated)
                            .clickable(onClick = onOpenShop)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        GlovePreviewBadge(glove = economy.equippedGlove, size = 28.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("EQUIPADOS", style = MaterialTheme.typography.labelSmall, color = MutedSteel)
                            Text(
                                text = economy.equippedGlove.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = economy.equippedGlove.glowColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onStartFight(selectedFighter.id) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChampionshipGold,
                    contentColor = ArenaObsidian
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("quick_fight_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SportsMma,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${stringResource(R.string.hub_quick_fight)} VS ${selectedFighter.nickname.uppercase()}",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.hub_select_rival),
                style = MaterialTheme.typography.headlineMedium,
                color = ChalkWhite
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(FighterRoster.fighters, key = { it.id }) { fighter ->
                val isSelected = fighter.id == selectedFighter.id
                val isDefeated = sessionRecord.defeatedIds.contains(fighter.id)
                FighterCard(
                    fighter = fighter,
                    isSelected = isSelected,
                    isDefeated = isDefeated,
                    onSelect = { onSelectFighter(fighter.id) },
                    onFight = { onStartFight(fighter.id) }
                )
            }
        }
    }
}

@Composable
private fun GloveShopTab(
    economy: PlayerEconomy,
    onBuyOrEquipGlove: (GloveItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Surface(
                color = ArenaCard.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TIENDA DEL GIMNASIO",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ChampionshipGold
                        )
                        Text(
                            text = "Gana monedas peleando para desbloquear guantes con ventajas reales en el ring.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                }
            }
        }

        items(GloveShopCatalog.gloves, key = { it.id }) { glove ->
            val owned = economy.ownedGloveIds.contains(glove.id)
            val equipped = economy.equippedGloveId == glove.id
            val canAfford = economy.coins >= glove.priceCoins

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (equipped) ArenaCardElevated else ArenaCard
                ),
                border = BorderStroke(
                    width = if (equipped) 2.dp else 1.dp,
                    color = if (equipped) glove.glowColor else ArenaBorder
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("glove_card_${glove.id}")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GlovePreviewBadge(glove = glove, size = 64.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = glove.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = ChalkWhite
                                )
                                if (equipped) {
                                    Surface(
                                        color = StaminaEmerald.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "EQUIPADO",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StaminaEmerald,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = glove.subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = glove.glowColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = glove.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ChalkWhite.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        equipped -> {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                border = BorderStroke(1.dp, StaminaEmerald),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StaminaEmerald)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("EN USO ACTUALMENTE", color = StaminaEmerald, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        owned -> {
                            Button(
                                onClick = { onBuyOrEquipGlove(glove) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ArenaCardElevated,
                                    contentColor = ChalkWhite
                                ),
                                border = BorderStroke(1.5.dp, glove.glowColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("equip_glove_${glove.id}")
                            ) {
                                Text("EQUIPAR GUANTES", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        else -> {
                            Button(
                                onClick = { onBuyOrEquipGlove(glove) },
                                enabled = canAfford,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ChampionshipGold,
                                    contentColor = ArenaObsidian,
                                    disabledContainerColor = ArenaCardElevated,
                                    disabledContentColor = MutedSteel
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("buy_glove_${glove.id}")
                            ) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (canAfford) "COMPRAR POR ${glove.priceCoins}" else "FALTAN ${glove.priceCoins - economy.coins} MONEDAS",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordStatCell(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedSteel
        )
    }
}

@Composable
private fun FighterCard(
    fighter: FighterProfile,
    isSelected: Boolean,
    isDefeated: Boolean,
    onSelect: () -> Unit,
    onFight: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ArenaCardElevated else ArenaCard
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) fighter.accentColor else ArenaBorder
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
            .testTag("fighter_card_${fighter.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FighterAvatarBadge(fighter = fighter, size = 60.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = fighter.accentColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = fighter.difficultyLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = fighter.accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = ChampionshipGold.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "+${fighter.rewardCoins} 💰",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampionshipGold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (isDefeated) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Derrotado",
                                tint = StaminaEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${fighter.name} \"${fighter.nickname}\"",
                        style = MaterialTheme.typography.titleLarge,
                        color = ChalkWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = fighter.weightClass,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = fighter.styleDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkWhite.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatBar(
                    label = stringResource(R.string.stat_speed),
                    value = fighter.speedStat,
                    color = ChampionshipGold,
                    modifier = Modifier.weight(1f)
                )
                StatBar(
                    label = stringResource(R.string.stat_power),
                    value = fighter.powerStat,
                    color = CrimsonCorner,
                    modifier = Modifier.weight(1f)
                )
                StatBar(
                    label = stringResource(R.string.stat_guard),
                    value = fighter.guardStat,
                    color = StaminaEmerald,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = isSelected, enter = fadeIn()) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onFight,
                        border = BorderStroke(1.5.dp, fighter.accentColor),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ChalkWhite,
                            containerColor = fighter.accentColor.copy(alpha = 0.14f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("fight_button_${fighter.id}")
                    ) {
                        Icon(
                            imageVector = if (fighter.id == "diego") Icons.Default.EmojiEvents else Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = fighter.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_fight),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBar(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MutedSteel)
            Text(text = "$value/10", style = MaterialTheme.typography.labelSmall, color = ChalkWhite)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (value / 10f).coerceIn(0f, 1f) },
            color = color,
            trackColor = ArenaSlate,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun SettingsDialog(
    settings: UserSettings,
    onToggleSound: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ArenaCard,
        title = {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = ChampionshipGold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_sound),
                            style = MaterialTheme.typography.titleMedium,
                            color = ChalkWhite
                        )
                        Text(
                            text = stringResource(R.string.settings_sound_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                    Switch(
                        checked = settings.soundEnabled,
                        onCheckedChange = onToggleSound,
                        modifier = Modifier.testTag("sound_switch")
                    )
                }

                HorizontalDivider(color = ArenaBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_haptics),
                            style = MaterialTheme.typography.titleMedium,
                            color = ChalkWhite
                        )
                        Text(
                            text = stringResource(R.string.settings_haptics_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = onToggleHaptics,
                        modifier = Modifier.testTag("haptics_switch")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = ChampionshipGold, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun RingGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ArenaCard,
        title = {
            Text(
                text = stringResource(R.string.guide_title),
                style = MaterialTheme.typography.headlineMedium,
                color = ChampionshipGold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GuideRow("Movimiento en el Ring", "Usa ◄ MOVER / MOVER ► para desplazarte lateralmente y ▲ ENTRAR / ▼ SALIR para acercarte (+daño) o alejarte (+energía).")
                GuideRow("10 Rounds Progresivos", "Cada asalto aumenta la velocidad, agresividad y combinaciones del rival. ¡Administra tu resistencia!")
                GuideRow("Esquiva y Parry", "Observa el brillo en el guante rival. Esquiva a tiempo o sube la Guardia justo al impacto para abrir un CONTRAATAQUE (2x daño).")
                GuideRow("Tienda de Guantes", "Gana monedas tras cada pelea y compra guantes especiales con más potencia, velocidad o defensa.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = ChampionshipGold, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun UsernameDialog(
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(initialName) { mutableStateOf(if (initialName == "Retador") "" else initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ArenaCard,
        title = {
            Text(
                text = "NOMBRE DE USUARIO",
                style = MaterialTheme.typography.headlineMedium,
                color = ChampionshipGold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Así te anunciará el presentador al subir al ring.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteel
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 18) draft = it },
                    singleLine = true,
                    placeholder = { Text("Ej. El Tornado", color = MutedSteel) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ChampionshipGold,
                        unfocusedBorderColor = ArenaBorder,
                        focusedTextColor = ChalkWhite,
                        unfocusedTextColor = ChalkWhite,
                        cursorColor = ChampionshipGold
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(draft) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChampionshipGold,
                    contentColor = ArenaObsidian
                ),
                modifier = Modifier.testTag("save_username_button")
            ) {
                Text("GUARDAR", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MutedSteel)
            }
        }
    )
}

@Composable
private fun MultiplayerHubTab(
    currentUser: FirebaseUser?,
    player1Name: String,
    equippedGloveId: String,
    onAuthSuccess: () -> Unit,
    onStartVersus: (String, Int) -> Unit,
    onEnterOnlineMatch: (String) -> Unit,
    onHostWifiMatch: (Int) -> Unit,
    onJoinWifiMatch: (String, Int) -> Unit
) {
    var versusMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Wi-Fi 1v1, 1 = 1v1 Local, 2 = Online
    var player2Name by rememberSaveable { mutableStateOf("Jugador 2") }
    var selectedRounds by rememberSaveable { mutableIntStateOf(3) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            listOf(
                Triple(0, "WI-FI 1v1", "mode_wifi_button"),
                Triple(1, "1v1 LOCAL", "mode_local_button"),
                Triple(2, "ONLINE", "mode_online_button")
            ).forEach { (mode, label, tag) ->
                val selected = versusMode == mode
                OutlinedButton(
                    onClick = { versusMode = mode },
                    border = BorderStroke(1.5.dp, if (selected) ChampionshipGold else ArenaBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) ChampionshipGold.copy(alpha = 0.18f) else ArenaCard,
                        contentColor = if (selected) ChampionshipGold else ChalkWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag(tag)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (versusMode) {
            0 -> {
                WifiLobbySection(
                    playerName = player1Name,
                    onHostMatch = onHostWifiMatch,
                    onJoinMatch = onJoinWifiMatch
                )
                return
            }
            2 -> {
                OnlineLobbySection(
                    currentUser = currentUser,
                    playerName = player1Name,
                    equippedGloveId = equippedGloveId,
                    onAuthSuccess = onAuthSuccess,
                    onEnterOnlineMatch = onEnterOnlineMatch
                )
                return
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ArenaCard),
                border = BorderStroke(1.5.dp, ChampionshipGold.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = ChampionshipGold,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "MULTIJUGADOR 1v1 LOCAL",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ChampionshipGold
                        )
                        Text(
                            text = "Dos jugadores frente a frente en la misma pantalla con controles simultáneos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                }

                HorizontalDivider(color = ArenaBorder)

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("ESQUINA ROJA (ABAJO)", style = MaterialTheme.typography.labelSmall, color = CrimsonCorner)
                        Text(player1Name, style = MaterialTheme.typography.titleMedium, color = ChalkWhite)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ESQUINA AZUL (ARRIBA)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF60A5FA))
                        Text(player2Name.ifBlank { "Jugador 2" }, style = MaterialTheme.typography.titleMedium, color = ChalkWhite)
                    }
                }

                OutlinedTextField(
                    value = player2Name,
                    onValueChange = { if (it.length <= 16) player2Name = it },
                    label = { Text("Nombre del Jugador 2", color = MutedSteel) },
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
                        .testTag("p2_name_input")
                )

                Text(
                    text = "DURACIÓN DEL DUELO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSteel
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
                        ) {
                            Text("$rounds ROUNDS", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Button(
                    onClick = { onStartVersus(player2Name.ifBlank { "Jugador 2" }, selectedRounds) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampionshipGold,
                        contentColor = ArenaObsidian
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_versus_button")
                ) {
                    Icon(Icons.Default.SportsMma, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("INICIAR DUELO 1v1", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        }
    }
}

@Composable
private fun GuideRow(title: String, body: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = ChampionshipGold)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = ChalkWhite.copy(alpha = 0.88f))
    }
}
