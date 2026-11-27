package com.example.ui.fight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SportsMma
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.DodgeDirection
import com.example.model.FightPhase
import com.example.model.FightUiState
import com.example.model.HandSide
import com.example.model.MatchOutcome
import com.example.model.PunchType
import com.example.ui.components.FighterAvatarBadge
import com.example.ui.components.GlovePreviewBadge
import com.example.ui.hub.RingGuideDialog
import com.example.ui.theme.ArenaBorder
import com.example.ui.theme.ArenaCard
import com.example.ui.theme.ArenaCardElevated
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.ArenaSlate
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CobaltCorner
import com.example.ui.theme.CobaltGlow
import com.example.ui.theme.CrimsonCorner
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.StaminaEmerald
import com.example.ui.theme.WarningAmber
import kotlin.math.abs

@Composable
fun FightScreen(
    state: FightUiState,
    onThrowPunch: (PunchType) -> Unit,
    onSetGuard: (Boolean) -> Unit,
    onDodge: (DodgeDirection) -> Unit,
    onSetMoveInput: (Float, Float) -> Unit,
    onSkipPresentation: () -> Unit,
    onMashGetUp: () -> Unit,
    onTogglePause: () -> Unit,
    onRematch: () -> Unit,
    onExitToHub: () -> Unit
) {
    var showGuideInPause by remember { mutableStateOf(false) }

    BackHandler {
        if (state.phase == FightPhase.MATCH_OVER) {
            onExitToHub()
        } else {
            onTogglePause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArenaObsidian)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Broadcast Top HUD
            FightTopHud(
                state = state,
                onTogglePause = onTogglePause,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ArenaObsidian, ArenaObsidian.copy(alpha = 0.85f))
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // 2. Interactive Ring Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                var dragX by remember { mutableFloatStateOf(0f) }
                var dragY by remember { mutableFloatStateOf(0f) }
                var swipeHandled by remember { mutableStateOf(false) }

                RingCanvas(
                    state = state,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(state.phase) {
                            if (state.phase != FightPhase.FIGHTING) return@pointerInput
                            detectTapGestures { offset ->
                                if (offset.x < size.width * 0.5f) {
                                    onThrowPunch(PunchType.JAB)
                                } else {
                                    onThrowPunch(PunchType.CROSS)
                                }
                            }
                        }
                        .pointerInput(state.phase) {
                            if (state.phase != FightPhase.FIGHTING) return@pointerInput
                            detectDragGestures(
                                onDragStart = {
                                    dragX = 0f
                                    dragY = 0f
                                    swipeHandled = false
                                },
                                onDrag = { _, dragAmount ->
                                    if (swipeHandled) return@detectDragGestures
                                    dragX += dragAmount.x
                                    dragY += dragAmount.y
                                    val threshold = 48f
                                    if (abs(dragX) > threshold && abs(dragX) > abs(dragY)) {
                                        swipeHandled = true
                                        onDodge(if (dragX < 0) DodgeDirection.LEFT else DodgeDirection.RIGHT)
                                    } else if (dragY < -threshold && abs(dragY) > abs(dragX)) {
                                        swipeHandled = true
                                        onThrowPunch(PunchType.UPPERCUT)
                                    }
                                }
                            )
                        }
                )

                // Range & Crowd Status Strip at Top of Ring
                if (state.phase == FightPhase.FIGHTING) {
                    val distLabel = when {
                        state.relativeDistance < 0.34f -> "DISTANCIA: CORTA (+DAÑO)"
                        state.relativeDistance > 0.70f -> "DISTANCIA: LEJOS (+ENERGÍA)"
                        else -> "DISTANCIA: MEDIA"
                    }
                    val distColor = when {
                        state.relativeDistance < 0.34f -> CrimsonCorner
                        state.relativeDistance > 0.70f -> StaminaEmerald
                        else -> MutedSteel
                    }
                    Surface(
                        color = ArenaObsidian.copy(alpha = 0.70f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, ArenaBorder),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 8.dp)
                    ) {
                        Text(
                            text = distLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = distColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Crowd Cheer Banner
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.crowdBannerText.isNotEmpty() && state.phase != FightPhase.MATCH_OVER,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 10.dp, top = 8.dp)
                ) {
                    Surface(
                        color = CrimsonCorner.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = state.crowdBannerText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ChalkWhite,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Counter Window Status Banner inside Ring
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.counterWindowMs > 0L && state.phase == FightPhase.FIGHTING,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 38.dp)
                ) {
                    Surface(
                        color = ChampionshipGold,
                        shape = RoundedCornerShape(999.dp),
                        shadowElevation = 6.dp
                    ) {
                        Text(
                            text = "¡CONTRAATAQUE LISTO! (2x DAÑO)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ArenaObsidian,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 3. Ergonomic Arcade Fight Pad (with Footwork + Defense + Punches)
            ArcadeFightPad(
                state = state,
                onThrowPunch = onThrowPunch,
                onSetGuard = onSetGuard,
                onDodge = onDodge,
                onSetMoveInput = onSetMoveInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ArenaSlate)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }

        // 4. Phase Overlays
        if (state.phase == FightPhase.PRESENTATION) {
            PresentationOverlay(
                state = state,
                onSkip = onSkipPresentation
            )
        }

        if (state.phase == FightPhase.INTRO && state.introText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ArenaObsidian.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ArenaCard.copy(alpha = 0.95f),
                    border = BorderStroke(2.dp, ChampionshipGold),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 18.dp)
                    ) {
                        if (state.introText != "¡BOX!") {
                            Text(
                                text = state.roundDifficultyLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = CrimsonCorner
                            )
                        }
                        Text(
                            text = state.introText,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 62.sp),
                            color = ChampionshipGold
                        )
                    }
                }
            }
        }

        if (state.phase == FightPhase.ROUND_OVER) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ArenaObsidian.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ArenaCard,
                    border = BorderStroke(2.dp, ChampionshipGold),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 22.dp)
                    ) {
                        Text(
                            text = "FIN DEL ROUND ${state.round}",
                            style = MaterialTheme.typography.displayMedium,
                            color = ChampionshipGold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.roundSummaryText,
                            style = MaterialTheme.typography.titleMedium,
                            color = ChalkWhite,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (state.phase == FightPhase.KNOCKDOWN_COUNT) {
            KnockdownOverlay(
                state = state,
                onMashGetUp = onMashGetUp
            )
        }

        if (state.phase == FightPhase.PAUSED) {
            PauseOverlay(
                onResume = onTogglePause,
                onShowGuide = { showGuideInPause = true },
                onExit = onExitToHub
            )
        }

        if (state.phase == FightPhase.MATCH_OVER && state.outcome != null) {
            MatchResultOverlay(
                state = state,
                onRematch = onRematch,
                onExit = onExitToHub
            )
        }
    }

    if (showGuideInPause) {
        RingGuideDialog(onDismiss = { showGuideInPause = false })
    }
}

