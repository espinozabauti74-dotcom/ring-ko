package com.example.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.BoxingSoundEngine
import com.example.data.SettingsRepository
import com.example.data.UserSettings
import com.example.model.BoxerAppearance
import com.example.model.CalloutTone
import com.example.model.DodgeDirection
import com.example.model.EnemyAttackType
import com.example.model.EnemyPhase
import com.example.model.FightPhase
import com.example.model.FightUiState
import com.example.model.FighterRoster
import com.example.model.FloatingCallout
import com.example.model.GloveItem
import com.example.model.HandSide
import com.example.model.ImpactSpark
import com.example.model.MatchOutcome
import com.example.model.PlayerAction
import com.example.model.PlayerEconomy
import com.example.model.PunchType
import com.example.model.SessionRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

class FightViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application.applicationContext)
    private val soundEngine = BoxingSoundEngine(application.applicationContext)

    val settings: StateFlow<UserSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val economy: StateFlow<PlayerEconomy> = settingsRepository.economyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerEconomy())

    val appearance: StateFlow<BoxerAppearance> = settingsRepository.appearanceFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BoxerAppearance())

    private val _sessionRecord = MutableStateFlow(SessionRecord())
    val sessionRecord: StateFlow<SessionRecord> = _sessionRecord.asStateFlow()

    private val _uiState = MutableStateFlow(FightUiState())
    val uiState: StateFlow<FightUiState> = _uiState.asStateFlow()

    private var loopJob: Job? = null
    private var nextEffectId = 1L

    // Continuous movement input (-1f..1f)
    @Volatile
    private var moveInputX: Float = 0f
    @Volatile
    private var moveInputZ: Float = 0f

    // Internal simulation timers (ms)
    private var presentationTimerMs = 0L
    private var introTimerMs = 0L
    private var playerActionTimerMs = 0L
    private var playerPunchImpactDone = false
    private var guardStartedElapsedMs = -9999L
    private var totalFightElapsedMs = 0L
    private var playerInvulnMs = 0L

    private var enemyStateTimerMs = 0L
    private var enemyAiCooldownMs = 1100L
    private var enemyStrikeImpactDone = false
    private var enemyRecoveryTargetCount = 7
    private var enemyComboChainRemaining = 0
    private var enemyMoveDir = 1f
    private var enemyMoveTimerMs = 1200L

    private var knockdownTickMs = 0L
    private var roundOverTimerMs = 0L
    private var playerRoundDamage = 0f
    private var enemyRoundDamage = 0f
    private var outcomeRecorded = false
    private var crowdBannerTimerMs = 0L
    private var lastCrowdCheerElapsedMs = -5000L

    init {
        viewModelScope.launch {
            settings.collect { s ->
                soundEngine.soundEnabled = s.soundEnabled
                soundEngine.hapticsEnabled = s.hapticsEnabled
            }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticsEnabled(enabled) }
    }

    fun setPlayerName(name: String) {
        viewModelScope.launch { settingsRepository.setPlayerName(name) }
    }

    fun buyOrEquipGlove(glove: GloveItem) {
        viewModelScope.launch {
            settingsRepository.buyOrEquipGlove(glove)
        }
    }

    fun updateAppearance(appearance: BoxerAppearance) {
        viewModelScope.launch {
            settingsRepository.saveAppearance(appearance)
        }
    }

    fun startMatch(fighterId: String) {
        loopJob?.cancel()
        val fighter = FighterRoster.byId(fighterId)
        val equippedGlove = economy.value.equippedGlove
        val activePlayerName = settings.value.playerName.trim().ifBlank { "Retador" }
        outcomeRecorded = false
        totalFightElapsedMs = 0L
        presentationTimerMs = 4800L
        introTimerMs = 1600L
        playerActionTimerMs = 0L
        playerPunchImpactDone = false
        guardStartedElapsedMs = -9999L
        playerInvulnMs = 0L
        enemyStateTimerMs = 0L
        enemyAiCooldownMs = 1100L
        enemyStrikeImpactDone = false
        enemyComboChainRemaining = 0
        moveInputX = 0f
        moveInputZ = 0f
        knockdownTickMs = 0L
        roundOverTimerMs = 0L
        playerRoundDamage = 0f
        enemyRoundDamage = 0f
        crowdBannerTimerMs = 0L

        _uiState.value = FightUiState(
            playerName = activePlayerName,
            fighter = fighter,
            equippedGlove = equippedGlove,
            phase = FightPhase.PRESENTATION,
            presentationStep = 0,
            presentationTitle = "¡NOCHE DE CAMPEONATO!",
            presentationSubtitle = "COMBATE ESTELAR A 10 ROUNDS • ¡LA HINCHADA LISTOS!",
            round = 1,
            maxRounds = 10,
            roundTimeMs = 45_000L,
            roundDifficultyLabel = roundDifficultyLabel(1),
            introText = "ROUND 1",
            playerHp = 100f,
            playerMaxHp = 100f,
            playerStamina = 100f,
            playerMaxStamina = 100f,
            playerPosX = 0f,
            playerPosZ = 0.45f,
            enemyPosX = 0f,
            enemyPosZ = 0.08f,
            enemyHp = fighter.maxHp,
            enemyMaxHp = fighter.maxHp,
            crowdExcitement = 0.55f,
            crowdBannerText = "¡ARENA LLENA!"
        )
        crowdBannerTimerMs = 1800L
        soundEngine.playPresentationFanfare()
        soundEngine.playCrowdCheer(0.75f)
        startLoop()
    }

    fun skipPresentation() {
        val state = _uiState.value
        if (state.phase != FightPhase.PRESENTATION) return
        beginRoundIntro(roundNum = 1)
    }

    fun stopMatch() {
        loopJob?.cancel()
        loopJob = null
        moveInputX = 0f
        moveInputZ = 0f
    }

    fun togglePause() {
        _uiState.update { state ->
            when (state.phase) {
                FightPhase.FIGHTING, FightPhase.INTRO, FightPhase.PRESENTATION -> state.copy(
                    phase = FightPhase.PAUSED,
                    previousPhaseBeforePause = state.phase,
                    isGuarding = false
                )
                FightPhase.PAUSED -> state.copy(phase = state.previousPhaseBeforePause)
                else -> state
            }
        }
    }

    fun setMoveInput(x: Float, z: Float) {
        moveInputX = x.coerceIn(-1f, 1f)
        moveInputZ = z.coerceIn(-1f, 1f)
    }

    fun setGuard(held: Boolean) {
        val state = _uiState.value
        if (state.phase != FightPhase.FIGHTING) return
        if (state.playerAction == PlayerAction.HIT_STUN || state.playerAction == PlayerAction.KNOCKED_DOWN) return

        if (held && !state.isGuarding) {
            guardStartedElapsedMs = totalFightElapsedMs
        }
        _uiState.update { curr ->
            val nextAction = when {
                curr.playerAction == PlayerAction.PUNCHING -> curr.playerAction
                held -> PlayerAction.GUARD
                curr.playerAction == PlayerAction.GUARD -> PlayerAction.IDLE
                else -> curr.playerAction
            }
            curr.copy(isGuarding = held, playerAction = nextAction)
        }
    }

    fun dodge(direction: DodgeDirection) {
        val state = _uiState.value
        if (state.phase != FightPhase.FIGHTING) return
        if (direction == DodgeDirection.NONE) return
        if (state.playerAction == PlayerAction.HIT_STUN ||
            state.playerAction == PlayerAction.KNOCKED_DOWN ||
            state.playerAction == PlayerAction.PUNCHING
        ) return
        if (state.playerStamina < 6f) return

        playerActionTimerMs = 340L
        val stepShift = if (direction == DodgeDirection.LEFT) -0.16f else 0.16f
        _uiState.update { curr ->
            curr.copy(
                playerAction = if (direction == DodgeDirection.LEFT) PlayerAction.DODGE_LEFT else PlayerAction.DODGE_RIGHT,
                dodgeDirection = direction,
                isGuarding = false,
                playerPosX = (curr.playerPosX + stepShift).coerceIn(-0.92f, 0.92f),
                playerStamina = (curr.playerStamina - 7f).coerceAtLeast(0f)
            )
        }
        soundEngine.playWhoosh()
    }

    fun throwPunch(type: PunchType) {
        val state = _uiState.value
        if (state.phase != FightPhase.FIGHTING) return
        if (state.playerAction == PlayerAction.HIT_STUN ||
            state.playerAction == PlayerAction.KNOCKED_DOWN ||
            state.playerAction == PlayerAction.PUNCHING
        ) return

        val glove = state.equippedGlove
        val effectiveCost = type.staminaCost * glove.staminaCostMultiplier
        if (state.playerStamina < 5f) {
            addCallout("SIN AIRE", CalloutTone.CRIMSON, 0.5f, 0.72f)
            return
        }

        val exhausted = state.playerStamina < effectiveCost
        val speedFactor = (1f / glove.speedMultiplier) * (if (exhausted) 1.30f else 1f)
        playerActionTimerMs = (type.durationMs * speedFactor).toLong().coerceAtLeast(140L)
        playerPunchImpactDone = false

        // Slight forward & lateral step-in when punching
        val stepInZ = (state.playerPosZ - 0.05f).coerceAtLeast(0.24f)
        val alignX = state.playerPosX + ((state.enemyPosX - state.playerPosX) * 0.14f)

        _uiState.update { curr ->
            curr.copy(
                playerAction = PlayerAction.PUNCHING,
                activePunch = type,
                playerPunchProgress = 0f,
                isGuarding = false,
                dodgeDirection = DodgeDirection.NONE,
                playerPosZ = stepInZ,
                playerPosX = alignX.coerceIn(-0.92f, 0.92f),
                playerStamina = (curr.playerStamina - effectiveCost).coerceAtLeast(0f),
                stats = curr.stats.copy(punchesThrown = curr.stats.punchesThrown + 1)
            )
        }
        soundEngine.playWhoosh()
    }

    fun mashGetUp() {
        val state = _uiState.value
        if (state.phase != FightPhase.KNOCKDOWN_COUNT || state.knockedDownWho != HandSide.LEFT) return
        if (state.stats.knockdownsSuffered >= 3) return

        val nextProg = (state.playerGetUpProgress + 0.17f).coerceAtMost(1f)
        if (nextProg >= 1f) {
            playerInvulnMs = 1300L
            playerActionTimerMs = 0L
            enemyAiCooldownMs = 1000L
            _uiState.update { curr ->
                curr.copy(
                    phase = FightPhase.FIGHTING,
                    playerHp = curr.playerMaxHp * 0.44f,
                    playerStamina = curr.playerMaxStamina * 0.78f,
                    playerAction = PlayerAction.IDLE,
                    knockedDownWho = null,
                    playerGetUpProgress = 0f,
                    crowdExcitement = (curr.crowdExcitement + 0.35f).coerceAtMost(1f)
                )
            }
            triggerCrowdBanner("¡SE LEVANTA EL RETADOR!")
            addCallout("¡DE PIE!", CalloutTone.EMERALD, 0.5f, 0.6f)
            soundEngine.playBell(doubleRing = false)
            soundEngine.playCrowdCheer(0.9f)
        } else {
            _uiState.update { it.copy(playerGetUpProgress = nextProg) }
        }
    }

    private fun startLoop() {
        loopJob = viewModelScope.launch {
            var lastNs = System.nanoTime()
            while (true) {
                delay(16L)
                val nowNs = System.nanoTime()
                val dtMs = ((nowNs - lastNs) / 1_000_000L).coerceIn(1L, 48L)
                lastNs = nowNs
                tick(dtMs)
            }
        }
    }

    private fun tick(dtMs: Long) {
        val current = _uiState.value
        if (current.phase == FightPhase.PAUSED || current.phase == FightPhase.MATCH_OVER) return

        val updatedCallouts = current.callouts.mapNotNull { c ->
            val nextAge = c.ageMs + dtMs
            if (nextAge >= c.durationMs) null else c.copy(ageMs = nextAge)
        }
        val updatedSparks = current.sparks.mapNotNull { s ->
            val nextAge = s.ageMs + dtMs
            if (nextAge >= s.durationMs) null else s.copy(ageMs = nextAge)
        }
        val nextEnemyFlash = (current.enemyHitFlash - dtMs / 180f).coerceAtLeast(0f)
        val nextPlayerFlash = (current.playerHitFlash - dtMs / 220f).coerceAtLeast(0f)
        val nextShake = (current.screenShake - dtMs / 210f).coerceAtLeast(0f)
        val nextCrowd = (current.crowdExcitement - dtMs / 6500f).coerceAtLeast(0.22f)
        if (crowdBannerTimerMs > 0L) {
            crowdBannerTimerMs = (crowdBannerTimerMs - dtMs).coerceAtLeast(0L)
        }

        _uiState.update {
            it.copy(
                callouts = updatedCallouts,
                sparks = updatedSparks,
                enemyHitFlash = nextEnemyFlash,
                playerHitFlash = nextPlayerFlash,
                screenShake = nextShake,
                crowdExcitement = nextCrowd,
                crowdBannerText = if (crowdBannerTimerMs > 0L) it.crowdBannerText else ""
            )
        }

        when (_uiState.value.phase) {
            FightPhase.PRESENTATION -> tickPresentation(dtMs)
            FightPhase.INTRO -> tickIntro(dtMs)
            FightPhase.FIGHTING -> tickFighting(dtMs)
            FightPhase.KNOCKDOWN_COUNT -> tickKnockdown(dtMs)
            FightPhase.ROUND_OVER -> tickRoundOver(dtMs)
            else -> Unit
        }
    }

    private fun tickPresentation(dtMs: Long) {
        presentationTimerMs -= dtMs
        val fighter = _uiState.value.fighter
        val step = when {
            presentationTimerMs > 3100L -> 0
            presentationTimerMs > 1400L -> 1
            else -> 2
        }
        if (step != _uiState.value.presentationStep) {
            when (step) {
                1 -> {
                    _uiState.update {
                        it.copy(
                            presentationStep = 1,
                            presentationTitle = "ESQUINA AZUL: ${fighter.name.uppercase()}",
                            presentationSubtitle = "\"${fighter.nickname.uppercase()}\" • ${fighter.introLine}",
                            crowdExcitement = 0.80f
                        )
                    }
                    soundEngine.playCrowdCheer(0.7f)
                }
                2 -> {
                    _uiState.update {
                        it.copy(
                            presentationStep = 2,
                            presentationTitle = "ESQUINA ROJA: ${it.playerName.uppercase()}",
                            presentationSubtitle = "GUANTES: ${it.equippedGlove.name.uppercase()} • ¡TODO LISTO!",
                            crowdExcitement = 0.92f
                        )
                    }
                    soundEngine.playPresentationFanfare()
                }
            }
        }
        if (presentationTimerMs <= 0L) {
            beginRoundIntro(roundNum = 1)
        }
    }

    private fun beginRoundIntro(roundNum: Int) {
        introTimerMs = 1550L
        _uiState.update { curr ->
            curr.copy(
                phase = FightPhase.INTRO,
                round = roundNum,
                roundDifficultyLabel = roundDifficultyLabel(roundNum),
                introText = "ROUND $roundNum",
                crowdExcitement = 0.72f
            )
        }
        soundEngine.playBell(doubleRing = false)
    }

    private fun tickIntro(dtMs: Long) {
        introTimerMs -= dtMs
        if (introTimerMs <= 520L && _uiState.value.introText != "¡BOX!") {
            _uiState.update { it.copy(introText = "¡BOX!") }
        }
        if (introTimerMs <= 0L) {
            _uiState.update { it.copy(phase = FightPhase.FIGHTING, introText = "") }
        }
    }

    private fun tickFighting(dtMs: Long) {
        totalFightElapsedMs += dtMs
        if (playerInvulnMs > 0L) playerInvulnMs = (playerInvulnMs - dtMs).coerceAtLeast(0L)

        val stateBefore = _uiState.value
        val dtSec = dtMs / 1000f

        // 1. Player footwork & positioning
        val canMove = stateBefore.playerAction != PlayerAction.HIT_STUN &&
            stateBefore.playerAction != PlayerAction.KNOCKED_DOWN &&
            stateBefore.playerAction != PlayerAction.PUNCHING
        val speedMoveScale = if (stateBefore.isGuarding) 0.55f else 1.0f
        val newPlayerX = if (canMove) {
            (stateBefore.playerPosX + moveInputX * 1.15f * speedMoveScale * dtSec).coerceIn(-0.92f, 0.92f)
        } else stateBefore.playerPosX
        val newPlayerZ = if (canMove) {
            (stateBefore.playerPosZ - moveInputZ * 0.85f * speedMoveScale * dtSec).coerceIn(0.24f, 0.92f)
        } else stateBefore.playerPosZ

        // 2. Round clock & distance-aware stamina regen
        val nextRoundTime = (stateBefore.roundTimeMs - dtMs).coerceAtLeast(0L)
        val dist = (newPlayerZ - stateBefore.enemyPosZ).coerceIn(0f, 1f)
        val baseRegen = when {
            stateBefore.isGuarding -> 9f
            dist > 0.70f -> 34f
            else -> 23f
        }
        val nextStamina = if (stateBefore.playerAction == PlayerAction.PUNCHING) {
            stateBefore.playerStamina
        } else {
            (stateBefore.playerStamina + baseRegen * dtSec).coerceAtMost(stateBefore.playerMaxStamina)
        }
        val nextCounterWindow = (stateBefore.counterWindowMs - dtMs).coerceAtLeast(0L)

        _uiState.update {
            it.copy(
                roundTimeMs = nextRoundTime,
                playerPosX = newPlayerX,
                playerPosZ = newPlayerZ,
                playerStamina = nextStamina,
                counterWindowMs = nextCounterWindow,
                stats = it.stats.copy(elapsedSeconds = (totalFightElapsedMs / 1000L).toInt())
            )
        }

        // 3. Player active action progression
        tickPlayerAction(dtMs)

        // 4. Enemy AI footwork & attacks
        if (_uiState.value.phase == FightPhase.FIGHTING) {
            tickEnemyFootwork(dtMs)
            tickEnemyAi(dtMs)
        }

        // 5. Round end check
        if (_uiState.value.phase == FightPhase.FIGHTING && _uiState.value.roundTimeMs <= 0L) {
            handleRoundEnd()
        }
    }

    private fun tickEnemyFootwork(dtMs: Long) {
        val state = _uiState.value
        if (state.enemyPhase == EnemyPhase.KNOCKED_DOWN) return

        enemyMoveTimerMs -= dtMs
        if (enemyMoveTimerMs <= 0L) {
            enemyMoveTimerMs = Random.nextLong(700L, 1600L)
            enemyMoveDir = if (Random.nextFloat() < 0.5f) -1f else 1f
        }

        val dtSec = dtMs / 1000f
        val roundAggro = 0.55f + (state.round - 1) * 0.06f
        val targetX = when (state.enemyPhase) {
            EnemyPhase.HIT_STUN, EnemyPhase.VULNERABLE -> state.enemyPosX
            EnemyPhase.TELEGRAPH, EnemyPhase.STRIKING -> state.playerPosX
            else -> (state.playerPosX + enemyMoveDir * 0.28f).coerceIn(-0.82f, 0.82f)
        }
        val diffX = targetX - state.enemyPosX
        val nextEnemyX = (state.enemyPosX + diffX.coerceIn(-1f, 1f) * 0.72f * roundAggro * dtSec)
            .coerceIn(-0.85f, 0.85f)

        // Enemy steps in slightly when winding up or aggressive, backs slightly if low HP
        val targetZ = when {
            state.enemyPhase == EnemyPhase.TELEGRAPH || state.enemyPhase == EnemyPhase.STRIKING ->
                (state.playerPosZ - 0.42f).coerceIn(0.0f, 0.28f)
            state.enemyHp < state.enemyMaxHp * 0.30f -> 0.02f
            else -> (state.playerPosZ - 0.50f).coerceIn(0.0f, 0.24f)
        }
        val diffZ = targetZ - state.enemyPosZ
        val nextEnemyZ = (state.enemyPosZ + diffZ * 1.4f * dtSec).coerceIn(0.0f, 0.26f)

        _uiState.update {
            it.copy(enemyPosX = nextEnemyX, enemyPosZ = nextEnemyZ)
        }
    }

    private fun tickPlayerAction(dtMs: Long) {
        val state = _uiState.value
        when (state.playerAction) {
            PlayerAction.PUNCHING -> {
                val punch = state.activePunch ?: return
                val totalDur = (punch.durationMs / state.equippedGlove.speedMultiplier).coerceAtLeast(140f)
                playerActionTimerMs -= dtMs
                val elapsed = (totalDur - playerActionTimerMs).coerceAtLeast(0f)
                val progress = (elapsed / totalDur).coerceIn(0f, 1f)

                if (!playerPunchImpactDone && progress >= 0.48f) {
                    playerPunchImpactDone = true
                    resolvePlayerPunchImpact(punch)
                }

                if (_uiState.value.phase != FightPhase.FIGHTING) return

                if (playerActionTimerMs <= 0L) {
                    _uiState.update { curr ->
                        curr.copy(
                            playerAction = if (curr.isGuarding) PlayerAction.GUARD else PlayerAction.IDLE,
                            activePunch = null,
                            playerPunchProgress = 0f
                        )
                    }
                } else {
                    _uiState.update { it.copy(playerPunchProgress = progress) }
                }
            }

            PlayerAction.DODGE_LEFT, PlayerAction.DODGE_RIGHT -> {
                playerActionTimerMs -= dtMs
                if (playerActionTimerMs <= 0L) {
                    _uiState.update { curr ->
                        curr.copy(
                            playerAction = if (curr.isGuarding) PlayerAction.GUARD else PlayerAction.IDLE,
                            dodgeDirection = DodgeDirection.NONE
                        )
                    }
                }
            }

            PlayerAction.HIT_STUN -> {
                playerActionTimerMs -= dtMs
                if (playerActionTimerMs <= 0L) {
                    _uiState.update { curr ->
                        curr.copy(
                            playerAction = if (curr.isGuarding) PlayerAction.GUARD else PlayerAction.IDLE
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    private fun resolvePlayerPunchImpact(punch: PunchType) {
        val state = _uiState.value
        val dist = state.relativeDistance
        val lateralOff = abs(state.lateralDelta)

        // Footwork range & lateral alignment check
        if (dist > punch.maxRange || lateralOff > 0.65f) {
            addCallout("¡SIN ALCANCE!", CalloutTone.WHITE, 0.5f, 0.44f)
            return
        }

        val isCounter = state.counterWindowMs > 0L || state.enemyPhase == EnemyPhase.VULNERABLE
        val comboMult = 1f + (state.comboCount.coerceAtMost(5) * 0.08f)
        val closePocketBonus = if (dist < 0.34f && (punch == PunchType.HOOK || punch == PunchType.UPPERCUT)) 1.20f else 1f
        val gloveDmgMult = state.equippedGlove.damageMultiplier

        // Check enemy guard
        if (state.enemyPhase == EnemyPhase.GUARDING && !isCounter && punch.breaksGuardFactor < 0.5f) {
            val chip = punch.damage * 0.26f * gloveDmgMult
            val nextEnemyHp = (state.enemyHp - chip).coerceAtLeast(0f)
            playerRoundDamage += chip
            _uiState.update {
                it.copy(
                    enemyHp = nextEnemyHp,
                    comboCount = 0,
                    enemyHitFlash = 0.25f
                )
            }
            addCallout("BLOQUEADO", CalloutTone.COBALT, 0.5f, 0.34f)
            addSparks(0.5f, 0.36f, CalloutTone.COBALT, 5)
            soundEngine.playBlock(perfectParry = false)
            return
        }

        val brokeGuard = state.enemyPhase == EnemyPhase.GUARDING
        val counterMult = when {
            isCounter && punch == PunchType.UPPERCUT -> 2.35f
            isCounter -> 1.95f
            brokeGuard -> 1.25f
            else -> 1f
        }
        val rawDamage = punch.damage * counterMult * comboMult * closePocketBonus * gloveDmgMult
        val nextEnemyHp = (state.enemyHp - rawDamage).coerceAtLeast(0f)
        playerRoundDamage += rawDamage

        val interrupted = isCounter || brokeGuard || punch == PunchType.HOOK || punch == PunchType.UPPERCUT ||
            (state.enemyPhase == EnemyPhase.TELEGRAPH && punch == PunchType.JAB && Random.nextFloat() < 0.42f)

        val nextEnemyPhase = if (interrupted) EnemyPhase.HIT_STUN else state.enemyPhase
        if (interrupted) {
            enemyStateTimerMs = if (isCounter) 420L else 260L
            enemyStrikeImpactDone = false
            enemyComboChainRemaining = 0
        }

        val nextCombo = state.comboCount + 1
        val excitementGain = when {
            isCounter -> 0.32f
            nextCombo >= 3 -> 0.20f
            else -> 0.10f
        }

        _uiState.update { curr ->
            curr.copy(
                enemyHp = nextEnemyHp,
                enemyPhase = nextEnemyPhase,
                enemyAttack = if (interrupted) null else curr.enemyAttack,
                enemyPhaseProgress = if (interrupted) 0f else curr.enemyPhaseProgress,
                enemyHitFlash = if (isCounter) 1f else 0.72f,
                screenShake = if (isCounter || punch == PunchType.UPPERCUT || punch == PunchType.HOOK) 0.9f else 0.4f,
                counterWindowMs = 0L,
                comboCount = nextCombo,
                crowdExcitement = (curr.crowdExcitement + excitementGain).coerceAtMost(1f),
                stats = curr.stats.copy(
                    punchesLanded = curr.stats.punchesLanded + 1,
                    countersLanded = curr.stats.countersLanded + if (isCounter) 1 else 0
                )
            )
        }

        val hitX = (0.5f + state.lateralDelta * 0.45f).coerceIn(0.25f, 0.75f)
        when {
            isCounter -> {
                addCallout("¡CONTRAATAQUE!", CalloutTone.GOLD, hitX, 0.28f)
                addSparks(hitX, 0.32f, CalloutTone.GOLD, 12)
                soundEngine.playImpact(heavy = true, counter = true)
                maybePlayCrowdRoar("¡LA HINCHADA ENLOQUECE!")
            }
            brokeGuard -> {
                addCallout("¡GUARDIA ROTA!", CalloutTone.GOLD, hitX, 0.30f)
                addSparks(hitX, 0.34f, CalloutTone.GOLD, 9)
                soundEngine.playImpact(heavy = true, counter = false)
            }
            punch == PunchType.HOOK || punch == PunchType.UPPERCUT -> {
                addCallout("¡GOLPE DURO!", CalloutTone.CRIMSON, hitX, 0.31f)
                addSparks(hitX, 0.34f, CalloutTone.CRIMSON, 9)
                soundEngine.playImpact(heavy = true, counter = false)
                if (nextCombo >= 4) maybePlayCrowdRoar("¡RÁFAGA DE GOLPES!")
            }
            else -> {
                addSparks(hitX, 0.34f, CalloutTone.WHITE, 6)
                soundEngine.playImpact(heavy = false, counter = false)
            }
        }

        if (nextEnemyHp <= 0f) {
            triggerKnockdown(who = HandSide.RIGHT, massiveFinish = isCounter || punch == PunchType.UPPERCUT)
        }
    }

    private fun tickEnemyAi(dtMs: Long) {
        val state = _uiState.value
        val fighter = state.fighter
        // Round difficulty scaling across 10 rounds
        val roundSpeedMult = (1f - (state.round - 1) * 0.045f).coerceAtLeast(0.56f)
        val guardBias = (fighter.guardChance + (state.round - 1) * 0.015f).coerceAtMost(0.62f)

        when (state.enemyPhase) {
            EnemyPhase.IDLE -> {
                enemyAiCooldownMs -= dtMs
                if (enemyAiCooldownMs <= 0L) {
                    val roll = Random.nextFloat()
                    if (roll < guardBias) {
                        enemyStateTimerMs = (Random.nextLong(550L, 1050L) * roundSpeedMult).toLong()
                        _uiState.update { it.copy(enemyPhase = EnemyPhase.GUARDING, enemyPhaseProgress = 0f) }
                    } else if (state.relativeDistance <= 0.84f) {
                        // Start combo flurry in later rounds
                        enemyComboChainRemaining = when {
                            state.round >= 8 && Random.nextFloat() < 0.55f -> 2
                            state.round >= 4 && Random.nextFloat() < 0.40f -> 1
                            else -> 0
                        }
                        startEnemyTelegraph(roundSpeedMult)
                    } else {
                        enemyAiCooldownMs = 360L
                    }
                }
            }

            EnemyPhase.GUARDING -> {
                enemyStateTimerMs -= dtMs
                if (enemyStateTimerMs <= 0L) {
                    val counterRoll = (fighter.counterChance + (state.round - 1) * 0.02f).coerceAtMost(0.65f)
                    if (Random.nextFloat() < counterRoll && state.relativeDistance <= 0.82f) {
                        enemyComboChainRemaining = if (state.round >= 6 && Random.nextFloat() < 0.45f) 1 else 0
                        startEnemyTelegraph(roundSpeedMult)
                    } else {
                        enemyAiCooldownMs = (Random.nextLong(380L, 760L) * roundSpeedMult).toLong()
                        _uiState.update { it.copy(enemyPhase = EnemyPhase.IDLE, enemyPhaseProgress = 0f) }
                    }
                }
            }

            EnemyPhase.TELEGRAPH -> {
                enemyStateTimerMs -= dtMs
                val total = (fighter.telegraphMs * roundSpeedMult).coerceAtLeast(300f)
                val prog = ((total - enemyStateTimerMs) / total).coerceIn(0f, 1f)
                if (enemyStateTimerMs <= 0L) {
                    enemyStateTimerMs = (240f * (0.85f + roundSpeedMult * 0.15f)).toLong()
                    enemyStrikeImpactDone = false
                    _uiState.update {
                        it.copy(enemyPhase = EnemyPhase.STRIKING, enemyPhaseProgress = 0f)
                    }
                    soundEngine.playWhoosh()
                } else {
                    _uiState.update { it.copy(enemyPhaseProgress = prog) }
                }
            }

            EnemyPhase.STRIKING -> {
                enemyStateTimerMs -= dtMs
                val prog = ((230f - enemyStateTimerMs) / 230f).coerceIn(0f, 1f)
                if (!enemyStrikeImpactDone && prog >= 0.45f) {
                    enemyStrikeImpactDone = true
                    resolveEnemyStrikeImpact()
                }
                if (_uiState.value.phase != FightPhase.FIGHTING) return
                if (enemyStateTimerMs <= 0L) {
                    val wasVulnerable = _uiState.value.counterWindowMs > 0L
                    if (!wasVulnerable && enemyComboChainRemaining > 0 && _uiState.value.relativeDistance <= 0.84f) {
                        enemyComboChainRemaining -= 1
                        startEnemyTelegraph(roundSpeedMult * 0.88f)
                    } else if (wasVulnerable) {
                        enemyComboChainRemaining = 0
                        enemyStateTimerMs = 580L
                        _uiState.update {
                            it.copy(
                                enemyPhase = EnemyPhase.VULNERABLE,
                                enemyPhaseProgress = 0f,
                                enemyAttack = null
                            )
                        }
                    } else {
                        enemyAiCooldownMs = (Random.nextLong(fighter.aiIntervalMinMs, fighter.aiIntervalMaxMs) * roundSpeedMult)
                            .toLong().coerceAtLeast(360L)
                        _uiState.update {
                            it.copy(
                                enemyPhase = EnemyPhase.IDLE,
                                enemyPhaseProgress = 0f,
                                enemyAttack = null
                            )
                        }
                    }
                } else {
                    _uiState.update { it.copy(enemyPhaseProgress = prog) }
                }
            }

            EnemyPhase.VULNERABLE, EnemyPhase.HIT_STUN -> {
                enemyStateTimerMs -= dtMs
                if (enemyStateTimerMs <= 0L) {
                    enemyAiCooldownMs = (Random.nextLong(fighter.aiIntervalMinMs, fighter.aiIntervalMaxMs) * roundSpeedMult)
                        .toLong().coerceAtLeast(360L)
                    _uiState.update {
                        it.copy(
                            enemyPhase = EnemyPhase.IDLE,
                            enemyPhaseProgress = 0f,
                            enemyAttack = null
                        )
                    }
                }
            }

            EnemyPhase.KNOCKED_DOWN -> Unit
        }
    }

    private fun startEnemyTelegraph(roundSpeedMult: Float) {
        val state = _uiState.value
        val fighter = state.fighter
        val attackPool = when {
            state.round >= 7 -> EnemyAttackType.entries
            fighter.id == "mateo" -> listOf(EnemyAttackType.LEFT_JAB, EnemyAttackType.RIGHT_CROSS, EnemyAttackType.LEFT_HOOK)
            fighter.id == "bruno" -> listOf(EnemyAttackType.LEFT_HOOK, EnemyAttackType.RIGHT_CROSS, EnemyAttackType.UPPERCUT)
            fighter.id == "viktor" -> listOf(EnemyAttackType.RIGHT_CROSS, EnemyAttackType.LEFT_HOOK, EnemyAttackType.UPPERCUT)
            else -> EnemyAttackType.entries
        }
        val attack = attackPool.random()
        enemyStateTimerMs = (fighter.telegraphMs * roundSpeedMult).toLong().coerceAtLeast(300L)
        _uiState.update {
            it.copy(
                enemyPhase = EnemyPhase.TELEGRAPH,
                enemyAttack = attack,
                enemyPhaseProgress = 0f
            )
        }
    }

    private fun resolveEnemyStrikeImpact() {
        val state = _uiState.value
        val attack = state.enemyAttack ?: return
        if (playerInvulnMs > 0L) return

        // Footwork evasion: stepping out of range or circling completely past shoulder
        if (state.relativeDistance > attack.maxRange || abs(state.lateralDelta) > 0.56f) {
            _uiState.update {
                it.copy(
                    counterWindowMs = 850L,
                    stats = it.stats.copy(dodgesMade = it.stats.dodgesMade + 1)
                )
            }
            addCallout("¡AL AIRE!", CalloutTone.EMERALD, 0.5f, 0.56f)
            soundEngine.playWhoosh()
            return
        }

        val roundPowerMult = 1f + (state.round - 1) * 0.065f
        val powerMult = (0.76f + (state.fighter.powerStat * 0.052f)) * roundPowerMult
        val incomingDamage = attack.damage * powerMult

        // Check active dodge
        val isDodging = state.playerAction == PlayerAction.DODGE_LEFT || state.playerAction == PlayerAction.DODGE_RIGHT
        if (isDodging) {
            val dodgedSide = if (state.playerAction == PlayerAction.DODGE_LEFT) HandSide.LEFT else HandSide.RIGHT
            if (dodgedSide == attack.expectedDodge || !attack.isHeavy) {
                _uiState.update {
                    it.copy(
                        counterWindowMs = 1200L,
                        crowdExcitement = (it.crowdExcitement + 0.16f).coerceAtMost(1f),
                        stats = it.stats.copy(dodgesMade = it.stats.dodgesMade + 1)
                    )
                }
                addCallout("¡ESQUIVA PERFECTA!", CalloutTone.EMERALD, 0.5f, 0.58f)
                soundEngine.playWhoosh()
                return
            } else {
                val graze = incomingDamage * 0.38f
                val nextHp = (state.playerHp - graze).coerceAtLeast(0f)
                enemyRoundDamage += graze
                _uiState.update {
                    it.copy(
                        playerHp = nextHp,
                        playerHitFlash = 0.45f,
                        screenShake = 0.35f,
                        comboCount = 0
                    )
                }
                addCallout("ROCE", CalloutTone.CRIMSON, 0.5f, 0.64f)
                soundEngine.playImpact(heavy = false)
                if (nextHp <= 0f) triggerKnockdown(who = HandSide.LEFT, massiveFinish = false)
                return
            }
        }

        // Check Guard / Parry (enhanced by equipped glove!)
        if (state.isGuarding || state.playerAction == PlayerAction.GUARD) {
            val parryWindow = 270L + (state.equippedGlove.guardAbsorptionBonus * 900L).toLong()
            val timeSinceGuard = totalFightElapsedMs - guardStartedElapsedMs
            val isPerfectParry = timeSinceGuard in 0L..parryWindow
            if (isPerfectParry) {
                _uiState.update {
                    it.copy(
                        counterWindowMs = 1250L,
                        crowdExcitement = (it.crowdExcitement + 0.22f).coerceAtMost(1f),
                        stats = it.stats.copy(blocksMade = it.stats.blocksMade + 1)
                    )
                }
                addCallout("¡PARRY!", CalloutTone.GOLD, 0.5f, 0.58f)
                addSparks(0.5f, 0.56f, CalloutTone.GOLD, 10)
                soundEngine.playBlock(perfectParry = true)
            } else {
                val absorbFactor = (0.18f - state.equippedGlove.guardAbsorptionBonus).coerceAtLeast(0.06f)
                val blockedDmg = incomingDamage * absorbFactor
                val nextHp = (state.playerHp - blockedDmg).coerceAtLeast(0f)
                enemyRoundDamage += blockedDmg
                _uiState.update {
                    it.copy(
                        playerHp = nextHp,
                        playerStamina = (it.playerStamina - 5.5f).coerceAtLeast(0f),
                        screenShake = 0.25f,
                        stats = it.stats.copy(blocksMade = it.stats.blocksMade + 1)
                    )
                }
                addCallout("BLOQUEO", CalloutTone.COBALT, 0.5f, 0.62f)
                soundEngine.playBlock(perfectParry = false)
                if (nextHp <= 0f) triggerKnockdown(who = HandSide.LEFT, massiveFinish = false)
            }
            return
        }

        // Clean hit on player
        val nextHp = (state.playerHp - incomingDamage).coerceAtLeast(0f)
        enemyRoundDamage += incomingDamage
        playerActionTimerMs = 240L
        _uiState.update {
            it.copy(
                playerHp = nextHp,
                playerAction = PlayerAction.HIT_STUN,
                playerHitFlash = 0.95f,
                screenShake = 0.85f,
                comboCount = 0,
                crowdExcitement = (it.crowdExcitement + 0.14f).coerceAtMost(1f)
            )
        }
        addCallout("-${incomingDamage.toInt()}", CalloutTone.CRIMSON, 0.5f, 0.65f)
        addSparks(0.5f, 0.62f, CalloutTone.CRIMSON, 8)
        soundEngine.playImpact(heavy = attack.isHeavy)

        if (nextHp <= 0f) {
            triggerKnockdown(who = HandSide.LEFT, massiveFinish = attack.isHeavy)
        }
    }

    private fun triggerKnockdown(who: HandSide, massiveFinish: Boolean) {
        soundEngine.playKnockdown()
        soundEngine.playCrowdCheer(1.0f)
        knockdownTickMs = 820L

        if (who == HandSide.RIGHT) {
            val newKds = _uiState.value.stats.knockdownsScored + 1
            enemyRecoveryTargetCount = when {
                newKds >= 3 -> 11
                newKds == 2 && massiveFinish -> 11
                newKds == 2 -> 9
                else -> 7
            }
            _uiState.update { curr ->
                curr.copy(
                    phase = FightPhase.KNOCKDOWN_COUNT,
                    enemyPhase = EnemyPhase.KNOCKED_DOWN,
                    enemyAttack = null,
                    knockedDownWho = HandSide.RIGHT,
                    knockdownCount = 1,
                    playerRoundPoints = curr.playerRoundPoints + 1,
                    stats = curr.stats.copy(knockdownsScored = newKds),
                    screenShake = 1.0f,
                    crowdExcitement = 1.0f
                )
            }
            triggerCrowdBanner("¡EL ESTADIO EXPLOTA!")
            addCallout("¡A LA LONA!", CalloutTone.GOLD, 0.5f, 0.32f)
        } else {
            val newKds = _uiState.value.stats.knockdownsSuffered + 1
            _uiState.update { curr ->
                curr.copy(
                    phase = FightPhase.KNOCKDOWN_COUNT,
                    playerAction = PlayerAction.KNOCKED_DOWN,
                    isGuarding = false,
                    knockedDownWho = HandSide.LEFT,
                    knockdownCount = 1,
                    playerGetUpProgress = 0f,
                    enemyRoundPoints = curr.enemyRoundPoints + 1,
                    stats = curr.stats.copy(knockdownsSuffered = newKds),
                    screenShake = 1.0f,
                    crowdExcitement = 0.95f
                )
            }
            triggerCrowdBanner("¡CONTEO EN LA ARENA!")
            addCallout("¡DERRIBO!", CalloutTone.CRIMSON, 0.5f, 0.65f)
        }
    }

    private fun tickKnockdown(dtMs: Long) {
        knockdownTickMs -= dtMs
        if (knockdownTickMs > 0L) return
        knockdownTickMs = 740L

        val state = _uiState.value
        val nextCount = state.knockdownCount + 1

        if (state.knockedDownWho == HandSide.RIGHT) {
            if (nextCount >= enemyRecoveryTargetCount && enemyRecoveryTargetCount <= 10) {
                val recoverRatio = if (state.stats.knockdownsScored == 1) 0.45f else 0.32f
                enemyAiCooldownMs = 950L
                _uiState.update { curr ->
                    curr.copy(
                        phase = FightPhase.FIGHTING,
                        enemyHp = curr.enemyMaxHp * recoverRatio,
                        enemyPhase = EnemyPhase.IDLE,
                        knockedDownWho = null,
                        knockdownCount = 0
                    )
                }
                addCallout("¡SE LEVANTA!", CalloutTone.GOLD, 0.5f, 0.35f)
                soundEngine.playBell(doubleRing = false)
                return
            }
        }

        if (nextCount > 10) {
            val outcome = if (state.knockedDownWho == HandSide.RIGHT) {
                MatchOutcome.KO_WIN
            } else {
                MatchOutcome.KO_LOSS
            }
            finishMatch(outcome)
        } else {
            _uiState.update { it.copy(knockdownCount = nextCount) }
        }
    }

    private fun handleRoundEnd() {
        soundEngine.playBell(doubleRing = true)
        val state = _uiState.value
        val playerScoreAdd = if (playerRoundDamage >= enemyRoundDamage) 10 else 9
        val enemyScoreAdd = if (enemyRoundDamage > playerRoundDamage) 10 else 9
        val newPlayerPts = state.playerRoundPoints + playerScoreAdd
        val newEnemyPts = state.enemyRoundPoints + enemyScoreAdd

        if (state.round >= state.maxRounds) {
            _uiState.update {
                it.copy(
                    playerRoundPoints = newPlayerPts,
                    enemyRoundPoints = newEnemyPts
                )
            }
            val outcome = if (newPlayerPts >= newEnemyPts) {
                MatchOutcome.DECISION_WIN
            } else {
                MatchOutcome.DECISION_LOSS
            }
            finishMatch(outcome)
        } else {
            roundOverTimerMs = 2150L
            val winnerTxt = if (playerRoundDamage >= enemyRoundDamage) {
                "ROUND PARA TI"
            } else {
                "ROUND PARA ${state.fighter.nickname.uppercase()}"
            }
            _uiState.update {
                it.copy(
                    phase = FightPhase.ROUND_OVER,
                    playerRoundPoints = newPlayerPts,
                    enemyRoundPoints = newEnemyPts,
                    roundSummaryText = "$winnerTxt • $newPlayerPts - $newEnemyPts",
                    isGuarding = false,
                    crowdExcitement = 0.65f
                )
            }
        }
    }

    private fun tickRoundOver(dtMs: Long) {
        roundOverTimerMs -= dtMs
        if (roundOverTimerMs > 0L) return

        val state = _uiState.value
        val nextRound = state.round + 1
        playerRoundDamage = 0f
        enemyRoundDamage = 0f
        enemyComboChainRemaining = 0
        enemyAiCooldownMs = 950L

        _uiState.update { curr ->
            curr.copy(
                roundTimeMs = 45_000L,
                playerHp = (curr.playerHp + curr.playerMaxHp * 0.16f).coerceAtMost(curr.playerMaxHp),
                playerStamina = curr.playerMaxStamina,
                playerAction = PlayerAction.IDLE,
                enemyHp = (curr.enemyHp + curr.enemyMaxHp * 0.14f).coerceAtMost(curr.enemyMaxHp),
                enemyPhase = EnemyPhase.IDLE,
                enemyAttack = null,
                counterWindowMs = 0L,
                roundSummaryText = ""
            )
        }
        beginRoundIntro(nextRound)
    }

    private fun finishMatch(outcome: MatchOutcome) {
        soundEngine.playBell(doubleRing = true)
        soundEngine.playCrowdCheer(1.0f)
        loopJob?.cancel()
        loopJob = null
        moveInputX = 0f
        moveInputZ = 0f

        val state = _uiState.value
        val basePurse = state.fighter.rewardCoins
        val rawCoins = when (outcome) {
            MatchOutcome.KO_WIN -> (basePurse * 1.45f).toInt()
            MatchOutcome.DECISION_WIN -> basePurse
            MatchOutcome.DECISION_LOSS -> (basePurse * 0.40f).toInt().coerceAtLeast(50)
            MatchOutcome.KO_LOSS -> (basePurse * 0.30f).toInt().coerceAtLeast(40)
        }
        val finalCoins = (rawCoins * state.equippedGlove.coinBonusMultiplier).toInt()

        _uiState.update {
            it.copy(
                phase = FightPhase.MATCH_OVER,
                outcome = outcome,
                coinsEarned = finalCoins,
                isGuarding = false,
                crowdExcitement = 1.0f
            )
        }

        if (!outcomeRecorded) {
            outcomeRecorded = true
            viewModelScope.launch {
                settingsRepository.addCoins(finalCoins)
            }
            val won = outcome == MatchOutcome.KO_WIN || outcome == MatchOutcome.DECISION_WIN
            val isKo = outcome == MatchOutcome.KO_WIN
            val defeatedId = state.fighter.id
            _sessionRecord.update { rec ->
                if (won) {
                    rec.copy(
                        wins = rec.wins + 1,
                        kos = rec.kos + if (isKo) 1 else 0,
                        currentStreak = rec.currentStreak + 1,
                        defeatedIds = rec.defeatedIds + defeatedId
                    )
                } else {
                    rec.copy(currentStreak = 0)
                }
            }
        }
    }

    private fun roundDifficultyLabel(round: Int): String = when (round) {
        1, 2 -> "APERTURA"
        3, 4 -> "RITMO CRECIENTE"
        5, 6 -> "PRESIÓN ALTA"
        7, 8 -> "FASE CAMPEONATO"
        9 -> "ZONA DE PELIGRO"
        else -> "¡ÚLTIMO ASALTO!"
    }

    private fun maybePlayCrowdRoar(banner: String) {
        if (totalFightElapsedMs - lastCrowdCheerElapsedMs > 4200L) {
            lastCrowdCheerElapsedMs = totalFightElapsedMs
            soundEngine.playCrowdCheer(0.85f)
            triggerCrowdBanner(banner)
        }
    }

    private fun triggerCrowdBanner(banner: String) {
        crowdBannerTimerMs = 1900L
        _uiState.update { it.copy(crowdBannerText = banner) }
    }

    private fun addCallout(text: String, tone: CalloutTone, xNorm: Float, yNorm: Float) {
        val callout = FloatingCallout(
            id = nextEffectId++,
            text = text,
            tone = tone,
            xNorm = xNorm,
            yNorm = yNorm
        )
        _uiState.update { it.copy(callouts = (it.callouts + callout).takeLast(6)) }
    }

    private fun addSparks(xNorm: Float, yNorm: Float, tone: CalloutTone, count: Int) {
        val newSparks = List(count) { idx ->
            val angle = ((idx.toFloat() / count) * 2f * PI.toFloat()) + Random.nextFloat() * 0.5f
            ImpactSpark(
                id = nextEffectId++,
                xNorm = xNorm,
                yNorm = yNorm,
                angleRad = angle,
                speed = 0.14f + Random.nextFloat() * 0.22f,
                tone = tone
            )
        }
        _uiState.update { it.copy(sparks = (it.sparks + newSparks).takeLast(28)) }
    }

    override fun onCleared() {
        super.onCleared()
        loopJob?.cancel()
        soundEngine.release()
    }
}
