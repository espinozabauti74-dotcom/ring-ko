package com.example.online

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.BoxingSoundEngine
import com.example.model.CalloutTone
import com.example.model.FightPhase
import com.example.model.GloveItem
import com.example.model.GloveShopCatalog
import com.example.model.HandSide
import com.example.model.PunchType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class OnlineBoutPhase {
    LOADING,
    WAITING_OPPONENT,
    PRESENTATION,
    FIGHTING,
    FINISHED,
    ERROR
}

data class OnlineFightUiState(
    val matchId: String = "",
    val phase: OnlineBoutPhase = OnlineBoutPhase.LOADING,
    val isHost: Boolean = true,
    val myName: String = "Retador",
    val oppName: String = "Rival Online",
    val myGlove: GloveItem = GloveShopCatalog.gloves.first(),
    val oppGlove: GloveItem = GloveShopCatalog.gloves[1],
    val maxRounds: Int = 3,
    val currentRound: Int = 1,
    val roundTimeMs: Long = 35_000L,
    val myHp: Float = 150f,
    val myMaxHp: Float = 150f,
    val oppHp: Float = 150f,
    val oppMaxHp: Float = 150f,
    val myStamina: Float = 100f,
    val oppStamina: Float = 100f,
    val myPosX: Float = -0.35f,
    val oppPosX: Float = 0.35f,
    val myGuarding: Boolean = false,
    val oppGuarding: Boolean = false,
    val myDodging: Boolean = false,
    val oppDodging: Boolean = false,
    val myActivePunch: PunchType? = null,
    val myPunchProgress: Float = 0f,
    val oppActivePunch: PunchType? = null,
    val oppPunchProgress: Float = 0f,
    val hitFlashAlpha: Float = 0f,
    val oppHitFlashAlpha: Float = 0f,
    val counterWindowMs: Long = 0L,
    val calloutText: String = "",
    val calloutTone: CalloutTone = CalloutTone.COBALT,
    val winnerIsMe: Boolean? = null,
    val winMethod: String? = null,
    val errorMessage: String? = null,
    val crowdExcitement: Float = 0.35f,
    val myHitsLanded: Int = 0,
    val oppHitsLanded: Int = 0,
    val isWifiLocal: Boolean = false,
    val wifiAddressHint: String = ""
)