@Composable
private fun PresentationOverlay(
    state: FightUiState,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ArenaObsidian.copy(alpha = 0.35f),
                        ArenaObsidian.copy(alpha = 0.72f)
                    )
                )
            )
    ) {
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 60.dp, end = 14.dp)
                .testTag("skip_presentation_button")
        ) {
            Text("SALTAR INTRO", color = ChampionshipGold, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.SkipNext, contentDescription = "Saltar presentación", tint = ChampionshipGold)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = ArenaCard.copy(alpha = 0.94f)),
            border = BorderStroke(2.dp, ChampionshipGold),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .widthIn(max = 500.dp)
                .fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (state.presentationStep) {
                        1 -> FighterAvatarBadge(fighter = state.fighter, size = 64.dp)
                        2 -> GlovePreviewBadge(glove = state.equippedGlove, size = 64.dp)
                        else -> Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = ChampionshipGold,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.presentationTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = ChampionshipGold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.presentationSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChalkWhite
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (state.presentationStep + 1) / 3f },
                    color = ChampionshipGold,
                    trackColor = ArenaSlate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun FightTopHud(
    state: FightUiState,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.playerName.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = state.equippedGlove.glowColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (state.comboCount >= 2) {
                        Surface(
                            color = ChampionshipGold,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "x${state.comboCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ArenaObsidian,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { (state.playerHp / state.playerMaxHp).coerceIn(0f, 1f) },
                    color = if (state.playerHp < 30f) CrimsonCorner else StaminaEmerald,
                    trackColor = ArenaCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (state.playerStamina / state.playerMaxStamina).coerceIn(0f, 1f) },
                    color = if (state.playerStamina < 20f) WarningAmber else CobaltGlow,
                    trackColor = ArenaCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = ArenaCardElevated,
                    border = BorderStroke(1.dp, ArenaBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "R${state.round}/${state.maxRounds}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChampionshipGold
                        )
                        val secs = (state.roundTimeMs / 1000L).toInt().coerceAtLeast(0)
                        val timeFormatted = String.format("%02d:%02d", secs / 60, secs % 60)
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.sp),
                            color = ChalkWhite
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = state.fighter.nickname.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = state.fighter.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { (state.enemyHp / state.enemyMaxHp).coerceIn(0f, 1f) },
                    color = state.fighter.accentColor,
                    trackColor = ArenaCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${state.playerRoundPoints} - ${state.enemyRoundPoints} PTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSteel
                )
            }

            FighterAvatarBadge(fighter = state.fighter, size = 40.dp, borderWidth = 1.5.dp)

            IconButton(
                onClick = onTogglePause,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ArenaCard)
                    .testTag("pause_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = stringResource(R.string.btn_pause),
                    tint = ChalkWhite
                )
            }
        }
    }
}

