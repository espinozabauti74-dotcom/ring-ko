package com.example.online

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class OnlineRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Debes iniciar sesión con Google para jugar online.")
    }

    fun observeUserProfile(userId: String): Flow<BoxerProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Usuario autenticado distinto al perfil solicitado." }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(BoxerProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeWaitingMatches(): Flow<List<OnlineMatch>> = flow {
        val uid = requireUserId()
        val path = "online_matches"
        emitAll(
            db.collection("online_matches")
                .whereEqualTo("status", "WAITING")
                .snapshots()
                .map { snap ->
                    snap.toObjects(OnlineMatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeMatch(matchId: String): Flow<OnlineMatch?> = flow {
        val uid = requireUserId()
        require(matchId.isNotBlank()) { "El código de sala no puede estar vacío." }
        val path = "online_matches/$matchId"
        emitAll(
            db.collection("online_matches").document(matchId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(OnlineMatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(displayName: String, equippedGloveId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim().ifBlank { "Retador" }.take(24)
            val cleanGlove = equippedGloveId.trim().ifBlank { "classic_red" }.take(32)
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "equippedGloveId" to cleanGlove,
                    "onlineWins" to 0L,
                    "onlineLosses" to 0L,
                    "onlineKos" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to cleanName,
                        "equippedGloveId" to cleanGlove,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath, auth)
            Result.failure(e)
        }
    }

    suspend fun recordMatchResult(won: Boolean, isKo: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (won) {
                updates["onlineWins"] = FieldValue.increment(1L)
                if (isKo) updates["onlineKos"] = FieldValue.increment(1L)
            } else {
                updates["onlineLosses"] = FieldValue.increment(1L)
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath, auth)
            Result.failure(e)
        }
    }

    suspend fun createMatch(
        hostName: String,
        hostGloveId: String,
        maxRounds: Int
    ): Result<String> {
        var docPath = "online_matches"
        return try {
            val uid = requireUserId()
            val cleanName = hostName.trim().ifBlank { "Retador" }.take(24)
            val cleanGlove = hostGloveId.trim().ifBlank { "classic_red" }.take(32)
            val validRounds = if (maxRounds == 10) 10L else 3L
            val code = generateRoomCode()
            val docRef = db.collection("online_matches").document(code)
            docPath = docRef.path
            val payload = mapOf(
                "roomCode" to code,
                "hostUid" to uid,
                "participantUids" to listOf(uid),
                "hostName" to cleanName,
                "hostGloveId" to cleanGlove,
                "status" to "WAITING",
                "maxRounds" to validRounds,
                "hostHp" to 150L,
                "guestHp" to 150L,
                "hostStamina" to 100L,
                "guestStamina" to 100L,
                "hostPosX" to -0.35,
                "guestPosX" to 0.35,
                "hostGuarding" to false,
                "guestGuarding" to false,
                "hostDodgeSeq" to 0L,
                "guestDodgeSeq" to 0L,
                "hostPunchType" to "NONE",
                "guestPunchType" to "NONE",
                "hostPunchSeq" to 0L,
                "guestPunchSeq" to 0L,
                "hostPunchDamage" to 0L,
                "guestPunchDamage" to 0L,
                "hostHitsLanded" to 0L,
                "guestHitsLanded" to 0L,
                "hostKnockdowns" to 0L,
                "guestKnockdowns" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath, auth)
            Result.failure(e)
        }
    }

    suspend fun joinMatch(
        rawRoomCode: String,
        guestName: String,
        guestGloveId: String
    ): Result<String> {
        val code = rawRoomCode.trim().uppercase().filter { it.isLetterOrDigit() }
        if (code.length !in 4..12) {
            return Result.failure(IllegalArgumentException("Ingresa un código de sala válido (4-12 caracteres)."))
        }
        val docPath = "online_matches/$code"
        return try {
            val uid = requireUserId()
            val cleanName = guestName.trim().ifBlank { "Retador" }.take(24)
            val cleanGlove = guestGloveId.trim().ifBlank { "classic_red" }.take(32)
            val docRef = db.collection("online_matches").document(code)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("No se encontró ninguna sala con el código $code."))
            }
            val match = snap.toObject(OnlineMatch::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Sala inválida."))
            if (match.hostUid == uid) {
                return Result.success(code)
            }
            if (match.status != "WAITING" || match.guestUid != null) {
                return Result.failure(IllegalStateException("Esta sala ya está en curso o finalizada."))
            }
            docRef.update(
                mapOf(
                    "guestUid" to uid,
                    "guestName" to cleanName,
                    "guestGloveId" to cleanGlove,
                    "participantUids" to listOf(match.hostUid, uid),
                    "status" to "ACTIVE",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath, auth)
            Result.failure(e)
        }
    }

    suspend fun syncPlayerCombatState(
        matchId: String,
        isHost: Boolean,
        hp: Int,
        stamina: Int,
        posX: Double,
        guarding: Boolean,
        dodgeSeq: Long,
        punchType: String,
        punchSeq: Long,
        punchDamage: Int,
        hitsLanded: Int,
        knockdowns: Int
    ): Result<Unit> {
        val docPath = "online_matches/$matchId"
        return try {
            requireUserId()
            val prefix = if (isHost) "host" else "guest"
            val clampedHp = hp.coerceIn(0, 250).toLong()
            val clampedStamina = stamina.coerceIn(0, 100).toLong()
            val clampedPos = posX.coerceIn(-1.0, 1.0)
            val clampedDmg = punchDamage.coerceIn(0, 120).toLong()
            val updates = mapOf(
                "${prefix}Hp" to clampedHp,
                "${prefix}Stamina" to clampedStamina,
                "${prefix}PosX" to clampedPos,
                "${prefix}Guarding" to guarding,
                "${prefix}DodgeSeq" to dodgeSeq.coerceAtLeast(0L),
                "${prefix}PunchType" to punchType,
                "${prefix}PunchSeq" to punchSeq.coerceAtLeast(0L),
                "${prefix}PunchDamage" to clampedDmg,
                "${prefix}HitsLanded" to hitsLanded.coerceAtLeast(0).toLong(),
                "${prefix}Knockdowns" to knockdowns.coerceAtLeast(0).toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("online_matches").document(matchId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath, auth)
            Result.failure(e)
        }
    }

    suspend fun finishMatch(
        matchId: String,
        winnerUid: String,
        winMethod: String,
        hostHp: Int,
        guestHp: Int,
        hostKnockdowns: Int,
        guestKnockdowns: Int
    ): Result<Unit> {
        val docPath = "online_matches/$matchId"
        return try {
            requireUserId()
            val safeMethod = if (winMethod in listOf("KO", "PUNTOS", "ABANDONO")) winMethod else "KO"
            db.collection("online_matches").document(matchId).update(
                mapOf(
                    "hostHp" to hostHp.coerceIn(0, 250).toLong(),
                    "guestHp" to guestHp.coerceIn(0, 250).toLong(),
                    "hostKnockdowns" to hostKnockdowns.coerceAtLeast(0).toLong(),
                    "guestKnockdowns" to guestKnockdowns.coerceAtLeast(0).toLong(),
                    "status" to "FINISHED",
                    "winnerUid" to winnerUid,
                    "winMethod" to safeMethod,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath, auth)
            Result.failure(e)
        }
    }

    suspend fun cancelWaitingMatch(matchId: String): Result<Unit> {
        val docPath = "online_matches/$matchId"
        return try {
            requireUserId()
            db.collection("online_matches").document(matchId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath, auth)
            Result.failure(e)
        }
    }

    private fun generateRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }
}