class OnlineFightViewModel(
    private val repository: OnlineRepository,
    private val soundEngine: BoxingSoundEngine,
    private val currentUserId: String,
    private val soundEnabled: () -> Boolean,
    private val hapticsEnabled: () -> Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineFightUiState())
    val uiState: StateFlow<OnlineFightUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var loopJob: Job? = null

    private var currentMatch: OnlineMatch? = null
    private var moveDir: Float = 0f
    private var dodgeRemainingMs: Long = 0L
    private var oppDodgeRemainingMs: Long = 0L
    private var guardStartTimeMs: Long = 0L
    private var calloutRemainingMs: Long = 0L
    private var presentationRemainingMs: Long = 0L

    private var myPunchElapsedMs: Long = 0L
    private var oppPunchElapsedMs: Long = 0L

    private var myPunchSeq: Long = 0L
    private var myDodgeSeq: Long = 0L
    private var myKnockdowns: Int = 0
    private var oppKnockdowns: Int = 0
    private var lastSyncedOppPunchSeq: Long = 0L
    private var lastSyncedOppDodgeSeq: Long = 0L
    private var lastSyncTimeMs: Long = 0L
    private var resultRecorded: Boolean = false

    fun attachMatch(matchId: String) {
        if (_uiState.value.matchId == matchId && observeJob?.isActive == true) return
        stopMatch()
        resultRecorded = false
        lastSyncedOppPunchSeq = 0L
        lastSyncedOppDodgeSeq = 0L
        myPunchSeq = 0L
        myDodgeSeq = 0L
        myKnockdowns = 0
        oppKnockdowns = 0

        _uiState.value = OnlineFightUiState(matchId = matchId, phase = OnlineBoutPhase.LOADING)

        observeJob = viewModelScope.launch {
            repository.observeMatch(matchId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w("OnlineFightVM", "Error observing online match", rootError)
                    _uiState.update {
                        it.copy(
                            phase = OnlineBoutPhase.ERROR,
                            errorMessage = rootError.message ?: "Error de conexión con la sala"
                        )
                    }
                }
                .collect { match ->
                    if (match == null) {
                        _uiState.update {
                            if (it.phase == OnlineBoutPhase.FINISHED) it
                            else it.copy(
                                phase = OnlineBoutPhase.ERROR,
                                errorMessage = "La sala fue cancelada o ya no existe."
                            )
                        }
                        return@collect
                    }
                    handleMatchSnapshot(match)
                }
        }
    }

    private fun handleMatchSnapshot(match: OnlineMatch) {
        currentMatch = match
        val isHost = match.hostUid == currentUserId
        val myName = if (isHost) match.hostName else (match.guestName ?: "Retador")
        val oppName = if (isHost) (match.guestName ?: "Esperando rival...") else match.hostName
        val myGlove = GloveShopCatalog.byId(if (isHost) match.hostGloveId else (match.guestGloveId ?: "classic_red"))
        val oppGlove = GloveShopCatalog.byId(if (isHost) (match.guestGloveId ?: "cobalt_flash") else match.hostGloveId)

        val oppHp = (if (isHost) match.guestHp else match.hostHp).toFloat()
        val oppStamina = (if (isHost) match.guestStamina else match.hostStamina).toFloat()
        val oppPos = (if (isHost) match.guestPosX else match.hostPosX).toFloat()
        val oppGuarding = if (isHost) match.guestGuarding else match.hostGuarding
        val oppPunchSeq = if (isHost) match.guestPunchSeq else match.hostPunchSeq
        val oppDodgeSeq = if (isHost) match.guestDodgeSeq else match.hostDodgeSeq
        val oppPunchTypeRaw = if (isHost) match.guestPunchType else match.hostPunchType
        val oppPunchDamage = (if (isHost) match.guestPunchDamage else match.hostPunchDamage).toFloat()
        val oppHits = (if (isHost) match.guestHitsLanded else match.hostHitsLanded).toInt()
        oppKnockdowns = (if (isHost) match.guestKnockdowns else match.hostKnockdowns).toInt()

        when (match.status) {
            "WAITING" -> {
                _uiState.update {
                    it.copy(
                        phase = OnlineBoutPhase.WAITING_OPPONENT,
                        isHost = isHost,
                        myName = myName,
                        oppName = oppName,
                        myGlove = myGlove,
                        oppGlove = oppGlove,
                        maxRounds = match.maxRounds.toInt(),
                        myPosX = if (isHost) -0.35f else 0.35f,
                        oppPosX = oppPos
                    )
                }
            }

            "ACTIVE" -> {
                val previousPhase = _uiState.value.phase
                if (previousPhase == OnlineBoutPhase.LOADING || previousPhase == OnlineBoutPhase.WAITING_OPPONENT) {
                    presentationRemainingMs = 2600L
                    if (soundEnabled()) soundEngine.playPresentationFanfare()
                    _uiState.update {
                        it.copy(
                            phase = OnlineBoutPhase.PRESENTATION,
                            isHost = isHost,
                            myName = myName,
                            oppName = oppName,
                            myGlove = myGlove,
                            oppGlove = oppGlove,
                            maxRounds = match.maxRounds.toInt(),
                            myHp = (if (isHost) match.hostHp else match.guestHp).toFloat(),
                            oppHp = oppHp,
                            myPosX = if (isHost) -0.35f else 0.35f,
                            oppPosX = oppPos
                        )
                    }
                    startLoopIfNeeded()
                } else {
                    _uiState.update {
                        it.copy(
                            isHost = isHost,
                            myName = myName,
                            oppName = oppName,
                            myGlove = myGlove,
                            oppGlove = oppGlove,
                            oppHp = oppHp,
                            oppStamina = oppStamina,
                            oppPosX = oppPos,
                            oppGuarding = oppGuarding,
                            oppHitsLanded = oppHits
                        )
                    }
                }

                if (oppDodgeSeq > lastSyncedOppDodgeSeq) {
                    lastSyncedOppDodgeSeq = oppDodgeSeq
                    oppDodgeRemainingMs = 340L
                }

                if (oppPunchSeq > lastSyncedOppPunchSeq) {
                    lastSyncedOppPunchSeq = oppPunchSeq
                    val punch = PunchType.entries.firstOrNull { it.name == oppPunchTypeRaw }
                    if (punch != null) {
                        resolveIncomingOpponentPunch(punch, oppPunchDamage, oppPos)
                    }
                }
            }

            "FINISHED" -> {
                loopJob?.cancel()
                val won = match.winnerUid == currentUserId
                val method = match.winMethod ?: "KO"
                if (!resultRecorded) {
                    resultRecorded = true
                    if (soundEnabled()) {
                        if (won) soundEngine.playCrowdCheer() else soundEngine.playBell()
                    }
                    viewModelScope.launch {
                        repository.recordMatchResult(won = won, isKo = (method == "KO"))
                    }
                }
                _uiState.update {
                    it.copy(
                        phase = OnlineBoutPhase.FINISHED,
                        isHost = isHost,
                        myName = myName,
                        oppName = oppName,
                        myGlove = myGlove,
                        oppGlove = oppGlove,
                        myHp = (if (isHost) match.hostHp else match.guestHp).toFloat(),
                        oppHp = oppHp,
                        winnerIsMe = won,
                        winMethod = method
                    )
                }
            }
        }
    }

    private fun resolveIncomingOpponentPunch(
        punch: PunchType,
        damage: Float,
        oppPosX: Float
    ) {
        oppPunchElapsedMs = 0L
        _uiState.update { it.copy(oppActivePunch = punch, oppPunchProgress = 0.05f) }
        if (soundEnabled()) soundEngine.playWhoosh()

        val s = _uiState.value
        if (s.phase != OnlineBoutPhase.FIGHTING) return

        val dist = abs(s.myPosX - oppPosX)
        if (dist > punch.maxRange) return

        if (dodgeRemainingMs > 0L) {
            triggerCallout("¡ESQUIVA PERFECTA!", CalloutTone.EMERALD)
            _uiState.update { it.copy(counterWindowMs = 750L, crowdExcitement = (it.crowdExcitement + 0.14f).coerceAtMost(1f)) }
            return
        }

        val now = System.currentTimeMillis()
        if (s.myGuarding) {
            val parryWindow = (now - guardStartTimeMs) <= 280L
            if (parryWindow) {
                triggerCallout("¡PARRY! CONTRA 2x", CalloutTone.GOLD)
                if (soundEnabled()) soundEngine.playBlock(perfectParry = true)
                _uiState.update { it.copy(counterWindowMs = 900L, crowdExcitement = (it.crowdExcitement + 0.18f).coerceAtMost(1f)) }
            } else {
                val chip = (damage * (0.24f - s.myGlove.guardAbsorptionBonus)).coerceAtLeast(1f)
                val nextHp = (s.myHp - chip).coerceAtLeast(1f)
                if (soundEnabled()) soundEngine.playBlock()
                _uiState.update { it.copy(myHp = nextHp, hitFlashAlpha = 0.25f) }
                syncNow()
            }
            return
        }

        val nextHp = (s.myHp - damage).coerceAtLeast(0f)
        soundEngine.soundEnabled = soundEnabled()
        soundEngine.hapticsEnabled = hapticsEnabled()
        soundEngine.playImpact(heavy = damage >= 15f)

        _uiState.update {
            it.copy(
                myHp = nextHp,
                hitFlashAlpha = 0.72f,
                crowdExcitement = (it.crowdExcitement + 0.20f).coerceAtMost(1f)
            )
        }
        triggerCallout("-${damage.toInt()} IMPACTO", CalloutTone.CRIMSON)

        if (nextHp <= 0f) {
            myKnockdowns += 1
            val match = currentMatch ?: return
            val oppUid = if (match.hostUid == currentUserId) match.guestUid else match.hostUid
            if (oppUid != null) {
                viewModelScope.launch {
                    repository.finishMatch(
                        matchId = match.roomCode,
                        winnerUid = oppUid,
                        winMethod = "KO",
                        hostHp = if (match.hostUid == currentUserId) 0 else _uiState.value.oppHp.toInt(),
                        guestHp = if (match.hostUid == currentUserId) _uiState.value.oppHp.toInt() else 0,
                        hostKnockdowns = if (match.hostUid == currentUserId) myKnockdowns else oppKnockdowns,
                        guestKnockdowns = if (match.hostUid == currentUserId) oppKnockdowns else myKnockdowns
                    )
                }
            }
        } else {
            syncNow()
        }
    }

    private fun startLoopIfNeeded() {
        if (loopJob?.isActive == true) return
        loopJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (true) {
                delay(16L)
                val now = System.currentTimeMillis()
                val dt = (now - lastTick).coerceIn(1L, 64L)
                lastTick = now
                tick(dt, now)
            }
        }
    }

    private fun tick(dt: Long, now: Long) {
        val s = _uiState.value
        when (s.phase) {
            OnlineBoutPhase.PRESENTATION -> {
                presentationRemainingMs -= dt
                if (presentationRemainingMs <= 0L) {
                    if (soundEnabled()) soundEngine.playBell()
                    triggerCallout("¡ROUND 1 — PELEEN!", CalloutTone.GOLD)
                    _uiState.update { it.copy(phase = OnlineBoutPhase.FIGHTING, roundTimeMs = 35_000L) }
                }
            }

            OnlineBoutPhase.FIGHTING -> {
                if (dodgeRemainingMs > 0L) dodgeRemainingMs = (dodgeRemainingMs - dt).coerceAtLeast(0L)
                if (oppDodgeRemainingMs > 0L) oppDodgeRemainingMs = (oppDodgeRemainingMs - dt).coerceAtLeast(0L)
                if (calloutRemainingMs > 0L) {
                    calloutRemainingMs = (calloutRemainingMs - dt).coerceAtLeast(0L)
                    if (calloutRemainingMs == 0L) _uiState.update { it.copy(calloutText = "") }
                }

                var myPunch = s.myActivePunch
                var myProg = s.myPunchProgress
                if (myPunch != null) {
                    myPunchElapsedMs += dt
                    myProg = (myPunchElapsedMs.toFloat() / myPunch.durationMs.toFloat()).coerceIn(0f, 1f)
                    if (myPunchElapsedMs >= myPunch.durationMs) {
                        myPunch = null
                        myProg = 0f
                    }
                }

                var oppPunch = s.oppActivePunch
                var oppProg = s.oppPunchProgress
                if (oppPunch != null) {
                    oppPunchElapsedMs += dt
                    oppProg = (oppPunchElapsedMs.toFloat() / oppPunch.durationMs.toFloat()).coerceIn(0f, 1f)
                    if (oppPunchElapsedMs >= oppPunch.durationMs) {
                        oppPunch = null
                        oppProg = 0f
                    }
                }

                val moveStep = moveDir * (dt / 1000f) * 1.15f
                val nextPos = (s.myPosX + moveStep).coerceIn(-0.88f, 0.88f)
                val staminaRate = if (s.myGuarding) 5f else 18f
                val nextStamina = (s.myStamina + staminaRate * (dt / 1000f)).coerceIn(0f, 100f)
                val nextRoundTime = (s.roundTimeMs - dt).coerceAtLeast(0L)

                var nextRound = s.currentRound
                var finalRoundTime = nextRoundTime
                if (nextRoundTime == 0L) {
                    if (s.currentRound < s.maxRounds) {
                        nextRound += 1
                        finalRoundTime = 35_000L
                        if (soundEnabled()) soundEngine.playBell()
                        triggerCallout("¡ROUND $nextRound!", CalloutTone.GOLD)
                    } else {
                        finishByPoints()
                        return
                    }
                }

                _uiState.update {
                    it.copy(
                        myPosX = nextPos,
                        myStamina = nextStamina,
                        myDodging = dodgeRemainingMs > 0L,
                        oppDodging = oppDodgeRemainingMs > 0L,
                        myActivePunch = myPunch,
                        myPunchProgress = myProg,
                        oppActivePunch = oppPunch,
                        oppPunchProgress = oppProg,
                        hitFlashAlpha = (it.hitFlashAlpha - dt * 0.0025f).coerceAtLeast(0f),
                        oppHitFlashAlpha = (it.oppHitFlashAlpha - dt * 0.0025f).coerceAtLeast(0f),
                        counterWindowMs = (it.counterWindowMs - dt).coerceAtLeast(0L),
                        crowdExcitement = (it.crowdExcitement - dt * 0.00012f).coerceAtLeast(0.28f),
                        currentRound = nextRound,
                        roundTimeMs = finalRoundTime
                    )
                }

                if (now - lastSyncTimeMs >= 140L && moveDir != 0f) {
                    syncNow()
                }
            }

            else -> Unit
        }
    }

    fun skipPresentation() {
        if (_uiState.value.phase == OnlineBoutPhase.PRESENTATION) {
            presentationRemainingMs = 0L
            if (soundEnabled()) soundEngine.playBell()
            triggerCallout("¡A PELEAR!", CalloutTone.GOLD)
            _uiState.update { it.copy(phase = OnlineBoutPhase.FIGHTING, roundTimeMs = 35_000L) }
        }
    }

    fun setMoveInput(dir: Float) {
        moveDir = dir.coerceIn(-1f, 1f)
        if (dir == 0f && _uiState.value.phase == OnlineBoutPhase.FIGHTING) {
            syncNow()
        }
    }

    fun setGuard(active: Boolean) {
        val s = _uiState.value
        if (s.phase != OnlineBoutPhase.FIGHTING) return
        if (active && !s.myGuarding) guardStartTimeMs = System.currentTimeMillis()
        _uiState.update { it.copy(myGuarding = active) }
        syncNow()
    }

    fun dodge() {
        val s = _uiState.value
        if (s.phase != OnlineBoutPhase.FIGHTING || dodgeRemainingMs > 0L || s.myStamina < 12f) return
        dodgeRemainingMs = 360L
        myDodgeSeq += 1L
        if (soundEnabled()) soundEngine.playWhoosh()
        _uiState.update { it.copy(myStamina = (it.myStamina - 12f).coerceAtLeast(0f), myDodging = true) }
        syncNow(overrideDodgeSeq = myDodgeSeq)
    }

    fun throwPunch(punch: PunchType) {
        val s = _uiState.value
        if (s.phase != OnlineBoutPhase.FIGHTING || s.myActivePunch != null || s.myGuarding) return
        val cost = punch.staminaCost * s.myGlove.staminaCostMultiplier
        if (s.myStamina < cost) {
            triggerCallout("¡SIN ENERGÍA!", CalloutTone.CRIMSON)
            return
        }

        val isCounter = s.counterWindowMs > 0L
        val mult = if (isCounter) 1.75f else 1.0f
        val scaledDmg = (punch.damage * s.myGlove.damageMultiplier * mult).toInt().coerceIn(5, 65)
        val inRange = abs(s.myPosX - s.oppPosX) <= punch.maxRange
        val newHits = if (inRange && !s.oppDodging) s.myHitsLanded + 1 else s.myHitsLanded

        myPunchElapsedMs = 0L
        myPunchSeq += 1L
        soundEngine.soundEnabled = soundEnabled()
        soundEngine.hapticsEnabled = hapticsEnabled()
        if (inRange && !s.oppDodging) {
            soundEngine.playImpact(heavy = punch.damage >= 16f, counter = isCounter)
        } else {
            soundEngine.playWhoosh()
        }

        _uiState.update {
            it.copy(
                myActivePunch = punch,
                myPunchProgress = 0.05f,
                myStamina = (it.myStamina - cost).coerceAtLeast(0f),
                counterWindowMs = 0L,
                myHitsLanded = newHits,
                oppHitFlashAlpha = if (inRange && !s.oppDodging) 0.45f else 0f
            )
        }
        if (isCounter) triggerCallout("¡CONTRAATAQUE!", CalloutTone.GOLD)
        syncNow(punchType = punch.name, punchDamage = scaledDmg)
    }

    private fun finishByPoints() {
        val match = currentMatch ?: return
        val s = _uiState.value
        val myScore = s.myHp + s.myHitsLanded * 3f
        val oppScore = s.oppHp + s.oppHitsLanded * 3f
        val oppUid = if (match.hostUid == currentUserId) match.guestUid else match.hostUid
        val winnerUid = if (myScore >= oppScore || oppUid == null) currentUserId else oppUid
        viewModelScope.launch {
            repository.finishMatch(
                matchId = match.roomCode,
                winnerUid = winnerUid,
                winMethod = "PUNTOS",
                hostHp = if (s.isHost) s.myHp.toInt() else s.oppHp.toInt(),
                guestHp = if (s.isHost) s.oppHp.toInt() else s.myHp.toInt(),
                hostKnockdowns = if (s.isHost) myKnockdowns else oppKnockdowns,
                guestKnockdowns = if (s.isHost) oppKnockdowns else myKnockdowns
            )
        }
    }

    fun cancelOrForfeitAndExit(onDone: () -> Unit) {
        val match = currentMatch
        val s = _uiState.value
        viewModelScope.launch {
            if (match != null) {
                when (s.phase) {
                    OnlineBoutPhase.WAITING_OPPONENT -> {
                        if (match.hostUid == currentUserId) {
                            repository.cancelWaitingMatch(match.roomCode)
                        }
                    }
                    OnlineBoutPhase.PRESENTATION, OnlineBoutPhase.FIGHTING -> {
                        val oppUid = if (match.hostUid == currentUserId) match.guestUid else match.hostUid
                        if (oppUid != null) {
                            repository.finishMatch(
                                matchId = match.roomCode,
                                winnerUid = oppUid,
                                winMethod = "ABANDONO",
                                hostHp = if (s.isHost) s.myHp.toInt() else s.oppHp.toInt(),
                                guestHp = if (s.isHost) s.oppHp.toInt() else s.myHp.toInt(),
                                hostKnockdowns = if (s.isHost) myKnockdowns else oppKnockdowns,
                                guestKnockdowns = if (s.isHost) oppKnockdowns else myKnockdowns
                            )
                        }
                    }
                    else -> Unit
                }
            }
            stopMatch()
            onDone()
        }
    }

    private fun syncNow(
        punchType: String = "NONE",
        punchDamage: Int = 0,
        overrideDodgeSeq: Long = myDodgeSeq
    ) {
        val match = currentMatch ?: return
        if (match.status != "ACTIVE") return
        lastSyncTimeMs = System.currentTimeMillis()
        val s = _uiState.value
        viewModelScope.launch {
            repository.syncPlayerCombatState(
                matchId = match.roomCode,
                isHost = s.isHost,
                hp = s.myHp.toInt(),
                stamina = s.myStamina.toInt(),
                posX = s.myPosX.toDouble(),
                guarding = s.myGuarding,
                dodgeSeq = overrideDodgeSeq,
                punchType = punchType,
                punchSeq = myPunchSeq,
                punchDamage = punchDamage,
                hitsLanded = s.myHitsLanded,
                knockdowns = myKnockdowns
            )
        }
    }

    private fun triggerCallout(text: String, tone: CalloutTone) {
        calloutRemainingMs = 950L
        _uiState.update { it.copy(calloutText = text, calloutTone = tone) }
    }

    fun stopMatch() {
        observeJob?.cancel()
        loopJob?.cancel()
        observeJob = null
        loopJob = null
        moveDir = 0f
    }

    override fun onCleared() {
        super.onCleared()
        stopMatch()
    }
}