@Composable
private fun ArcadeFightPad(
    state: FightUiState,
    onThrowPunch: (PunchType) -> Unit,
    onSetGuard: (Boolean) -> Unit,
    onDodge: (DodgeDirection) -> Unit,
    onSetMoveInput: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier
    ) {
        // Row 1: Continuous 2D Ring Footwork (Move Left, Step In, Step Back, Move Right)
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            HoldMoveButton(
                label = "◄ MOVER",
                accent = MutedSteel,
                onHeldChange = { held -> onSetMoveInput(if (held) -1f else 0f, 0f) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("move_left_button")
            )
            HoldMoveButton(
                label = "▲ ENTRAR",
                accent = CrimsonCorner,
                onHeldChange = { held -> onSetMoveInput(0f, if (held) 1f else 0f) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("move_forward_button")
            )
            HoldMoveButton(
                label = "▼ SALIR",
                accent = StaminaEmerald,
                onHeldChange = { held -> onSetMoveInput(0f, if (held) -1f else 0f) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("move_back_button")
            )
            HoldMoveButton(
                label = "MOVER ►",
                accent = MutedSteel,
                onHeldChange = { held -> onSetMoveInput(if (held) 1f else 0f, 0f) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("move_right_button")
            )
        }

        // Row 2: Defense & Quick Dodge
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            InstantActionButton(
                label = stringResource(R.string.ctrl_dodge_left),
                subLabel = "←",
                accent = StaminaEmerald,
                isActive = state.dodgeDirection == DodgeDirection.LEFT,
                onPress = { onDodge(DodgeDirection.LEFT) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("dodge_left_button")
            )

            GuardHoldButton(
                isGuarding = state.isGuarding,
                onSetGuard = onSetGuard,
                modifier = Modifier
                    .weight(1.35f)
                    .height(50.dp)
                    .testTag("guard_button")
            )

            InstantActionButton(
                label = stringResource(R.string.ctrl_dodge_right),
                subLabel = "→",
                accent = StaminaEmerald,
                isActive = state.dodgeDirection == DodgeDirection.RIGHT,
                onPress = { onDodge(DodgeDirection.RIGHT) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("dodge_right_button")
            )
        }

        // Row 3: Punches
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            InstantActionButton(
                label = stringResource(R.string.ctrl_jab),
                subLabel = "RÁPIDO",
                accent = ChalkWhite,
                isActive = state.activePunch == PunchType.JAB,
                onPress = { onThrowPunch(PunchType.JAB) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("jab_button")
            )
            InstantActionButton(
                label = stringResource(R.string.ctrl_hook),
                subLabel = "ROMPE",
                accent = WarningAmber,
                isActive = state.activePunch == PunchType.HOOK,
                onPress = { onThrowPunch(PunchType.HOOK) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("hook_button")
            )
            InstantActionButton(
                label = stringResource(R.string.ctrl_uppercut),
                subLabel = if (state.counterWindowMs > 0L) "2x ¡YA!" else "POTENTE",
                accent = ChampionshipGold,
                isActive = state.activePunch == PunchType.UPPERCUT || state.counterWindowMs > 0L,
                onPress = { onThrowPunch(PunchType.UPPERCUT) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("uppercut_button")
            )
            InstantActionButton(
                label = stringResource(R.string.ctrl_cross),
                subLabel = "RECTO",
                accent = CrimsonCorner,
                isActive = state.activePunch == PunchType.CROSS,
                onPress = { onThrowPunch(PunchType.CROSS) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("cross_button")
            )
        }
    }
}

@Composable
private fun HoldMoveButton(
    label: String,
    accent: Color,
    onHeldChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) accent.copy(alpha = 0.28f) else ArenaCard)
            .border(
                width = if (pressed) 1.5.dp else 1.dp,
                color = if (pressed) accent else ArenaBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onHeldChange(true)
                    waitForUpOrCancellation()
                    pressed = false
                    onHeldChange(false)
                }
            }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (pressed) ChalkWhite else accent,
            maxLines = 1
        )
    }
}

@Composable
private fun InstantActionButton(
    label: String,
    subLabel: String,
    accent: Color,
    isActive: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val bgColor = when {
        pressed -> accent.copy(alpha = 0.34f)
        isActive -> accent.copy(alpha = 0.24f)
        else -> ArenaCard
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isActive || pressed) 2.dp else 1.dp,
                color = if (isActive || pressed) accent else ArenaBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onPress()
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                color = ChalkWhite,
                maxLines = 1
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GuardHoldButton(
    isGuarding: Boolean,
    onSetGuard: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isGuarding) CobaltCorner.copy(alpha = 0.42f) else ArenaCardElevated
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isGuarding) 2.dp else 1.dp,
                color = if (isGuarding) CobaltGlow else ArenaBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onSetGuard(true)
                    waitForUpOrCancellation()
                    onSetGuard(false)
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = stringResource(R.string.ctrl_guard),
                tint = if (isGuarding) ChalkWhite else CobaltGlow,
                modifier = Modifier.size(18.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.ctrl_guard),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                    color = ChalkWhite
                )
                Text(
                    text = "MANTENER",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = CobaltGlow
                )
            }
        }
    }
}

