package com.example.game

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.BoxingSoundEngine
import com.example.data.SettingsRepository
import com.example.model.BoxerAppearance
import com.example.model.CalloutTone
import com.example.model.FightPhase
import com.example.model.FloatingCallout
import com.example.model.GloveItem
import com.example.model.GloveShopCatalog
import com.example.model.ImpactSpark
import com.example.model.PunchType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

data class VersusBoxerState(
    val playerIndex: Int, // 1 or 2
    val name: String,
    val hp: Float = 130f,
    val maxHp: Float = 130f,
    val stamina: Float = 100f,
    val maxStamina: Float = 100f,
    val posX: Float = 0f,
    val isGuarding: Boolean = false,
    val isDodging: Boolean = false,
    val activePunch: PunchType? = null,
    val punchProgress: Float = 0f,
    val hitStunMs: Long = 0L,
    val hitFlash: Float = 0f,
    val counterWindowMs: Long = 0L,
    val comboCount: Int = 0,
    val roundPoints: Int = 0,
    val punchesLanded: Int = 0,
    val knockdownsScored: Int = 0,
    val isKnockedDown: Boolean = false,
    val skinColor: Color = Color(0xFFC47A4C),
    val hairColor: Color = Color(0xFF4A2C1D),
    val trunksColor: Color = Color(0xFFD62828),
    val gloveColor: Color = Color(0xFFE63946)
)

data class VersusUiState(
    val phase: FightPhase = FightPhase.PRESENTATION,
    val previousPhaseBeforePause: FightPhase = FightPhase.FIGHTING,
    val presentationTitle: String = "¡DUELO MULTIJUGADOR 1v1!",
    val presentationSubtitle: String = "MISMA PANTALLA • CARA A CARA",
    val round: Int = 1,
    val maxRounds: Int = 3,
    val roundTimeMs: Long = 45_000L,
    val introText: String = "ROUND 1",
    val p1: VersusBoxerState = VersusBoxerState(playerIndex = 1, name = "Jugador 1", posX = -0.35f),
    val p2: VersusBoxerState = VersusBoxerState(playerIndex = 2, name = "Jugador 2", posX = 0.35f),
    val winnerIndex: Int? = null, // 1, 2, or 0 for draw
    val winMethod: String = "",
    val crowdExcitement: Float = 0.45f,
    val screenShake: Float = 0f,
    val callouts: List<FloatingCallout> = emptyList(),
    val sparks: List<ImpactSpark> = emptyList()
)

class VersusViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application.applicationContext)
    private val soundEngine = BoxingSoundEngine(application.applicationContext)

    private val _uiState = MutableStateFlow(VersusUiState())
    val uiState: StateFlow<VersusUiState> = _uiState.asStateFlow()

    private var loopJob: Job? = null
    private var nextEffectId = 1L

    @Volatile private var p1MoveX = 0f
    @Volatile private var p2MoveX = 0f

    private var presentationMs = 2600L
    private var introMs = 1400L
    private var roundOverMs = 0L
    private var totalElapsedMs = 0L

    private var p1ActionTimerMs = 0L
    private var p2ActionTimerMs = 0L
    private var p1ImpactDone = false
    private var p2ImpactDone = false
    private var p1GuardStartMs = -9999L
    private var p2GuardStartMs = -9999L
    private var p1RoundDmg = 0f
    private var p2RoundDmg = 0f

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { s ->
                soundEngine.soundEnabled = s.soundEnabled
                soundEngine.hapticsEnabled = s.hapticsEnabled
            }
        }
    }

    fun startVersusMatch(
        p1Name: String,
        p2Name: String = "Jugador 2",
        maxRounds: Int = 3,
        p1Appearance: BoxerAppearance = BoxerAppearance(),
        p1Glove: GloveItem = GloveShopCatalog.gloves.first()
    ) {
        loopJob?.cancel()
        p1MoveX = 0f
        p2MoveX = 0f
        presentationMs = 2600L
        introMs = 1400L
        roundOverMs = 0L
        totalElapsedMs = 0L
        p1ActionTimerMs = 0L
        p2ActionTimerMs = 0L
        p1ImpactDone = false
        p2ImpactDone = false
        p1GuardStartMs = -9999L
        p2GuardStartMs = -9999L
        p1RoundDmg = 0f
        p2RoundDmg = 0f

        val cleanP1 = p1Name.trim().ifBlank { "Jugador 1" }
        val cleanP2 = p2Name.trim().ifBlank { "Jugador 2" }

        _uiState.value = VersusUiState(
            phase = FightPhase.PRESENTATION,
            presentationTitle = "${cleanP1.uppercase()}  VS  ${cleanP2.uppercase()}",
            presentationSubtitle = "DUELO CARA A CARA • $maxRounds ROUNDS",
            round = 1,
            maxRounds = maxRounds,
            roundTimeMs = 45_000L,
            p1 = VersusBoxerState(
                playerIndex = 1,
                name = cleanP1,
                posX = -0.35f,
                skinColor = p1Appearance.resolvedSkinColor(),
                hairColor = p1Appearance.resolvedHairColor(),
                trunksColor = p1Appearance.resolvedTrunksColor(),
                gloveColor = p1Glove.primaryColor
            ),
            p2 = VersusBoxerState(
                playerIndex = 2,
                name = cleanP2,
                posX = 0.35f,
                skinColor = Color(0xFFD69E76),
                hairColor = Color(0xFF1D1B24),
                trunksColor = Color(0xFF1D4ED8),
                gloveColor = Color(0xFF3B82F6)
            ),
            crowdExcitement = 0.70f
        )
        soundEngine.playPresentationFanfare()
        soundEngine.playCrowdCheer(0.75f)
        soundEngine.announceReferee("$cleanP1 contra $cleanP2, ¡combate a $maxRounds rounds!")
        startLoop()
    }

    fun skipPresentation() {
        if (_uiState.value.phase != FightPhase.PRESENTATION) return
        beginRoundIntro(1)
    }

    fun stopMatch() {
        loopJob?.cancel()
        loopJob = null
        p1MoveX = 0f
        p2MoveX = 0f
        soundEngine.stopReferee()
    }

    fun togglePause() {
        _uiState.update { curr ->
            when (curr.phase) {
                FightPhase.FIGHTING, FightPhase.INTRO, FightPhase.PRESENTATION -> {
                    soundEngine.stopReferee()
                    curr.copy(
                        phase = FightPhase.PAUSED,
                        previousPhaseBeforePause = curr.phase,
                        p1 = curr.p1.copy(isGuarding = false),
                        p2 = curr.p2.copy(isGuarding = false)
                    )
                }
                FightPhase.PAUSED -> curr.copy(phase = curr.previousPhaseBeforePause)
                else -> curr
            }
        }
    }

    fun setMove(playerIndex: Int, dirX: Float) {
        if (playerIndex == 1) p1MoveX = dirX.coerceIn(-1f, 1f)
        else p2MoveX = dirX.coerceIn(-1f, 1f)
    }

    fun setGuard(playerIndex: Int, held: Boolean) {
        val state = _uiState.value
        if (state.phase != FightPhase.FIGHTING) return
        val boxer = if (playerIndex == 1) state.p1 else state.p2
        if (boxer.hitStunMs > 0L || boxer.isKnockedDown) return

        if (held && !boxer.isGuarding) {
            if (playerIndex == 1) p1GuardStartMs = totalElapsedMs else p2GuardStartMs = totalElapsedMs
            soundEngine.playGuardUp()
        }
        updateBoxer(playerIndex) { it.copy(isGuarding = held) }
    }

    fun dodge(playerIndex: Int) {
        val state = _uiState.value
        if (state.phase != FightPhase.FIGHTING) return
        val boxer = if (playerIndex == 1) state.p1 else state.p2
        if (boxer.activePunch != null || boxer.isDodging || boxer.hitStunMs > 0L || boxer.stamina < 8f) return

        if (playerIndex == 1) p1ActionTimerMs = 320L else p2ActionTimerMs = 320L
        val backShift = if (playerIndex == 1) -0.14f else 0.14f
        updateBoxer(playerIndex) {
            it.copy(
                isDodging = true,
                isGuarding = false,
                posX = (it.posX + backShift).coerceIn(-0.88f, 0.88f),
                stamina = (it.stamina - 8f).coerceAtLeast(0f)
            )
        }
        soundEngine.playDodge(perfect = false)
    }

    fun throwPunch(playerIndex: Int, punch: PunchType) {
        val state = _uiState.value
        if (state.phase != FightPhase.FIGHTING) return
        val boxer = if (playerIndex == 1) state.p1 else state.p2
        if (boxer.activePunch != null || boxer.hitStunMs > 0L || boxer.isKnockedDown) return
        if (boxer.stamina < 6f) {
            addCallout("SIN AIRE", CalloutTone.CRIMSON, if (playerIndex == 1) 0.25f else 0.75f, 0.45f)
            return
        }

        val exhausted = boxer.stamina < punch.staminaCost
        val dur = (punch.durationMs * (if (exhausted) 1.30f else 1f)).toLong()
        if (playerIndex == 1) {
            p1ActionTimerMs = dur
            p1ImpactDone = false
        } else {
            p2ActionTimerMs = dur
            p2ImpactDone = false
        }
        updateBoxer(playerIndex) {
            it.copy(
                activePunch = punch,
                punchProgress = 0f,
                isGuarding = false,
                isDodging = false,
                stamina = (it.stamina - punch.staminaCost).coerceAtLeast(0f)
            )
        }
        soundEngine.playPunchThrow(punch)
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
        val curr = _uiState.value
        if (curr.phase == FightPhase.PAUSED || curr.phase == FightPhase.MATCH_OVER) return

        val updatedCallouts = curr.callouts.mapNotNull { c ->
            val age = c.ageMs + dtMs
            if (age >= c.durationMs) null else c.copy(ageMs = age)
        }
        val updatedSparks = curr.sparks.mapNotNull { s ->
            val age = s.ageMs + dtMs
            if (age >= s.durationMs) null else s.copy(ageMs = age)
        }

        _uiState.update {
            it.copy(
                callouts = updatedCallouts,
                sparks = updatedSparks,
                screenShake = (it.screenShake - dtMs / 200f).coerceAtLeast(0f),
                crowdExcitement = (it.crowdExcitement - dtMs / 6000f).coerceAtLeast(0.25f),
                p1 = it.p1.copy(hitFlash = (it.p1.hitFlash - dtMs / 180f).coerceAtLeast(0f)),
                p2 = it.p2.copy(hitFlash = (it.p2.hitFlash - dtMs / 180f).coerceAtLeast(0f))
            )
        }

        when (_uiState.value.phase) {
            FightPhase.PRESENTATION -> {
                presentationMs -= dtMs
                if (presentationMs <= 0L) beginRoundIntro(1)
            }
            FightPhase.INTRO -> {
                introMs -= dtMs
                if (introMs <= 480L && _uiState.value.introText != "¡BOX!") {
                    _uiState.update { it.copy(introText = "¡BOX!") }
                    soundEngine.announceReferee("¡Box!")
                }
                if (introMs <= 0L) {
                    _uiState.update { it.copy(phase = FightPhase.FIGHTING, introText = "") }
                }
            }
            FightPhase.FIGHTING -> tickFighting(dtMs)
            FightPhase.ROUND_OVER -> {
                roundOverMs -= dtMs
                if (roundOverMs <= 0L) {
                    val nextRound = _uiState.value.round + 1
                    p1RoundDmg = 0f
                    p2RoundDmg = 0f
                    _uiState.update { s ->
                        s.copy(
                            roundTimeMs = 45_000L,
                            p1 = s.p1.copy(
                                hp = (s.p1.hp + s.p1.maxHp * 0.22f).coerceAtMost(s.p1.maxHp),
                                stamina = s.p1.maxStamina,
                                posX = -0.35f,
                                activePunch = null,
                                isGuarding = false
                            ),
                            p2 = s.p2.copy(
                                hp = (s.p2.hp + s.p2.maxHp * 0.22f).coerceAtMost(s.p2.maxHp),
                                stamina = s.p2.maxStamina,
                                posX = 0.35f,
                                activePunch = null,
                                isGuarding = false
                            )
                        )
                    }
                    beginRoundIntro(nextRound)
                }
            }
            else -> Unit
        }
    }

    private fun beginRoundIntro(roundNum: Int) {
        introMs = 1400L
        _uiState.update {
            it.copy(
                phase = FightPhase.INTRO,
                round = roundNum,
                introText = "ROUND $roundNum"
            )
        }
        soundEngine.playBell(doubleRing = false)
        soundEngine.announceReferee("Round $roundNum")
    }

    private fun tickFighting(dtMs: Long) {
        totalElapsedMs += dtMs
        val dtSec = dtMs / 1000f
        val state = _uiState.value

        // Movement with non-overlap spacing
        var x1 = state.p1.posX
        var x2 = state.p2.posX
        if (state.p1.activePunch == null && state.p1.hitStunMs <= 0L) {
            x1 = (x1 + p1MoveX * 1.1f * (if (state.p1.isGuarding) 0.5f else 1f) * dtSec).coerceIn(-0.88f, 0.72f)
        }
        if (state.p2.activePunch == null && state.p2.hitStunMs <= 0L) {
            x2 = (x2 + p2MoveX * 1.1f * (if (state.p2.isGuarding) 0.5f else 1f) * dtSec).coerceIn(-0.72f, 0.88f)
        }
        val minSpacing = 0.24f
        if (x2 - x1 < minSpacing) {
            val mid = (x1 + x2) / 2f
            x1 = (mid - minSpacing / 2f).coerceIn(-0.88f, 0.64f)
            x2 = (x1 + minSpacing).coerceIn(-0.64f, 0.88f)
        }

        val p1Regen = if (state.p1.isGuarding) 9f else 24f
        val p2Regen = if (state.p2.isGuarding) 9f else 24f

        val nextRoundTime = (state.roundTimeMs - dtMs).coerceAtLeast(0L)
        _uiState.update { s ->
            s.copy(
                roundTimeMs = nextRoundTime,
                p1 = s.p1.copy(
                    posX = x1,
                    stamina = if (s.p1.activePunch != null) s.p1.stamina else (s.p1.stamina + p1Regen * dtSec).coerceAtMost(s.p1.maxStamina),
                    hitStunMs = (s.p1.hitStunMs - dtMs).coerceAtLeast(0L),
                    counterWindowMs = (s.p1.counterWindowMs - dtMs).coerceAtLeast(0L)
                ),
                p2 = s.p2.copy(
                    posX = x2,
                    stamina = if (s.p2.activePunch != null) s.p2.stamina else (s.p2.stamina + p2Regen * dtSec).coerceAtMost(s.p2.maxStamina),
                    hitStunMs = (s.p2.hitStunMs - dtMs).coerceAtLeast(0L),
                    counterWindowMs = (s.p2.counterWindowMs - dtMs).coerceAtLeast(0L)
                )
            )
        }

        tickBoxerAction(playerIndex = 1, dtMs = dtMs)
        if (_uiState.value.phase == FightPhase.FIGHTING) {
            tickBoxerAction(playerIndex = 2, dtMs = dtMs)
        }
        if (_uiState.value.phase == FightPhase.FIGHTING && _uiState.value.roundTimeMs <= 0L) {
            handleRoundEnd()
        }
    }

    private fun tickBoxerAction(playerIndex: Int, dtMs: Long) {
        val state = _uiState.value
        val boxer = if (playerIndex == 1) state.p1 else state.p2
        val timer = if (playerIndex == 1) p1ActionTimerMs else p2ActionTimerMs

        if (boxer.activePunch != null) {
            val nextTimer = timer - dtMs
            if (playerIndex == 1) p1ActionTimerMs = nextTimer else p2ActionTimerMs = nextTimer

            val total = boxer.activePunch.durationMs.toFloat().coerceAtLeast(1f)
            val prog = ((total - nextTimer) / total).coerceIn(0f, 1f)
            val impactDone = if (playerIndex == 1) p1ImpactDone else p2ImpactDone

            if (!impactDone && prog >= 0.48f) {
                if (playerIndex == 1) p1ImpactDone = true else p2ImpactDone = true
                resolvePvpPunch(attackerIndex = playerIndex, punch = boxer.activePunch)
            }

            if (_uiState.value.phase != FightPhase.FIGHTING) return

            if (nextTimer <= 0L) {
                updateBoxer(playerIndex) { it.copy(activePunch = null, punchProgress = 0f) }
            } else {
                updateBoxer(playerIndex) { it.copy(punchProgress = prog) }
            }
        } else if (boxer.isDodging) {
            val nextTimer = timer - dtMs
            if (playerIndex == 1) p1ActionTimerMs = nextTimer else p2ActionTimerMs = nextTimer
            if (nextTimer <= 0L) {
                updateBoxer(playerIndex) { it.copy(isDodging = false) }
            }
        }
    }

    private fun resolvePvpPunch(attackerIndex: Int, punch: PunchType) {
        val state = _uiState.value
        val attacker = if (attackerIndex == 1) state.p1 else state.p2
        val defenderIndex = if (attackerIndex == 1) 2 else 1
        val defender = if (defenderIndex == 1) state.p1 else state.p2

        val dist = abs(state.p2.posX - state.p1.posX)
        val reach = when (punch) {
            PunchType.JAB, PunchType.CROSS -> 0.56f
            PunchType.HOOK -> 0.46f
            PunchType.UPPERCUT -> 0.42f
        }
        val impactXNorm = ((attacker.posX + defender.posX) * 0.25f + 0.5f).coerceIn(0.2f, 0.8f)

        if (dist > reach) {
            addCallout("¡FUERA DE RANGO!", CalloutTone.WHITE, impactXNorm, 0.44f)
            return
        }

        if (defender.isDodging) {
            updateBoxer(defenderIndex) { it.copy(counterWindowMs = 1100L) }
            addCallout("¡ESQUIVA!", CalloutTone.EMERALD, impactXNorm, 0.42f)
            soundEngine.playDodge(perfect = true)
            return
        }

        if (defender.isGuarding && punch.breaksGuardFactor < 0.5f) {
            val guardStart = if (defenderIndex == 1) p1GuardStartMs else p2GuardStartMs
            val isParry = (totalElapsedMs - guardStart) in 0L..260L
            if (isParry) {
                updateBoxer(defenderIndex) { it.copy(counterWindowMs = 1150L) }
                addCallout("¡PARRY!", CalloutTone.GOLD, impactXNorm, 0.42f)
                addSparks(impactXNorm, 0.5f, CalloutTone.GOLD, 9)
                soundEngine.playBlock(perfectParry = true)
            } else {
                val chip = punch.damage * 0.20f
                if (attackerIndex == 1) p1RoundDmg += chip else p2RoundDmg += chip
                updateBoxer(defenderIndex) { it.copy(hp = (it.hp - chip).coerceAtLeast(0f), hitFlash = 0.25f) }
                updateBoxer(attackerIndex) { it.copy(comboCount = 0) }
                addCallout("BLOQUEO", CalloutTone.COBALT, impactXNorm, 0.44f)
                soundEngine.playBlock(perfectParry = false, heavy = punch != PunchType.JAB)
            }
            return
        }

        val isCounter = attacker.counterWindowMs > 0L
        val brokeGuard = defender.isGuarding
        val dmg = punch.damage * (if (isCounter) 1.9f else if (brokeGuard) 1.25f else 1f)
        if (attackerIndex == 1) p1RoundDmg += dmg else p2RoundDmg += dmg

        val nextDefenderHp = (defender.hp - dmg).coerceAtLeast(0f)
        updateBoxer(defenderIndex) {
            it.copy(
                hp = nextDefenderHp,
                hitFlash = 0.9f,
                hitStunMs = if (isCounter || punch != PunchType.JAB) 260L else 140L,
                isGuarding = false,
                activePunch = null,
                comboCount = 0
            )
        }
        updateBoxer(attackerIndex) {
            it.copy(
                counterWindowMs = 0L,
                comboCount = it.comboCount + 1,
                punchesLanded = it.punchesLanded + 1
            )
        }

        _uiState.update {
            it.copy(
                screenShake = if (isCounter || punch != PunchType.JAB) 0.85f else 0.35f,
                crowdExcitement = (it.crowdExcitement + if (isCounter) 0.3f else 0.14f).coerceAtMost(1f)
            )
        }

        val tone = if (isCounter || brokeGuard) CalloutTone.GOLD else CalloutTone.CRIMSON
        val text = when {
            isCounter -> "¡CONTRAATAQUE!"
            brokeGuard -> "¡GUARDIA ROTA!"
            punch == PunchType.UPPERCUT -> "¡UPPERCUT!"
            else -> "-${dmg.toInt()}"
        }
        addCallout(text, tone, impactXNorm, 0.42f)
        addSparks(impactXNorm, 0.50f, tone, if (isCounter) 12 else 7)
        if (brokeGuard) {
            soundEngine.playGuardBreak()
        } else {
            soundEngine.playImpact(heavy = isCounter || punch != PunchType.JAB, counter = isCounter, punchType = punch)
        }

        if (nextDefenderHp <= 0f) {
            updateBoxer(attackerIndex) { it.copy(knockdownsScored = it.knockdownsScored + 1) }
            updateBoxer(defenderIndex) { it.copy(isKnockedDown = true) }
            soundEngine.playKnockdown()
            soundEngine.playCrowdCheer(1.0f)
            soundEngine.announceReferee("¡Nocaut! ¡Ganador, ${attacker.name}!")
            loopJob?.cancel()
            _uiState.update {
                it.copy(
                    phase = FightPhase.MATCH_OVER,
                    winnerIndex = attackerIndex,
                    winMethod = "¡NOCAUT!",
                    crowdExcitement = 1.0f
                )
            }
        }
    }

    private fun handleRoundEnd() {
        soundEngine.playBell(doubleRing = true)
        val state = _uiState.value
        val p1Pts = state.p1.roundPoints + if (p1RoundDmg >= p2RoundDmg) 10 else 9
        val p2Pts = state.p2.roundPoints + if (p2RoundDmg >= p1RoundDmg) 10 else 9

        _uiState.update {
            it.copy(
                p1 = it.p1.copy(roundPoints = p1Pts),
                p2 = it.p2.copy(roundPoints = p2Pts)
            )
        }

        if (state.round >= state.maxRounds) {
            loopJob?.cancel()
            val winner = when {
                p1Pts > p2Pts -> 1
                p2Pts > p1Pts -> 2
                state.p1.hp >= state.p2.hp -> 1
                else -> 2
            }
            val winnerName = if (winner == 1) state.p1.name else state.p2.name
            soundEngine.announceReferee("¡Decisión por puntos! ¡Ganador, $winnerName!")
            _uiState.update {
                it.copy(
                    phase = FightPhase.MATCH_OVER,
                    winnerIndex = winner,
                    winMethod = "DECISIÓN POR PUNTOS ($p1Pts - $p2Pts)",
                    crowdExcitement = 1.0f
                )
            }
        } else {
            roundOverMs = 1900L
            soundEngine.announceReferee("¡Fin del round ${state.round}!")
            _uiState.update {
                it.copy(
                    phase = FightPhase.ROUND_OVER,
                    introText = "FIN DEL ROUND ${state.round}"
                )
            }
        }
    }

    private fun updateBoxer(playerIndex: Int, transform: (VersusBoxerState) -> VersusBoxerState) {
        _uiState.update { s ->
            if (playerIndex == 1) s.copy(p1 = transform(s.p1)) else s.copy(p2 = transform(s.p2))
        }
    }

    private fun addCallout(text: String, tone: CalloutTone, xNorm: Float, yNorm: Float) {
        val c = FloatingCallout(id = nextEffectId++, text = text, tone = tone, xNorm = xNorm, yNorm = yNorm)
        _uiState.update { it.copy(callouts = (it.callouts + c).takeLast(6)) }
    }

    private fun addSparks(xNorm: Float, yNorm: Float, tone: CalloutTone, count: Int) {
        val list = List(count) { idx ->
            val angle = ((idx.toFloat() / count) * 2f * PI.toFloat()) + Random.nextFloat() * 0.5f
            ImpactSpark(id = nextEffectId++, xNorm = xNorm, yNorm = yNorm, angleRad = angle, speed = 0.14f + Random.nextFloat() * 0.2f, tone = tone)
        }
        _uiState.update { it.copy(sparks = (it.sparks + list).takeLast(24)) }
    }

    override fun onCleared() {
        super.onCleared()
        loopJob?.cancel()
        soundEngine.release()
    }
}
