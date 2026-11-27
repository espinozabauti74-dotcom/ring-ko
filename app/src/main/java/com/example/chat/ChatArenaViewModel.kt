package com.example.chat

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.BoxingSoundEngine
import com.example.model.BoxerAppearance
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

class ChatArenaViewModel(
    private val soundEngine: BoxingSoundEngine,
    private val soundEnabled: () -> Boolean,
    private val hapticsEnabled: () -> Boolean
) : ViewModel() {

    val myPeerId: String = "p_${UUID.randomUUID().toString().replace("-", "").take(10)}"

    private val relay = MqttWebSocketRelay(
        scope = viewModelScope,
        clientId = "ringko_$myPeerId"
    )

    private val _chatState = MutableStateFlow(ChatRoomUiState(myPeerId = myPeerId))
    val chatState: StateFlow<ChatRoomUiState> = _chatState.asStateFlow()

    private val _fightState = MutableStateFlow(OnlineFightUiState())
    val fightState: StateFlow<OnlineFightUiState> = _fightState.asStateFlow()

    private var presenceJob: Job? = null
    private var fightLoopJob: Job? = null

    private var currentPlayerName: String = "Retador"
    private var currentGloveId: String = "classic_red"
    private var currentAppearance: BoxerAppearance = BoxerAppearance()

    private var activeOpponentPeerId: String? = null
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

    fun syncPlayerConfig(
        playerName: String,
        gloveId: String,
        appearance: BoxerAppearance
    ) {
        val changed = currentPlayerName != playerName ||
            currentGloveId != gloveId ||
            currentAppearance != appearance
        currentPlayerName = playerName.trim().ifBlank { "Retador" }
        currentGloveId = gloveId
        currentAppearance = appearance
        if (changed && _chatState.value.isConnected) {
            broadcastPresence("PRESENCE")
        }
    }

    fun createChatRoom(
        roomTitle: String,
        playerName: String,
        gloveId: String,
        appearance: BoxerAppearance
    ) {
        syncPlayerConfig(playerName, gloveId, appearance)
        val code = ChatCodeGenerator.generateRoomCode(6)
        val cleanTitle = roomTitle.trim().ifBlank { "Sala de $currentPlayerName" }
        joinRoomInternal(code = code, title = cleanTitle, isCreator = true)
    }

    fun joinChatRoom(
        rawCode: String,
        playerName: String,
        gloveId: String,
        appearance: BoxerAppearance
    ) {
        syncPlayerConfig(playerName, gloveId, appearance)
        val normalized = ChatCodeGenerator.normalizeCode(rawCode)
        if (normalized.length < 4) {
            _chatState.update {
                it.copy(errorMessage = "Ingresa un código válido de al menos 4 caracteres.")
            }
            return
        }
        joinRoomInternal(code = normalized, title = "Sala $normalized", isCreator = false)
    }

    private fun joinRoomInternal(code: String, title: String, isCreator: Boolean) {
        leaveChatRoom(silent = true)

        val me = ChatCodeGenerator.buildLocalMember(
            peerId = myPeerId,
            playerName = currentPlayerName,
            gloveId = currentGloveId,
            characterId = _chatState.value.selectedCharacterId,
            appearance = currentAppearance
        )

        _chatState.update {
            ChatRoomUiState(
                roomCode = code,
                roomTitle = title,
                myPeerId = myPeerId,
                isConnected = false,
                isConnecting = true,
                connectionLabel = "Conectando...",
                selectedCharacterId = it.selectedCharacterId,
                selectedRounds = it.selectedRounds,
                members = listOf(me),
                messages = listOf(
                    ChatRoomMessage(
                        id = "sys_welcome_${System.currentTimeMillis()}",
                        senderId = "system",
                        senderName = "Ring KO",
                        text = if (isCreator) {
                            "Chat creado. Comparte el código $code con tus amigos para chatear y pelear online sin cuenta."
                        } else {
                            "Entrando a la sala $code sin necesidad de cuenta..."
                        },
                        timestampMs = System.currentTimeMillis(),
                        isSystem = true
                    )
                )
            )
        }

        val topic = "ringko/v1/room/$code"
        relay.connect(
            topic = topic,
            onState = { connected, label ->
                _chatState.update {
                    it.copy(
                        isConnected = connected,
                        isConnecting = !connected,
                        connectionLabel = label,
                        errorMessage = if (connected) null else it.errorMessage
                    )
                }
                if (connected) {
                    broadcastPresence("JOIN")
                    startPresenceLoop()
                }
            },
            onMessage = { _, payload ->
                runCatching { JSONObject(payload) }.getOrNull()?.let { json ->
                    handleProtocolJson(json)
                }
            },
            onError = { err ->
                _chatState.update { it.copy(errorMessage = err) }
            }
        )
    }

    fun selectCharacter(characterId: String) {
        _chatState.update { it.copy(selectedCharacterId = characterId) }
        if (_chatState.value.roomCode.isNotEmpty()) {
            val me = localMember()
            upsertMember(me)
            if (_chatState.value.isConnected) {
                broadcastPresence("PRESENCE")
            }
        }
    }

    fun selectRounds(rounds: Int) {
        val validRounds = if (rounds >= 10) 10 else 3
        _chatState.update { it.copy(selectedRounds = validRounds) }
    }

    fun sendChatMessage(rawText: String) {
        val text = rawText.trim()
        val state = _chatState.value
        if (text.isEmpty() || state.roomCode.isEmpty()) return

        val me = localMember()
        val msgId = "msg_${myPeerId}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
        val now = System.currentTimeMillis()
        val message = ChatRoomMessage(
            id = msgId,
            senderId = myPeerId,
            senderName = me.name,
            text = text.take(280),
            timestampMs = now,
            isSystem = false
        )
        appendMessage(message)

        val json = JSONObject().apply {
            put("v", 1)
            put("type", "CHAT")
            put("senderId", myPeerId)
            put("msgId", message.id)
            put("senderName", message.senderName)
            put("text", message.text)
            put("ts", now)
        }
        if (!relay.publish(json.toString())) {
            _chatState.update {
                it.copy(errorMessage = "Esperando conexión para enviar mensajes...")
            }
        }
    }

    fun sendChallenge(targetPeerId: String? = null, targetName: String? = null) {
        val state = _chatState.value
        if (state.roomCode.isEmpty() || !state.isConnected) {
            _chatState.update { it.copy(errorMessage = "Conéctate a la sala antes de enviar un reto.") }
            return
        }
        val me = localMember()
        val challengeId = "ch_${myPeerId}_${System.currentTimeMillis()}"
        val rounds = state.selectedRounds

        _chatState.update {
            it.copy(
                outgoingChallengeId = challengeId,
                outgoingChallengeTargetName = targetName ?: "la sala",
                errorMessage = null
            )
        }

        appendSystemMessage(
            if (targetName != null) {
                "Retaste a $targetName a un combate 1v1 ($rounds rounds)."
            } else {
                "Lanzaste un reto abierto a $rounds rounds. Esperando rival..."
            }
        )

        val json = JSONObject().apply {
            put("v", 1)
            put("type", "CHALLENGE")
            put("senderId", myPeerId)
            put("challengeId", challengeId)
            put("fromName", me.name)
            put("toPeerId", targetPeerId ?: "")
            put("rounds", rounds)
            putProfile(me)
        }
        relay.publish(json.toString())
    }

    fun cancelOutgoingChallenge() {
        val id = _chatState.value.outgoingChallengeId ?: return
        _chatState.update {
            it.copy(outgoingChallengeId = null, outgoingChallengeTargetName = null)
        }
        relay.publish(
            JSONObject().apply {
                put("v", 1)
                put("type", "CANCEL_CHALLENGE")
                put("senderId", myPeerId)
                put("challengeId", id)
            }.toString()
        )
    }

    fun acceptIncomingChallenge() {
        val challenge = _chatState.value.incomingChallenge ?: return
        val boutId = "bout_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
        val me = localMember()

        val json = JSONObject().apply {
            put("v", 1)
            put("type", "ACCEPT")
            put("senderId", myPeerId)
            put("challengeId", challenge.challengeId)
            put("boutId", boutId)
            put("hostPeerId", challenge.fromPeerId)
            put("guestPeerId", myPeerId)
            put("rounds", challenge.rounds)
            putProfile(me)
        }
        relay.publish(json.toString())

        val oppMember = ChatRoomMember(
            peerId = challenge.fromPeerId,
            name = challenge.fromName,
            characterId = challenge.characterId,
            gloveId = challenge.gloveId,
            skinArgb = challenge.skinArgb,
            hairArgb = challenge.hairArgb,
            trunksArgb = challenge.trunksArgb,
            gloveArgb = challenge.gloveArgb,
            trimArgb = challenge.trimArgb,
            lastSeenMs = System.currentTimeMillis()
        )
        _chatState.update {
            it.copy(
                incomingChallenge = null,
                outgoingChallengeId = null,
                outgoingChallengeTargetName = null
            )
        }
        startBout(
            boutId = boutId,
            isHost = false,
            myProfile = me,
            oppProfile = oppMember,
            rounds = challenge.rounds
        )
    }

    fun declineIncomingChallenge() {
        val challenge = _chatState.value.incomingChallenge ?: return
        val me = localMember()
        _chatState.update { it.copy(incomingChallenge = null) }
        relay.publish(
            JSONObject().apply {
                put("v", 1)
                put("type", "DECLINE")
                put("senderId", myPeerId)
                put("challengeId", challenge.challengeId)
                put("fromName", me.name)
            }.toString()
        )
    }

    internal fun handleProtocolJson(json: JSONObject) {
        val senderId = json.optString("senderId", "")
        if (senderId.isBlank() || senderId == myPeerId) return

        when (json.optString("type")) {
            "JOIN", "PRESENCE" -> {
                val member = json.toMember(senderId) ?: return
                val wasPresent = _chatState.value.members.any { it.peerId == senderId }
                upsertMember(member)
                val incomingTitle = json.optString("roomTitle", "")
                if (incomingTitle.isNotBlank() && json.optString("type") == "JOIN") {
                    _chatState.update { state ->
                        if (state.roomTitle.startsWith("Sala ")) state.copy(roomTitle = incomingTitle)
                        else state
                    }
                }
                if (!wasPresent) {
                    appendSystemMessage("${member.name} se unió al chat.")
                    if (_chatState.value.isConnected) {
                        broadcastPresence("PRESENCE")
                    }
                }
            }

            "LEAVE" -> {
                val leftName = _chatState.value.members.firstOrNull { it.peerId == senderId }?.name
                _chatState.update { state ->
                    state.copy(
                        members = state.members.filterNot { it.peerId == senderId },
                        incomingChallenge = if (state.incomingChallenge?.fromPeerId == senderId) null else state.incomingChallenge
                    )
                }
                if (leftName != null) {
                    appendSystemMessage("$leftName salió del chat.")
                }
            }

            "CHAT" -> {
                val msgId = json.optString("msgId", UUID.randomUUID().toString())
                val senderName = json.optString("senderName", "Amigo")
                val text = json.optString("text", "")
                val ts = json.optLong("ts", System.currentTimeMillis())
                if (text.isNotBlank()) {
                    appendMessage(
                        ChatRoomMessage(
                            id = msgId,
                            senderId = senderId,
                            senderName = senderName,
                            text = text,
                            timestampMs = ts,
                            isSystem = false
                        )
                    )
                }
            }

            "CHALLENGE" -> {
                val toPeerId = json.optString("toPeerId", "").ifBlank { null }
                if (toPeerId != null && toPeerId != myPeerId) return
                val member = json.toMember(senderId) ?: return
                upsertMember(member)
                val challenge = ChatChallenge(
                    challengeId = json.optString("challengeId", ""),
                    fromPeerId = senderId,
                    fromName = member.name,
                    toPeerId = toPeerId,
                    rounds = json.optInt("rounds", 3).coerceIn(3, 10),
                    characterId = member.characterId,
                    gloveId = member.gloveId,
                    skinArgb = member.skinArgb,
                    hairArgb = member.hairArgb,
                    trunksArgb = member.trunksArgb,
                    gloveArgb = member.gloveArgb,
                    trimArgb = member.trimArgb
                )
                _chatState.update { it.copy(incomingChallenge = challenge) }
                appendSystemMessage("${member.name} lanzó un reto a ${challenge.rounds} rounds.")
            }

            "CANCEL_CHALLENGE" -> {
                val cid = json.optString("challengeId", "")
                _chatState.update { state ->
                    if (state.incomingChallenge?.challengeId == cid) {
                        state.copy(incomingChallenge = null)
                    } else state
                }
            }

            "DECLINE" -> {
                val cid = json.optString("challengeId", "")
                val fromName = json.optString("fromName", "Tu amigo")
                if (_chatState.value.outgoingChallengeId == cid) {
                    _chatState.update {
                        it.copy(outgoingChallengeId = null, outgoingChallengeTargetName = null)
                    }
                    appendSystemMessage("$fromName rechazó el reto.")
                }
            }

            "ACCEPT" -> {
                val hostPeerId = json.optString("hostPeerId", "")
                val guestPeerId = json.optString("guestPeerId", "")
                val boutId = json.optString("boutId", "")
                if (hostPeerId != myPeerId || boutId.isBlank()) return
                val oppProfile = json.toMember(guestPeerId) ?: return
                val rounds = json.optInt("rounds", _chatState.value.selectedRounds).coerceIn(3, 10)
                upsertMember(oppProfile)
                _chatState.update {
                    it.copy(
                        outgoingChallengeId = null,
                        outgoingChallengeTargetName = null,
                        incomingChallenge = null
                    )
                }
                appendSystemMessage("${oppProfile.name} aceptó el reto. ¡Al ring!")
                startBout(
                    boutId = boutId,
                    isHost = true,
                    myProfile = localMember(),
                    oppProfile = oppProfile,
                    rounds = rounds
                )
            }

            "BOUT_START" -> {
                val boutId = json.optString("boutId", "")
                if (boutId != _chatState.value.activeBoutId) return
                if (_fightState.value.phase == OnlineBoutPhase.PRESENTATION) {
                    presentationRemainingMs = 0L
                    syncSoundPrefs()
                    soundEngine.playBell()
                    soundEngine.announceReferee("¡Round 1, peleen!")
                    triggerCallout("¡A PELEAR!", CalloutTone.GOLD)
                    _fightState.update { it.copy(phase = OnlineBoutPhase.FIGHTING, roundTimeMs = 35_000L) }
                }
            }

            "BOUT_STATE" -> {
                val boutId = json.optString("boutId", "")
                if (boutId != _chatState.value.activeBoutId) return
                handleOpponentBoutState(json)
            }

            "BOUT_FINISH" -> {
                val boutId = json.optString("boutId", "")
                if (boutId != _chatState.value.activeBoutId) return
                val winnerRole = json.optString("winnerRole", "HOST")
                val method = json.optString("winMethod", "KO")
                val hostHp = json.optDouble("hostHp", _fightState.value.myHp.toDouble()).toFloat()
                val guestHp = json.optDouble("guestHp", _fightState.value.oppHp.toDouble()).toFloat()
                applyBoutFinished(winnerRole, method, hostHp, guestHp)
            }
        }
    }

    private fun startBout(
        boutId: String,
        isHost: Boolean,
        myProfile: ChatRoomMember,
        oppProfile: ChatRoomMember,
        rounds: Int
    ) {
        resetBoutCounters()
        activeOpponentPeerId = oppProfile.peerId
        presentationRemainingMs = 2600L

        syncSoundPrefs()
        soundEngine.playPresentationFanfare()
        soundEngine.announceReferee("${myProfile.name} contra ${oppProfile.name}, ¡combate a $rounds rounds!")

        val myGlove = GloveShopCatalog.byId(myProfile.gloveId)
        val oppGlove = GloveShopCatalog.byId(oppProfile.gloveId)

        _fightState.value = OnlineFightUiState(
            matchId = _chatState.value.roomCode,
            phase = OnlineBoutPhase.PRESENTATION,
            isHost = isHost,
            myName = myProfile.name,
            oppName = oppProfile.name,
            myGlove = myGlove,
            oppGlove = oppGlove,
            maxRounds = rounds,
            currentRound = 1,
            roundTimeMs = 35_000L,
            myHp = 150f,
            myMaxHp = 150f,
            oppHp = 150f,
            oppMaxHp = 150f,
            myStamina = 100f,
            oppStamina = 100f,
            myPosX = if (isHost) -0.35f else 0.35f,
            oppPosX = if (isHost) 0.35f else -0.35f,
            isWifiLocal = false,
            mySkinColor = Color(myProfile.skinArgb),
            myHairColor = Color(myProfile.hairArgb),
            myTrunksColor = Color(myProfile.trunksArgb),
            myGloveColorOverride = Color(myProfile.gloveArgb),
            myGloveTrimOverride = Color(myProfile.trimArgb),
            oppSkinColor = Color(oppProfile.skinArgb),
            oppHairColor = Color(oppProfile.hairArgb),
            oppTrunksColor = Color(oppProfile.trunksArgb),
            oppGloveColorOverride = Color(oppProfile.gloveArgb)
        )

        _chatState.update { it.copy(activeBoutId = boutId) }
        startBoutLoop()
    }

    private fun handleOpponentBoutState(json: JSONObject) {
        val oppHp = json.optDouble("hp", 150.0).toFloat()
        val oppStamina = json.optDouble("stamina", 100.0).toFloat()
        val oppPosX = json.optDouble("posX", _fightState.value.oppPosX.toDouble()).toFloat()
        val oppGuarding = json.optBoolean("guarding", false)
        val oppDodgeSeq = json.optLong("dodgeSeq", 0L)
        val oppPunchTypeRaw = json.optString("punchType", "NONE")
        val oppPunchSeq = json.optLong("punchSeq", 0L)
        val oppPunchDamage = json.optDouble("punchDamage", 0.0).toFloat()
        val oppHits = json.optInt("hitsLanded", _fightState.value.oppHitsLanded)

        _fightState.update { state ->
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

    private fun resolveIncomingOpponentPunch(
        punch: PunchType,
        damage: Float,
        oppPosX: Float
    ) {
        oppPunchElapsedMs = 0L
        _fightState.update { it.copy(oppActivePunch = punch, oppPunchProgress = 0.05f) }
        syncSoundPrefs()
        soundEngine.playPunchThrow(punch)

        val s = _fightState.value
        if (s.phase != OnlineBoutPhase.FIGHTING) return

        val dist = abs(s.myPosX - oppPosX)
        if (dist > punch.maxRange) return

        if (dodgeRemainingMs > 0L) {
            triggerCallout("¡ESQUIVA PERFECTA!", CalloutTone.EMERALD)
            soundEngine.playDodge(perfect = true)
            _fightState.update {
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
                soundEngine.playBlock(perfectParry = true)
                _fightState.update {
                    it.copy(
                        counterWindowMs = 900L,
                        crowdExcitement = (it.crowdExcitement + 0.18f).coerceAtMost(1f)
                    )
                }
            } else {
                val chip = (damage * (0.24f - s.myGlove.guardAbsorptionBonus)).coerceAtLeast(1f)
                val nextHp = (s.myHp - chip).coerceAtLeast(1f)
                soundEngine.playBlock(perfectParry = false, heavy = punch != PunchType.JAB)
                _fightState.update { it.copy(myHp = nextHp, hitFlashAlpha = 0.25f) }
                sendBoutState()
            }
            return
        }

        val nextHp = (s.myHp - damage).coerceAtLeast(0f)
        soundEngine.playImpact(heavy = damage >= 15f, punchType = punch)
        _fightState.update {
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
            sendBoutFinish(winnerRole, "KO", hostHp, guestHp)
            applyBoutFinished(winnerRole, "KO", hostHp, guestHp)
        } else {
            sendBoutState()
        }
    }

    private fun startBoutLoop() {
        fightLoopJob?.cancel()
        fightLoopJob = viewModelScope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive) {
                delay(16L)
                val now = System.currentTimeMillis()
                val dt = (now - lastTick).coerceIn(1L, 64L)
                lastTick = now
                tickBout(dt, now)
            }
        }
    }

    private fun tickBout(dt: Long, now: Long) {
        val s = _fightState.value
        when (s.phase) {
            OnlineBoutPhase.PRESENTATION -> {
                presentationRemainingMs -= dt
                if (presentationRemainingMs <= 0L) {
                    syncSoundPrefs()
                    soundEngine.playBell()
                    soundEngine.announceReferee("¡Round 1, peleen!")
                    triggerCallout("¡ROUND 1 — PELEEN!", CalloutTone.GOLD)
                    _fightState.update { it.copy(phase = OnlineBoutPhase.FIGHTING, roundTimeMs = 35_000L) }
                }
            }

            OnlineBoutPhase.FIGHTING -> {
                if (dodgeRemainingMs > 0L) dodgeRemainingMs = (dodgeRemainingMs - dt).coerceAtLeast(0L)
                if (oppDodgeRemainingMs > 0L) oppDodgeRemainingMs = (oppDodgeRemainingMs - dt).coerceAtLeast(0L)
                if (calloutRemainingMs > 0L) {
                    calloutRemainingMs = (calloutRemainingMs - dt).coerceAtLeast(0L)
                    if (calloutRemainingMs == 0L) _fightState.update { it.copy(calloutText = "") }
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
                        syncSoundPrefs()
                        soundEngine.playBell()
                        soundEngine.announceReferee("¡Round $nextRound!")
                        triggerCallout("¡ROUND $nextRound!", CalloutTone.GOLD)
                    } else {
                        finishBoutByPoints()
                        return
                    }
                }

                _fightState.update {
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

                if (now - lastSyncTimeMs >= 95L && (moveDir != 0f || s.isHost)) {
                    sendBoutState()
                }
            }

            else -> Unit
        }
    }

    fun skipPresentation() {
        val boutId = _chatState.value.activeBoutId ?: return
        if (_fightState.value.phase == OnlineBoutPhase.PRESENTATION) {
            presentationRemainingMs = 0L
            syncSoundPrefs()
            soundEngine.playBell()
            soundEngine.announceReferee("¡Round 1, peleen!")
            triggerCallout("¡A PELEAR!", CalloutTone.GOLD)
            _fightState.update { it.copy(phase = OnlineBoutPhase.FIGHTING, roundTimeMs = 35_000L) }
            relay.publish(
                JSONObject().apply {
                    put("v", 1)
                    put("type", "BOUT_START")
                    put("senderId", myPeerId)
                    put("boutId", boutId)
                }.toString()
            )
        }
    }

    fun setMoveInput(dir: Float) {
        moveDir = dir.coerceIn(-1f, 1f)
        if (dir == 0f && _fightState.value.phase == OnlineBoutPhase.FIGHTING) {
            sendBoutState()
        }
    }

    fun setGuard(active: Boolean) {
        val s = _fightState.value
        if (s.phase != OnlineBoutPhase.FIGHTING) return
        if (active && !s.myGuarding) {
            guardStartTimeMs = System.currentTimeMillis()
            syncSoundPrefs()
            soundEngine.playGuardUp()
        }
        _fightState.update { it.copy(myGuarding = active) }
        sendBoutState()
    }

    fun dodge() {
        val s = _fightState.value
        if (s.phase != OnlineBoutPhase.FIGHTING || dodgeRemainingMs > 0L || s.myStamina < 12f) return
        dodgeRemainingMs = 360L
        myDodgeSeq += 1L
        syncSoundPrefs()
        soundEngine.playDodge(perfect = false)
        _fightState.update { it.copy(myStamina = (it.myStamina - 12f).coerceAtLeast(0f), myDodging = true) }
        sendBoutState()
    }

    fun throwPunch(punch: PunchType) {
        val s = _fightState.value
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
        syncSoundPrefs()
        soundEngine.playPunchThrow(punch)
        if (inRange && !s.oppDodging) {
            if (s.oppGuarding && punch.breaksGuardFactor < 0.5f) {
                soundEngine.playBlock(perfectParry = false, heavy = punch != PunchType.JAB)
            } else {
                soundEngine.playImpact(heavy = punch.damage >= 16f, counter = isCounter, punchType = punch)
            }
        }

        _fightState.update {
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
        sendBoutState(punchType = punch.name, punchDamage = scaledDmg)
    }

    private fun finishBoutByPoints() {
        val s = _fightState.value
        val myScore = s.myHp + s.myHitsLanded * 3f
        val oppScore = s.oppHp + s.oppHitsLanded * 3f
        val hostWon = if (s.isHost) myScore >= oppScore else oppScore > myScore
        val winnerRole = if (hostWon) "HOST" else "GUEST"
        val hostHp = if (s.isHost) s.myHp else s.oppHp
        val guestHp = if (s.isHost) s.oppHp else s.myHp
        sendBoutFinish(winnerRole, "PUNTOS", hostHp, guestHp)
        applyBoutFinished(winnerRole, "PUNTOS", hostHp, guestHp)
    }

    private fun applyBoutFinished(
        winnerRole: String,
        method: String,
        hostHp: Float,
        guestHp: Float
    ) {
        if (_fightState.value.phase == OnlineBoutPhase.FINISHED) return
        fightLoopJob?.cancel()
        val isHost = _fightState.value.isHost
        val won = (winnerRole == "HOST" && isHost) || (winnerRole == "GUEST" && !isHost)
        syncSoundPrefs()
        if (method == "KO") soundEngine.playKnockdown()
        soundEngine.playBell(doubleRing = true)
        if (won) soundEngine.playCrowdCheer()
        val winnerName = if (won) _fightState.value.myName else _fightState.value.oppName
        val verdict = if (method == "KO") "¡Nocaut! ¡Victoria para $winnerName!" else "¡Decisión por puntos! ¡Ganador, $winnerName!"
        soundEngine.announceReferee(verdict)
        _fightState.update {
            it.copy(
                phase = OnlineBoutPhase.FINISHED,
                myHp = if (isHost) hostHp else guestHp,
                oppHp = if (isHost) guestHp else hostHp,
                winnerIsMe = won,
                winMethod = method
            )
        }
        appendSystemMessage("Resultado del combate: ganó $winnerName ($method).")
    }

    fun exitFightToChat() {
        val s = _fightState.value
        if (s.phase == OnlineBoutPhase.PRESENTATION || s.phase == OnlineBoutPhase.FIGHTING) {
            val winnerRole = if (s.isHost) "GUEST" else "HOST"
            val hostHp = if (s.isHost) s.myHp else s.oppHp
            val guestHp = if (s.isHost) s.oppHp else s.myHp
            sendBoutFinish(winnerRole, "ABANDONO", hostHp, guestHp)
        }
        fightLoopJob?.cancel()
        fightLoopJob = null
        moveDir = 0f
        soundEngine.stopReferee()
        activeOpponentPeerId = null
        _chatState.update { it.copy(activeBoutId = null) }
    }

    fun leaveChatRoom(silent: Boolean = false) {
        fightLoopJob?.cancel()
        fightLoopJob = null
        presenceJob?.cancel()
        presenceJob = null
        soundEngine.stopReferee()
        if (!silent && _chatState.value.isConnected) {
            relay.publish(
                JSONObject().apply {
                    put("v", 1)
                    put("type", "LEAVE")
                    put("senderId", myPeerId)
                }.toString()
            )
        }
        relay.disconnect()
        _chatState.update {
            ChatRoomUiState(
                myPeerId = myPeerId,
                selectedCharacterId = it.selectedCharacterId,
                selectedRounds = it.selectedRounds
            )
        }
    }

    private fun startPresenceLoop() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (isActive) {
                delay(4_500L)
                if (_chatState.value.isConnected && _chatState.value.roomCode.isNotEmpty()) {
                    broadcastPresence("PRESENCE")
                    val cutoff = System.currentTimeMillis() - 16_000L
                    _chatState.update { state ->
                        state.copy(
                            members = state.members.filter { it.peerId == myPeerId || it.lastSeenMs >= cutoff }
                        )
                    }
                }
            }
        }
    }

    private fun broadcastPresence(type: String) {
        val me = localMember()
        upsertMember(me)
        relay.publish(
            JSONObject().apply {
                put("v", 1)
                put("type", type)
                put("senderId", myPeerId)
                put("roomTitle", _chatState.value.roomTitle)
                putProfile(me)
            }.toString()
        )
    }

    private fun sendBoutState(
        punchType: String = "NONE",
        punchDamage: Int = 0
    ) {
        val boutId = _chatState.value.activeBoutId ?: return
        val s = _fightState.value
        if (s.phase != OnlineBoutPhase.FIGHTING) return
        lastSyncTimeMs = System.currentTimeMillis()
        relay.publish(
            JSONObject().apply {
                put("v", 1)
                put("type", "BOUT_STATE")
                put("senderId", myPeerId)
                put("boutId", boutId)
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
            }.toString()
        )
    }

    private fun sendBoutFinish(
        winnerRole: String,
        method: String,
        hostHp: Float,
        guestHp: Float
    ) {
        val boutId = _chatState.value.activeBoutId ?: return
        relay.publish(
            JSONObject().apply {
                put("v", 1)
                put("type", "BOUT_FINISH")
                put("senderId", myPeerId)
                put("boutId", boutId)
                put("winnerRole", winnerRole)
                put("winMethod", method)
                put("hostHp", hostHp.toDouble())
                put("guestHp", guestHp.toDouble())
            }.toString()
        )
    }

    private fun localMember(): ChatRoomMember = ChatCodeGenerator.buildLocalMember(
        peerId = myPeerId,
        playerName = currentPlayerName,
        gloveId = currentGloveId,
        characterId = _chatState.value.selectedCharacterId,
        appearance = currentAppearance
    )

    private fun upsertMember(member: ChatRoomMember) {
        _chatState.update { state ->
            val others = state.members.filterNot { it.peerId == member.peerId }
            val nextList = (others + member).sortedByDescending { it.peerId == myPeerId }
            state.copy(members = nextList)
        }
    }

    private fun appendMessage(msg: ChatRoomMessage) {
        _chatState.update { state ->
            if (state.messages.any { it.id == msg.id }) return@update state
            val updated = (state.messages + msg).takeLast(120)
            state.copy(messages = updated)
        }
    }

    private fun appendSystemMessage(text: String) {
        appendMessage(
            ChatRoomMessage(
                id = "sys_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
                senderId = "system",
                senderName = "Ring KO",
                text = text,
                timestampMs = System.currentTimeMillis(),
                isSystem = true
            )
        )
    }

    private fun resetBoutCounters() {
        fightLoopJob?.cancel()
        fightLoopJob = null
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

    private fun triggerCallout(text: String, tone: CalloutTone) {
        calloutRemainingMs = 950L
        _fightState.update { it.copy(calloutText = text, calloutTone = tone) }
    }

    private fun syncSoundPrefs() {
        soundEngine.soundEnabled = soundEnabled()
        soundEngine.hapticsEnabled = hapticsEnabled()
    }

    private fun JSONObject.putProfile(m: ChatRoomMember) {
        put("name", m.name)
        put("charId", m.characterId)
        put("gloveId", m.gloveId)
        put("skinArgb", m.skinArgb)
        put("hairArgb", m.hairArgb)
        put("trunksArgb", m.trunksArgb)
        put("gloveArgb", m.gloveArgb)
        put("trimArgb", m.trimArgb)
    }

    private fun JSONObject.toMember(peerId: String): ChatRoomMember? {
        val name = optString("name", "").ifBlank { optString("fromName", "") }.ifBlank { return null }
        return ChatRoomMember(
            peerId = peerId,
            name = name,
            characterId = optString("charId", "custom"),
            gloveId = optString("gloveId", "classic_red"),
            skinArgb = optInt("skinArgb", 0xFFC47A4C.toInt()),
            hairArgb = optInt("hairArgb", 0xFF4A2C1D.toInt()),
            trunksArgb = optInt("trunksArgb", 0xFF1D4ED8.toInt()),
            gloveArgb = optInt("gloveArgb", 0xFFE63946.toInt()),
            trimArgb = optInt("trimArgb", 0xFFF5B700.toInt()),
            lastSeenMs = System.currentTimeMillis()
        )
    }

    override fun onCleared() {
        super.onCleared()
        leaveChatRoom(silent = true)
        soundEngine.release()
    }
}
