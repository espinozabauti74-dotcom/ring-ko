package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class BoxingSoundEngine(context: Context) {
    private val audioExecutor = Executors.newFixedThreadPool(3)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile
    var soundEnabled: Boolean = true

    @Volatile
    var hapticsEnabled: Boolean = true

    fun playBell(doubleRing: Boolean = false) {
        if (!soundEnabled) return
        audioExecutor.execute {
            playBellOnce()
            if (doubleRing) {
                Thread.sleep(190)
                playBellOnce()
            }
        }
    }

    private fun playBellOnce() {
        val sampleRate = 22050
        val durationMs = 520
        val count = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 5.2)
            val s = (sin(2 * PI * 980.0 * t) * 0.55 +
                sin(2 * PI * 1965.0 * t) * 0.30 +
                sin(2 * PI * 2940.0 * t) * 0.15) * env
            buffer[i] = (s * Short.MAX_VALUE * 0.45).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writePcm(buffer, sampleRate)
    }

    fun playWhoosh() {
        if (!soundEnabled) return
        audioExecutor.execute {
            val sampleRate = 22050
            val count = (sampleRate * 110) / 1000
            val buffer = ShortArray(count)
            var lp = 0.0
            for (i in 0 until count) {
                val p = i.toDouble() / count
                val env = sin(p * PI)
                val noise = (Random.nextDouble() * 2.0 - 1.0)
                lp = lp * 0.72 + noise * 0.28
                buffer[i] = (lp * env * Short.MAX_VALUE * 0.28).toInt().toShort()
            }
            writePcm(buffer, sampleRate)
        }
    }

    fun playImpact(heavy: Boolean = false, counter: Boolean = false) {
        if (hapticsEnabled) {
            vibrate(if (counter || heavy) 55L else 28L, if (counter) 240 else if (heavy) 190 else 120)
        }
        if (!soundEnabled) return
        audioExecutor.execute {
            val sampleRate = 22050
            val durationMs = if (counter) 190 else if (heavy) 150 else 105
            val count = (sampleRate * durationMs) / 1000
            val buffer = ShortArray(count)
            val baseFreq = if (counter) 145.0 else if (heavy) 115.0 else 170.0
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val thud = sin(2 * PI * (baseFreq * exp(-t * 14.0)) * t) * exp(-t * 22.0)
                val crack = (Random.nextDouble() * 2.0 - 1.0) * exp(-t * (if (counter) 28.0 else 42.0))
                val sample = thud * 0.75 + crack * (if (counter) 0.65 else 0.45)
                buffer[i] = (sample * Short.MAX_VALUE * 0.6).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            writePcm(buffer, sampleRate)
        }
    }

    fun playBlock(perfectParry: Boolean = false) {
        if (hapticsEnabled) {
            vibrate(if (perfectParry) 45L else 20L, if (perfectParry) 210 else 90)
        }
        if (!soundEnabled) return
        audioExecutor.execute {
            val sampleRate = 22050
            val count = (sampleRate * 110) / 1000
            val buffer = ShortArray(count)
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val env = exp(-t * 32.0)
                val tone = if (perfectParry) {
                    sin(2 * PI * 660.0 * t) * 0.5 + sin(2 * PI * 990.0 * t) * 0.4
                } else {
                    sin(2 * PI * 95.0 * t) * 0.6 + (Random.nextDouble() * 2.0 - 1.0) * 0.25
                }
                buffer[i] = (tone * env * Short.MAX_VALUE * 0.45).toInt()
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
        audioExecutor.execute {
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
        audioExecutor.execute {
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
        audioExecutor.execute {
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
        audioExecutor.shutdownNow()
    }
}