@Composable
private fun KnockdownOverlay(
    state: FightUiState,
    onMashGetUp: () -> Unit
) {
    val playerIsDown = state.knockedDownWho == HandSide.LEFT
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArenaObsidian.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ArenaCard),
            border = BorderStroke(2.dp, if (playerIsDown) CrimsonCorner else ChampionshipGold),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(28.dp)
            ) {
                Text(
                    text = if (playerIsDown) "¡CAÍSTE A LA LONA!" else "¡RIVAL EN LA LONA!",
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (playerIsDown) CrimsonCorner else ChampionshipGold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.knockdownCount.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp),
                    color = ChalkWhite
                )

                if (playerIsDown) {
                    if (state.stats.knockdownsSuffered >= 3) {
                        Text(
                            text = "3 CAÍDAS — NOCAUT TÉCNICO",
                            style = MaterialTheme.typography.titleMedium,
                            color = CrimsonCorner
                        )
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { state.playerGetUpProgress },
                            color = StaminaEmerald,
                            trackColor = ArenaSlate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onMashGetUp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StaminaEmerald,
                                contentColor = ArenaObsidian
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("get_up_button")
                        ) {
                            Text(
                                text = "¡TOCA RÁPIDO — LEVÁNTATE!",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }
                } else {
                    Text(
                        text = "CONTEO DEL ÁRBITRO...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MutedSteel
                    )
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(
    onResume: () -> Unit,
    onShowGuide: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArenaObsidian.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ArenaCard),
            border = BorderStroke(1.5.dp, ArenaBorder),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "COMBATE EN PAUSA",
                    style = MaterialTheme.typography.displayMedium,
                    color = ChampionshipGold
                )
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampionshipGold,
                        contentColor = ArenaObsidian
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("resume_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_resume), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onShowGuide,
                    border = BorderStroke(1.dp, ArenaBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(stringResource(R.string.btn_controls), color = ChalkWhite, style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onExit,
                    border = BorderStroke(1.dp, CrimsonCorner.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("exit_ring_button")
                ) {
                    Text(stringResource(R.string.btn_exit_ring), color = CrimsonCorner, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun MatchResultOverlay(
    state: FightUiState,
    onRematch: () -> Unit,
    onExit: () -> Unit
) {
    val outcome = state.outcome ?: return
    val isVictory = outcome == MatchOutcome.KO_WIN || outcome == MatchOutcome.DECISION_WIN
    val title = when (outcome) {
        MatchOutcome.KO_WIN -> "¡VICTORIA POR KO!"
        MatchOutcome.DECISION_WIN -> "¡DECISIÓN UNÁNIME!"
        MatchOutcome.KO_LOSS -> "DERROTA POR KO"
        MatchOutcome.DECISION_LOSS -> "DERROTA POR PUNTOS"
    }
    val accent = if (isVictory) ChampionshipGold else CrimsonCorner

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArenaObsidian.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ArenaCard),
            border = BorderStroke(2.dp, accent),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = if (isVictory) Icons.Default.EmojiEvents else Icons.Default.SportsMma,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayMedium,
                    color = accent,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "vs ${state.fighter.name} \"${state.fighter.nickname}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MutedSteel
                )

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ChampionshipGold.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = "Monedas ganadas",
                            tint = ChampionshipGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "+${state.coinsEarned} MONEDAS",
                            style = MaterialTheme.typography.labelLarge,
                            color = ChampionshipGold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = ArenaBorder)
                Spacer(modifier = Modifier.height(10.dp))

                ScoreStatRow("Golpes Conectados", "${state.stats.punchesLanded} / ${state.stats.punchesThrown}")
                ScoreStatRow("Precisión", "${state.stats.accuracyPercent}%")
                ScoreStatRow("Contraataques", "${state.stats.countersLanded}")
                ScoreStatRow("Esquivas y Bloqueos", "${state.stats.dodgesMade + state.stats.blocksMade}")
                ScoreStatRow("Caídas Provocadas", "${state.stats.knockdownsScored}")
                ScoreStatRow("Tarjetas (${state.round}/${state.maxRounds})", "${state.playerRoundPoints} - ${state.enemyRoundPoints}")

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onRematch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampionshipGold,
                        contentColor = ArenaObsidian
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("rematch_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_rematch), style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onExit,
                    border = BorderStroke(1.dp, ArenaBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("back_to_hub_button")
                ) {
                    Text(
                        text = stringResource(R.string.btn_exit_ring),
                        color = ChalkWhite,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = ChalkWhite)
    }
}
