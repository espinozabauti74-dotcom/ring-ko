package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.example.model.EnemyAttackType
import com.example.model.PunchType
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class BoxingSoundEngine(context: Context) {
    private val appContext = context.applicationContext
    private val audioExecutor = Executors.newFixedThreadPool(3)
    private val released = AtomicBoolean(false)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady: Boolean = false
    private val ttsLock = Any()
    private var pendingAnnouncement: Pair<String, Long>? = null

    @Volatile
    var soundEnabled: Boolean = true

    @Volatile
    var hapticsEnabled: Boolean = true

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (released.get()) return@TextToSpeech
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    try {
                        val esResult = engine.setLanguage(Locale("es", "ES"))
                        if (esResult == TextToSpeech.LANG_MISSING_DATA || esResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            val genericEs = engine.setLanguage(Locale("es"))
                            if (genericEs == TextToSpeech.LANG_MISSING_DATA || genericEs == TextToSpeech.LANG_NOT_SUPPORTED) {
                                engine.setLanguage(Locale.getDefault())
                            }
                        }
                        engine.setPitch(0.86f)
                        engine.setSpeechRate(1.06f)
                        engine.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        ttsReady = true
                        val pending = synchronized(ttsLock) {
                            val item = pendingAnnouncement
                            pendingAnnouncement = null
                            item
                        }
                        if (pending != null && soundEnabled && System.currentTimeMillis() - pending.second <= 2600L) {
                            speakInternal(pending.first, queue = false)
                        }
                    } catch (_: Throwable) {
                        ttsReady = false
                    }
                }
            }
        } catch (_: Throwable) {
            tts = null
            ttsReady = false
        }
    }

    fun announceReferee(text: String, queue: Boolean = false) {
        if (!soundEnabled || released.get() || text.isBlank()) return
        if (!ttsReady) {
            synchronized(ttsLock) {
                pendingAnnouncement = text to System.currentTimeMillis()
            }
            return
        }
        speakInternal(text, queue)
    }

    fun announceRefereeCount(count: Int) {
        val word = when (count) {
            1 -> "Uno"
            2 -> "Dos"
            3 -> "Tres"
            4 -> "Cuatro"
            5 -> "Cinco"
            6 -> "Seis"
            7 -> "Siete"
            8 -> "Ocho"
            9 -> "Nueve"
            10 -> "Diez"
            else -> count.toString()
        }
        announceReferee(word, queue = false)
    }

    fun stopReferee() {
        synchronized(ttsLock) {
            pendingAnnouncement = null
        }
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
    }

    private fun speakInternal(text: String, queue: Boolean) {
        val engine = tts ?: return
        try {
            val mode = if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
            engine.speak(text, mode, null, "referee_${System.nanoTime()}")
        } catch (_: Throwable) {
        }
    }

    fun playBell(doubleRing: Boolean = false) {
        if (!soundEnabled) return
        runAudio {
            playBellOnce()
            if (doubleRing) {
                Thread.sleep(190)
                playBellOnce()
            }
        }
    }

    private fun playBellOnce() {
        val sampleRate = 22050
        val durationMs = 540
        val count = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 5.0)
            val s = (sin(2 * PI * 980.0 * t) * 0.55 +
                sin(2 * PI * 1965.0 * t) * 0.30 +
                sin(2 * PI * 2940.0 * t) * 0.15) * env
            buffer[i] = (s * Short.MAX_VALUE * 0.45).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writePcm(buffer, sampleRate)
    }

    fun playPunchThrow(punchType: PunchType? = null) {
        if (!soundEnabled) return
        runAudio {
            val sampleRate = 22050
            val durationMs = when (punchType) {
                PunchType.JAB -> 85
                PunchType.CROSS -> 115
                PunchType.HOOK -> 140
                PunchType.UPPERCUT -> 155
                null -> 110
            }
            val count = (sampleRate * durationMs) / 1000
            val buffer = ShortArray(count)
            var lp = 0.0
            for (i in 0 until count) {
                val p = i.toDouble() / count
                val env = sin(p * PI)
                val noise = (Random.nextDouble() * 2.0 - 1.0)
                val alpha = when (punchType) {
                    PunchType.JAB -> 0.42
                    PunchType.CROSS -> 0.34
                    PunchType.HOOK -> 0.24 + 0.16 * p
                    PunchType.UPPERCUT -> 0.20 + 0.28 * p
                    null -> 0.28
                }
                lp = lp * (1.0 - alpha) + noise * alpha
                val sweepFreq = when (punchType) {
                    PunchType.JAB -> 320.0 - 110.0 * p
                    PunchType.CROSS -> 270.0 - 120.0 * p
                    PunchType.HOOK -> 185.0 + 75.0 * sin(p * PI)
                    PunchType.UPPERCUT -> 160.0 + 210.0 * p
                    null -> 220.0
                }
                val tone = sin(2 * PI * sweepFreq * (i.toDouble() / sampleRate)) * 0.16
                val s = (lp * 0.90 + tone) * env * 0.32
                buffer[i] = (s * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            writePcm(buffer, sampleRate)
        }
    }

    fun playEnemyPunchThrow(attackType: EnemyAttackType?) {
        val mapped = when (attackType) {
            EnemyAttackType.LEFT_JAB -> PunchType.JAB
            EnemyAttackType.RIGHT_CROSS -> PunchType.CROSS
            EnemyAttackType.LEFT_HOOK -> PunchType.HOOK
            EnemyAttackType.UPPERCUT -> PunchType.UPPERCUT
            null -> null
        }
        playPunchThrow(mapped)
    }

    fun playWhoosh() {
        playPunchThrow(null)
    }

    fun playDodge(perfect: Boolean = false) {
        if (hapticsEnabled && perfect) {
            vibrate(22L, 115)
        }
        if (!soundEnabled) return
        runAudio {
            val sampleRate = 22050
            val durationMs = if (perfect) 165 else 125
            val count = (sampleRate * durationMs) / 1000
            val buffer = ShortArray(count)
            var lp = 0.0
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val p = i.toDouble() / count
                val env = sin(p * PI)
                val noise = Random.nextDouble() * 2.0 - 1.0
                // Descending wind slip + subtle canvas footwork scuff at start
                val slipAlpha = 0.45 - 0.25 * p
                lp = lp * (1.0 - slipAlpha) + noise * slipAlpha
                val slipTone = sin(2 * PI * (480.0 - 240.0 * p) * t) * 0.18
                val scuff = if (p < 0.28) {
                    (Random.nextDouble() * 2.0 - 1.0) * sin((p / 0.28) * PI) * 0.18
                } else 0.0
                val perfectCue = if (perfect && p > 0.32) {
                    val cueT = (p - 0.32) / 0.68
                    val freq = if (cueT < 0.48) 740.0 else 1046.5
                    sin(2 * PI * freq * t) * exp(-cueT * 5.0) * 0.30
                } else 0.0
                val sample = (lp * 0.72 + slipTone) * env + scuff + perfectCue
                buffer[i] = (sample * Short.MAX_VALUE * 0.44).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            writePcm(buffer, sampleRate)
        }
    }

    fun playImpact(
        heavy: Boolean = false,
        counter: Boolean = false,
        punchType: PunchType? = null
    ) {
        if (hapticsEnabled) {
            vibrate(
                if (counter || heavy) 58L else 28L,
                if (counter) 245 else if (heavy) 195 else 120
            )
        }
        if (!soundEnabled) return
        runAudio {
            val sampleRate = 22050
            val durationMs = when {
                counter -> 205
                punchType == PunchType.UPPERCUT -> 185
                punchType == PunchType.HOOK || heavy -> 155
                punchType == PunchType.CROSS -> 130
                else -> 100
            }
            val count = (sampleRate * durationMs) / 1000
            val buffer = ShortArray(count)
            val baseFreq = when {
                counter -> 135.0
                punchType == PunchType.UPPERCUT -> 98.0
                punchType == PunchType.HOOK || heavy -> 112.0
                punchType == PunchType.CROSS -> 138.0
                else -> 175.0
            }
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val thud = sin(2 * PI * (baseFreq * exp(-t * 14.0)) * t) * exp(-t * 20.0)
                val leatherSnap = (Random.nextDouble() * 2.0 - 1.0) * exp(-t * (if (counter) 26.0 else 40.0))
                val counterRing = if (counter) {
                    sin(2 * PI * 520.0 * t) * exp(-t * 18.0) * 0.25
                } else 0.0
                val sample = thud * 0.76 + leatherSnap * (if (counter) 0.68 else 0.48) + counterRing
                buffer[i] = (sample * Short.MAX_VALUE * 0.62).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            writePcm(buffer, sampleRate)
        }
    }

    fun playGuardUp() {
        if (!soundEnabled) return
        runAudio {
            val sampleRate = 22050
            val count = (sampleRate * 48) / 1000
            val buffer = ShortArray(count)
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val env = exp(-t * 48.0)
                val tap = sin(2 * PI * 155.0 * t) * 0.5 + (Random.nextDouble() * 2.0 - 1.0) * 0.22
                buffer[i] = (tap * env * Short.MAX_VALUE * 0.26).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            writePcm(buffer, sampleRate)
        }
    }

    fun playBlock(perfectParry: Boolean = false, heavy: Boolean = false) {
        if (hapticsEnabled) {
            vibrate(if (perfectParry) 45L else 22L, if (perfectParry) 215 else 95)
        }
        if (!soundEnabled) return
        runAudio {
            val sampleRate = 22050
            val durationMs = if (perfectParry) 160 else 118
            val count = (sampleRate * durationMs) / 1000
            val buffer = ShortArray(count)
            var lp = 0.0
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val env = exp(-t * (if (perfectParry) 22.0 else 30.0))
                val noise = Random.nextDouble() * 2.0 - 1.0
                lp = lp * 0.78 + noise * 0.22
                val tone = if (perfectParry) {
                    sin(2 * PI * 720.0 * t) * 0.45 +
                        sin(2 * PI * 1140.0 * t) * 0.35 +
                        lp * 0.25
                } else {
                    val thudFreq = if (heavy) 74.0 else 92.0
                    sin(2 * PI * thudFreq * t) * 0.68 + lp * 0.42
                }
                buffer[i] = (tone * env * Short.MAX_VALUE * 0.48).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            writePcm(buffer, sampleRate)
        }
    }

    fun playGuardBreak() {
        if (hapticsEnabled) {
            vibrate(72L, 235)
        }
        if (!soundEnabled) return
        runAudio {
            val sampleRate = 22050
            val count = (sampleRate * 185) / 1000
            val buffer = ShortArray(count)
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val burst1 = exp(-t * 24.0)
                val burst2 = if (t > 0.045) exp(-(t - 0.045) * 20.0) else 0.0
                val low = sin(2 * PI * 86.0 * t) * (burst1 + burst2 * 0.8) * 0.55
                val crack = (Random.nextDouble() * 2.0 - 1.0) * (burst1 * 0.5 + burst2 * 0.6)
                buffer[i] = ((low + crack) * Short.MAX_VALUE * 0.55).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            writePcm(buffer, sampleRate)
        }
    }

    fun playKnockdown() {
        if (hapticsEnabled) {
            vibrate(140L, 255)
        }
        if (!soundEnabled) return
        runAudio {
            val sampleRate = 22050
            val count = (sampleRate * 420) / 1000
            val buffer = ShortArray(count)
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val boom = sin(2 * PI * (82.0 * exp(-t * 3.5)) * t) * exp(-t * 6.5)
                val crowd = (Random.nextDouble() * 2.0 - 1.0) * sin((i.toDouble() / count) * PI) * 0.22
                buffer[i] = ((boom * 0.75 + crowd) * Short.MAX_VALUE * 0.65).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            writePcm(buffer, sampleRate)
        }
    }

    fun playCrowdCheer(intensity: Float = 0.8f) {
        if (!soundEnabled) return
        runAudio {
            val sampleRate = 22050
            val count = (sampleRate * 550) / 1000
            val buffer = ShortArray(count)
            var lp = 0.0
            for (i in 0 until count) {
                val p = i.toDouble() / count
                val env = sin(p * PI)
                val noise = Random.nextDouble() * 2.0 - 1.0
                lp = lp * 0.82 + noise * 0.18
                val s = lp * env * (0.28 + intensity * 0.25)
                buffer[i] = (s * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            writePcm(buffer, sampleRate)
        }
    }

    fun playPresentationFanfare() {
        if (!soundEnabled) return
        runAudio {
            val sampleRate = 22050
            val notes = listOf(440.0 to 130, 554.37 to 130, 659.25 to 260)
            for ((freq, durMs) in notes) {
                val count = (sampleRate * durMs) / 1000
                val buffer = ShortArray(count)
                for (i in 0 until count) {
                    val t = i.toDouble() / sampleRate
                    val env = exp(-t * 4.5)
                    val s = (sin(2 * PI * freq * t) * 0.65 + sin(2 * PI * freq * 2.0 * t) * 0.25) * env
                    buffer[i] = (s * Short.MAX_VALUE * 0.35).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                writePcm(buffer, sampleRate)
            }
        }
    }

    private inline fun runAudio(crossinline block: () -> Unit) {
        if (released.get()) return
        try {
            audioExecutor.execute {
                if (!released.get()) {
                    block()
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (_: Throwable) {
            // Ignore vibration failure on devices without vibrator hardware
        }
    }

    private fun writePcm(buffer: ShortArray, sampleRate: Int) {
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bytesSize = maxOf(minBuf, buffer.size * 2)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bytesSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep((buffer.size * 1000L) / sampleRate + 30L)
        } catch (_: Throwable) {
            // Safe fallback if audio device is unavailable
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Throwable) {
            }
        }
    }

    fun release() {
        if (released.compareAndSet(false, true)) {
            stopReferee()
            try {
                tts?.shutdown()
            } catch (_: Throwable) {
            }
            tts = null
            ttsReady = false
            audioExecutor.shutdownNow()
        }
    }
}
