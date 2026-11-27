package com.example.wifi

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.BoxingSoundEngine
import com.example.model.CalloutTone
import com.example.model.GloveShopCatalog
import com.example.model.PunchType
import com.example.online.OnlineBoutPhase
import com.example.online.OnlineFightUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs

class WifiFightViewModel(
    context: Context,
    private val soundEngine: BoxingSoundEngine,
    private val soundEnabled: () -> Boolean,
    private val hapticsEnabled: () -> Boolean
) : ViewModel() {

    private val transport = WifiMatchTransport(context, viewModelScope)

    private val _uiState = MutableStateFlow(OnlineFightUiState(isWifiLocal = true))
    val uiState: StateFlow<OnlineFightUiState> = _uiState.asStateFlow()

    private var loopJob: Job? = null
    private var startedKey: String = ""

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
    private var lastSyncedOppPunchSeq: Long = 0L
    private var lastSyncedOppDodgeSeq: Long = 0L
    private var lastSyncTimeMs: Long = 0L

    fun startHost(
        myName: String,
        myGloveId: String,
        rounds: Int
    ) {
        val key = "host:$myName:$myGloveId:$rounds"
        if (startedKey == key && _uiState.value.phase != OnlineBoutPhase.ERROR) return
        resetCounters(key)

        val myGlove = GloveShopCatalog.byId(myGloveId)
        _uiState.value = OnlineFightUiState(
            matchId = "WI-FI",
            phase = OnlineBoutPhase.LOADING,
            isHost = true,
            myName = myName,
            oppName = "Esperando rival...",
            myGlove = myGlove,
            maxRounds = rounds,
            myPosX = -0.35f,
            oppPosX = 0.35f,
            isWifiLocal = true
        )

        transport.startHost(
            hostName = myName,
            hostGloveId = myGloveId,
            rounds = rounds,
            onWaiting = { ips, port ->
                val hint = if (ips.isEmpty()) {
                    "Puerto $port (conéctate a un Wi-Fi o Zona Wi-Fi)"
                } else {
                    ips.joinToString("\n") { "$it : $port" }
                }
                _uiState.update {
                    it.copy(
                        phase = OnlineBoutPhase.WAITING_OPPONENT,
                        wifiAddressHint = hint
                    )
                }
            },
            onConnected = { oppName, oppGloveId ->
                enterPresentation(
                    oppName = oppName,
                    oppGloveId = oppGloveId,
                    rounds = rounds
                )
            },
            onPacket = ::handlePacket,
            onError = { msg ->
                loopJob?.cancel()
                _uiState.update {
                    it.copy(phase = OnlineBoutPhase.ERROR, errorMessage = msg)
                }
            },
            onDisconnected = ::handlePeerDisconnected
        )
    }

    fun startGuest(
        hostIp: String,
        port: Int,
        myName: String,
        myGloveId: String
    ) {
        val key = "guest:$hostIp:$port:$myName:$myGloveId"
        if (startedKey == key && _uiState.value.phase != OnlineBoutPhase.ERROR) return
        resetCounters(key)

        val myGlove = GloveShopCatalog.byId(myGloveId)
        _uiState.value = OnlineFightUiState(
            matchId = "WI-FI",
            phase = OnlineBoutPhase.LOADING,
            isHost = false,
            myName = myName,
            oppName = "Conectando...",
            myGlove = myGlove,
            myPosX = 0.35f,
            oppPosX = -0.35f,
            isWifiLocal = true,
            wifiAddressHint = "$hostIp:$port"
        )

        transport.startGuest(
            hostIp = hostIp,
            port = port,
            guestName = myName,
            guestGloveId = myGloveId,
            onConnected = { oppName, oppGloveId, rounds ->
                enterPresentation(
                    oppName = oppName,
                    oppGloveId = oppGloveId,
                    rounds = rounds
                )
            },
            onPacket = ::handlePacket,
            onError = { msg ->
                loopJob?.cancel()
                _uiState.update {
                    it.copy(phase = OnlineBoutPhase.ERROR, errorMessage = msg)
                }
            },
            onDisconnected = ::handlePeerDisconnected
        )
    }

    private fun resetCounters(newKey: String) {
        loopJob?.cancel()
        loopJob = null
        startedKey = newKey
        moveDir = 0f
        dodgeRemainingMs = 0L
        oppDodgeRemainingMs = 0L
        calloutRemainingMs = 0L
        presentationRemainingMs = 0L
        myPunchElapsedMs = 0L
        oppPunchElapsedMs = 0L
        myPunchSeq = 0L
        myDodgeSeq = 0L
        lastSyncedOppPunchSeq = 0L
        lastSyncedOppDodgeSeq = 0L
        lastSyncTimeMs = 0L
    }

    private fun enterPresentation(
        oppName: String,
        oppGloveId: String,
        rounds: Int
    ) {
        presentationRemainingMs = 2600L
        if (soundEnabled()) soundEngine.playPresentationFanfare()
        _uiState.update {
            it.copy(
                phase = OnlineBoutPhase.PRESENTATION,
                oppName = oppName,
                oppGlove = GloveShopCatalog.byId(oppGloveId),
                maxRounds = rounds,
                currentRound = 1,
                roundTimeMs = 35_000L,
                myHp = 150f,
                oppHp = 150f,
                myStamina = 100f,
                oppStamina = 100f
            )
        }
        startLoopIfNeeded()
    }

    private fun handlePacket(json: JSONObject) {
        when (json.optString("type")) {
            "START" -> {
                if (_uiState.value.phase == OnlineBoutPhase.PRESENTATION) {
                    presentationRemainingMs = 0L
                    if (soundEnabled()) soundEngine.playBell()
                    triggerCallout("¡A PELEAR!", CalloutTone.GOLD)
                    _uiState.update { it.copy(phase = OnlineBoutPhase.FIGHTING, roundTimeMs = 35_000L) }
                }
            }

            "STATE" -> {
                val oppHp = json.optDouble("hp", 150.0).toFloat()
                val oppStamina = json.optDouble("stamina", 100.0).toFloat()
                val oppPosX = json.optDouble("posX", _uiState.value.oppPosX.toDouble()).toFloat()
                val oppGuarding = json.optBoolean("guarding", false)
                val oppDodgeSeq = json.optLong("dodgeSeq", 0L)
                val oppPunchTypeRaw = json.optString("punchType", "NONE")
                val oppPunchSeq = json.optLong("punchSeq", 0L)
                val oppPunchDamage = json.optDouble("punchDamage", 0.0).toFloat()
                val oppHits = json.optInt("hitsLanded", _uiState.value.oppHitsLanded)

                _uiState.update { state ->
                    val syncedRound = if (!state.isHost) json.optInt("round", state.currentRound) else state.currentRound
                    val syncedTime = if (!state.isHost) json.optLong("roundTimeMs", state.roundTimeMs) else state.roundTimeMs
                    state.copy(
                        oppHp = oppHp,
                        oppStamina = oppStamina,
                        oppPosX = oppPosX,
                        oppGuarding = oppGuarding,
                        oppHitsLanded = oppHits,
                        currentRound = syncedRound,
                        roundTimeMs = syncedTime
                    )
                }

                if (oppDodgeSeq > lastSyncedOppDodgeSeq) {
                    lastSyncedOppDodgeSeq = oppDodgeSeq
                    oppDodgeRemainingMs = 340L
                }

                if (oppPunchSeq > lastSyncedOppPunchSeq) {
                    lastSyncedOppPunchSeq = oppPunchSeq
                    val punch = PunchType.entries.firstOrNull { it.name == oppPunchTypeRaw }
                    if (punch != null) {
                        resolveIncomingOpponentPunch(punch, oppPunchDamage, oppPosX)
                    }
                }
            }

            "FINISH" -> {
                val winnerRole = json.optString("winnerRole", "HOST")
                val method = json.optString("winMethod", "KO")
                val hostHp = json.optDouble("hostHp", _uiState.value.myHp.toDouble()).toFloat()
                val guestHp = json.optDouble("guestHp", _uiState.value.oppHp.toDouble()).toFloat()
                applyFinished(winnerRole, method, hostHp, guestHp)
            }
        }
    }

    private fun handlePeerDisconnected() {
        val s = _uiState.value
        if (s.phase == OnlineBoutPhase.FINISHED || s.phase == OnlineBoutPhase.ERROR) return
        loopJob?.cancel()
        if (s.phase == OnlineBoutPhase.FIGHTING || s.phase == OnlineBoutPhase.PRESENTATION) {
            if (soundEnabled()) soundEngine.playCrowdCheer()
            _uiState.update {
                it.copy(
                    phase = OnlineBoutPhase.FINISHED,
                    winnerIsMe = true,
                    winMethod = "ABANDONO"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    phase = OnlineBoutPhase.ERROR,
                    errorMessage = "El rival se desconectó de la sala Wi-Fi."
                )
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
            _uiState.update {
                it.copy(
                    counterWindowMs = 750L,
                    crowdExcitement = (it.crowdExcitement + 0.14f).coerceAtMost(1f)
                )
            }
            return
        }

        val now = System.currentTimeMillis()
        if (s.myGuarding) {
            val parryWindow = (now - guardStartTimeMs) <= 280L
            if (parryWindow) {
                triggerCallout("¡PARRY! CONTRA 2x", CalloutTone.GOLD)
                if (soundEnabled()) soundEngine.playBlock(perfectParry = true)
                _uiState.update {
                    it.copy(
                        counterWindowMs = 900L,
                        crowdExcitement = (it.crowdExcitement + 0.18f).coerceAtMost(1f)
                    )
                }
            } else {
                val chip = (damage * (0.24f - s.myGlove.guardAbsorptionBonus)).coerceAtLeast(1f)
                val nextHp = (s.myHp - chip).coerceAtLeast(1f)
                if (soundEnabled()) soundEngine.playBlock()
                _uiState.update { it.copy(myHp = nextHp, hitFlashAlpha = 0.25f) }
                sendState()
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
            val winnerRole = if (s.isHost) "GUEST" else "HOST"
            val hostHp = if (s.isHost) 0f else s.oppHp
            val guestHp = if (s.isHost) s.oppHp else 0f
            sendFinish(winnerRole, "KO", hostHp, guestHp)
            applyFinished(winnerRole, "KO", hostHp, guestHp)
        } else {
            sendState()
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

                var nextRound = s.currentRound
                var nextRoundTime = (s.roundTimeMs - dt).coerceAtLeast(0L)
                if (s.isHost && nextRoundTime == 0L) {
                    if (s.currentRound < s.maxRounds) {
                        nextRound += 1
                        nextRoundTime = 35_000L
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
                        roundTimeMs = nextRoundTime
                    )
                }

                if (now - lastSyncTimeMs >= 65L && (moveDir != 0f || s.isHost)) {
                    sendState()
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
            transport.sendJson(JSONObject().apply { put("type", "START") })
        }
    }

    fun setMoveInput(dir: Float) {
        moveDir = dir.coerceIn(-1f, 1f)
        if (dir == 0f && _uiState.value.phase == OnlineBoutPhase.FIGHTING) {
            sendState()
        }
    }

    fun setGuard(active: Boolean) {
        val s = _uiState.value
        if (s.phase != OnlineBoutPhase.FIGHTING) return
        if (active && !s.myGuarding) guardStartTimeMs = System.currentTimeMillis()
        _uiState.update { it.copy(myGuarding = active) }
        sendState()
    }

    fun dodge() {
        val s = _uiState.value
        if (s.phase != OnlineBoutPhase.FIGHTING || dodgeRemainingMs > 0L || s.myStamina < 12f) return
        dodgeRemainingMs = 360L
        myDodgeSeq += 1L
        if (soundEnabled()) soundEngine.playWhoosh()
        _uiState.update { it.copy(myStamina = (it.myStamina - 12f).coerceAtLeast(0f), myDodging = true) }
        sendState()
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
        sendState(punchType = punch.name, punchDamage = scaledDmg)
    }

    private fun finishByPoints() {
        val s = _uiState.value
        val myScore = s.myHp + s.myHitsLanded * 3f
        val oppScore = s.oppHp + s.oppHitsLanded * 3f
        val hostWon = if (s.isHost) myScore >= oppScore else oppScore > myScore
        val winnerRole = if (hostWon) "HOST" else "GUEST"
        val hostHp = if (s.isHost) s.myHp else s.oppHp
        val guestHp = if (s.isHost) s.oppHp else s.myHp
        sendFinish(winnerRole, "PUNTOS", hostHp, guestHp)
        applyFinished(winnerRole, "PUNTOS", hostHp, guestHp)
    }

    private fun applyFinished(
        winnerRole: String,
        method: String,
        hostHp: Float,
        guestHp: Float
    ) {
        if (_uiState.value.phase == OnlineBoutPhase.FINISHED) return
        loopJob?.cancel()
        val isHost = _uiState.value.isHost
        val won = (winnerRole == "HOST" && isHost) || (winnerRole == "GUEST" && !isHost)
        if (soundEnabled()) {
            if (won) soundEngine.playCrowdCheer() else soundEngine.playBell()
        }
        _uiState.update {
            it.copy(
                phase = OnlineBoutPhase.FINISHED,
                myHp = if (isHost) hostHp else guestHp,
                oppHp = if (isHost) guestHp else hostHp,
                winnerIsMe = won,
                winMethod = method
            )
        }
    }

    private fun sendState(
        punchType: String = "NONE",
        punchDamage: Int = 0
    ) {
        val s = _uiState.value
        if (s.phase != OnlineBoutPhase.FIGHTING) return
        lastSyncTimeMs = System.currentTimeMillis()
        transport.sendJson(
            JSONObject().apply {
                put("type", "STATE")
                put("hp", s.myHp.toDouble())
                put("stamina", s.myStamina.toDouble())
                put("posX", s.myPosX.toDouble())
                put("guarding", s.myGuarding)
                put("dodgeSeq", myDodgeSeq)
                put("punchType", punchType)
                put("punchSeq", myPunchSeq)
                put("punchDamage", punchDamage.toDouble())
                put("hitsLanded", s.myHitsLanded)
                put("round", s.currentRound)
                put("roundTimeMs", s.roundTimeMs)
            }
        )
    }

    private fun sendFinish(
        winnerRole: String,
        method: String,
        hostHp: Float,
        guestHp: Float
    ) {
        transport.sendJson(
            JSONObject().apply {
                put("type", "FINISH")
                put("winnerRole", winnerRole)
                put("winMethod", method)
                put("hostHp", hostHp.toDouble())
                put("guestHp", guestHp.toDouble())
            }
        )
    }

    fun cancelOrForfeitAndExit(onDone: () -> Unit) {
        val s = _uiState.value
        viewModelScope.launch {
            if (s.phase == OnlineBoutPhase.PRESENTATION || s.phase == OnlineBoutPhase.FIGHTING) {
                val winnerRole = if (s.isHost) "GUEST" else "HOST"
                val hostHp = if (s.isHost) s.myHp else s.oppHp
                val guestHp = if (s.isHost) s.oppHp else s.myHp
                sendFinish(winnerRole, "ABANDONO", hostHp, guestHp)
                delay(120L)
            }
            stopMatch()
            onDone()
        }
    }

    private fun triggerCallout(text: String, tone: CalloutTone) {
        calloutRemainingMs = 950L
        _uiState.update { it.copy(calloutText = text, calloutTone = tone) }
    }

    fun stopMatch() {
        loopJob?.cancel()
        loopJob = null
        moveDir = 0f
        transport.close()
    }

    override fun onCleared() {
        super.onCleared()
        stopMatch()
    }
}
