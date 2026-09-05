package com.villageforge.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sign

class AudioManager {
    @Volatile var enabled = true

    private class Voice {
        var active = false; var id = SfxId.ROCK_HIT; var phase = 0; var dur = 0; var pitch = 1f; var seed = 0
    }

    private val voices = Array(8) { Voice() }
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<Pair<SfxId, Float>>()
    private var thread: Thread? = null
    @Volatile private var running = false
    private var counter = 0

    private val noise = FloatArray(4096).also { table ->
        var seed = 0x5EED
        for (i in table.indices) {
            seed = seed * 1103515245 + 12345
            table[i] = ((seed ushr 16) and 0xFFFF) / 32768f - 1f
        }
    }

    fun play(id: SfxId, pitch: Float = 1f) {
        if (!enabled) return
        if (pending.size > 32) return
        pending.add(id to pitch)
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "vf-audio").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.let { t -> t.join(60) }
        thread = null
        pending.clear()
        for (v in voices) v.active = false
    }

    private fun loop() {
        val minBytes = maxOf(AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), 2048)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBytes * 2)
            .build()
        track.play()
        val buffer = ShortArray(512)
        while (running) {
            drain()
            for (i in 0 until 512) {
                var sample = 0
                for (v in voices) {
                    if (!v.active) continue
                    sample += (render(v) * 0.6f).toInt()
                    v.phase++
                    if (v.phase >= v.dur) v.active = false
                }
                buffer[i] = sample.coerceIn(-12000, 12000).toShort()
            }
            track.write(buffer, 0, 512)
        }
        track.stop(); track.release()
    }

    private fun drain() {
        while (true) {
            val req = pending.poll() ?: return
            val voice = voices.firstOrNull { !it.active } ?: return
            voice.id = req.first; voice.pitch = req.second; voice.phase = 0
            voice.dur = (dur(voice.id) * 44100).toInt()
            voice.seed = (counter * 131 + voice.id.ordinal * 977) and 4095
            counter++
            voice.active = true
        }
    }

    private fun dur(id: SfxId): Float = when (id) {
        SfxId.ROCK_HIT -> 0.15f; SfxId.ROCK_BREAK -> 0.35f; SfxId.COINS -> 0.40f
        SfxId.BUY -> 0.18f; SfxId.DENIED -> 0.14f
        SfxId.SMELT -> 0.50f; SfxId.HAMMER -> 0.20f; SfxId.CRAFT -> 0.50f
        SfxId.QUEST -> 0.60f; SfxId.LEVELUP -> 0.60f
    }

    private fun render(v: Voice): Float {
        val t = v.phase.toFloat() / 44100
        val w = 6.2831853f * v.phase / 44100
        val p = v.pitch
        return when (v.id) {
            SfxId.ROCK_HIT -> {
                val d = exp(-t * 32f)
                (sin(w * 1900f * p) * 0.45f + sin(w * 2850f * p) * 0.28f) * d +
                    noise[(v.seed + v.phase) and 4095] * 0.30f * ((1f - v.phase.toFloat() / 240).coerceAtLeast(0f))
            }
            SfxId.ROCK_BREAK ->
                noise[(v.seed + (v.phase shr 1)) and 4095] * 0.55f * exp(-t * 9f) + sin(w * 95f) * 0.35f * exp(-t * 14f)
            SfxId.COINS -> {
                val a = sin(w * 920f) * 0.40f * exp(-t * 7f)
                val b = if (t > 0.06f) sin(6.2831853f * 1380f * (t - 0.06f)) * 0.30f * exp(-(t - 0.06f) * 6f) else 0f
                a + b
            }
            SfxId.BUY -> sin(w * 170f) * 0.5f * exp(-t * 22f)
            SfxId.DENIED ->
                (sign(sin(w * 150f)) + sign(sin(w * 151.8f))) * 0.12f * (1f - t / 0.14f).coerceAtLeast(0f)
            SfxId.SMELT -> {
                // Pour hiss settling into a warm shimmer.
                val hiss = noise[(v.seed + (v.phase shr 1)) and 4095] * 0.22f * exp(-t * 5f)
                val shimmer = (sin(w * 1560f * p) * 0.25f + sin(w * 2340f * p) * 0.16f) * exp(-t * 4f)
                hiss + shimmer
            }
            SfxId.HAMMER -> {
                // Metallic clang: inharmonic partials + a click transient.
                val clang = (sin(w * 2100f * p) * 0.40f + sin(w * 1050f * p) * 0.22f + sin(w * 3170f * p) * 0.12f) * exp(-t * 26f)
                val click = noise[(v.seed + v.phase) and 4095] * 0.30f * ((1f - v.phase.toFloat() / 160).coerceAtLeast(0f))
                clang + click
            }
            SfxId.CRAFT -> {
                // Final thud, then a bright two-note chime.
                val thud = sin(w * 520f) * 0.45f * exp(-t * 12f)
                val chime = if (t > 0.12f) {
                    (sin(6.2831853f * 1240f * (t - 0.12f)) * 0.22f + sin(6.2831853f * 1860f * (t - 0.12f)) * 0.10f) *
                        exp(-(t - 0.12f) * 7f)
                } else 0f
                thud + chime
            }
            SfxId.QUEST -> {
                // Rising three-note fanfare.
                val f = when {
                    t < 0.16f -> 523.25f
                    t < 0.32f -> 659.25f
                    else -> 783.99f
                }
                val local = if (t < 0.16f) t else if (t < 0.32f) t - 0.16f else t - 0.32f
                sin(6.2831853f * f * local) * 0.35f * (exp(-local * 5f) * (1f - t / 0.6f).coerceAtLeast(0f) + 0.15f)
            }
            SfxId.LEVELUP -> {
                // Bright rising sweep.
                val sweepPhase = 6.2831853f * (400f * t + 450f * t * t)
                sin(sweepPhase) * 0.35f * exp(-t * 3.5f) + sin(sweepPhase * 1.5f) * 0.12f * exp(-t * 4f)
            }
        }
    }
}
