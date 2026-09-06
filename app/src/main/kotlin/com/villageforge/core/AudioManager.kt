package com.villageforge.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sign

class AudioManager {
    @Volatile var enabled = true

    /** v2.1 procedural village music; toggle at runtime from the HUD. */
    val music = MusicSynth()

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
        music.silence()
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
                var sample = (music.render() * 32767f).toInt()
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
        SfxId.QUEST -> 0.60f; SfxId.LEVELUP -> 0.60f; SfxId.ACHIEVE -> 0.80f
        SfxId.ORDER -> 0.45f; SfxId.WOOD_HIT -> 0.18f
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
            SfxId.ACHIEVE -> {
                // Medal chime: two warm bells a fourth apart, slow shimmer.
                val a = (sin(6.2831853f * 880f * t) * 0.30f + sin(6.2831853f * 1320f * t) * 0.10f) * exp(-t * 3.2f)
                val b = if (t > 0.22f) {
                    (sin(6.2831853f * 1174.66f * (t - 0.22f)) * 0.26f + sin(6.2831853f * 1760f * (t - 0.22f)) * 0.08f) * exp(-(t - 0.22f) * 2.6f)
                } else 0f
                a + b
            }
            SfxId.ORDER -> {
                // A passing customer: two soft market-bell pings.
                val a = sin(6.2831853f * 987.77f * t) * 0.22f * exp(-t * 6f)
                val b = if (t > 0.18f) sin(6.2831853f * 1318.51f * (t - 0.18f)) * 0.18f * exp(-(t - 0.18f) * 6f) else 0f
                a + b
            }
            SfxId.WOOD_HIT -> {
                val d = exp(-t * 14f)
                (sin(w * 430f * p) * 0.55f + sin(w * 860f * p) * 0.18f) * d +
                    noise[(v.seed + v.phase) and 4095] * 0.18f * ((1f - t * 9f).coerceAtLeast(0f))
            }
        }
    }
}

/**
 * A tiny step-sequencer that renders a cozy pentatonic village loop in real
 * time — no audio assets required. Runs on the AudioManager thread; the UI
 * only touches [enabled] and [nightFactor] (both volatile).
 * Day: bright music-box melody over a soft bass. Night: melody drops an
 * octave and the mix calms down.
 */
class MusicSynth {
    @Volatile var enabled = true
    /** 0 = bright day mix, 1 = calm moonlit mix. */
    @Volatile var nightFactor = 0f

    private class Note {
        var active = false; var freq = 440f; var age = 0; var dur = 0; var kind = KIND_MELODY
    }

    private val notes = Array(7) { Note() }
    private var step = 0
    private var stepAcc = 0f

    fun silence() {
        for (n in notes) n.active = false
        stepAcc = 0f
    }

    /** One output sample; call once per sample from the audio thread. */
    fun render(): Float {
        stepAcc += 1f / SAMPLE_RATE
        while (stepAcc >= STEP_SECONDS) {
            stepAcc -= STEP_SECONDS
            triggerStep(step)
            step = (step + 1) % MELODY.size
        }
        var out = 0f
        for (n in notes) {
            if (!n.active) continue
            val t = n.age.toFloat() / SAMPLE_RATE
            out += when (n.kind) {
                KIND_MELODY -> {
                    val decay = exp(-t * 3.4f)
                    (sin(6.2831853f * n.freq * t) * 0.55f +
                        sin(6.2831853f * n.freq * 1.0013f * t) * 0.22f +
                        sin(6.2831853f * n.freq * 2f * t) * 0.10f) * decay
                }
                KIND_BASS -> {
                    val decay = exp(-t * 1.6f)
                    (sin(6.2831853f * n.freq * t) * 0.5f +
                        sin(6.2831853f * n.freq * 2f * t) * 0.08f) * decay
                }
                else -> { // KIND_BELL
                    val decay = exp(-t * 1.1f)
                    (sin(6.2831853f * n.freq * t) * 0.20f +
                        sin(6.2831853f * n.freq * 2.76f * t) * 0.06f) * decay
                }
            }
            n.age++
            if (n.age >= n.dur) n.active = false
        }
        val night = nightFactor
        val gain = if (enabled) (0.15f - 0.05f * night) else 0f
        return out * gain
    }

    private fun triggerStep(step: Int) {
        if (!enabled) return
        val night = nightFactor
        val melodic = MELODY[step]
        if (melodic >= 0) {
            val freq = PENTA[melodic] * (if (night > 0.5f) 0.5f else 1f)
            start(freq, KIND_MELODY, 1.4f)
        }
        if (step % 4 == 0) {
            val bar = step / 16
            start(BASS_LINE[bar], KIND_BASS, 2.4f)
        }
        if (step == 0 || step == 32) start(2093f, KIND_BELL, 2.8f)   // gentle sparkle every other bar group
    }

    private fun start(freq: Float, kind: Int, seconds: Float) {
        var slot = notes.firstOrNull { !it.active }
        if (slot == null) {
            // steal the oldest voice
            var oldest = notes[0]
            for (n in notes) if (n.age > oldest.age) oldest = n
            slot = oldest
        }
        slot!!.active = true
        slot.freq = freq
        slot.age = 0
        slot.dur = (seconds * SAMPLE_RATE).toInt()
        slot.kind = kind
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val BPM = 88f
        const val STEP_SECONDS = 60f / BPM / 4f   // sixteenth notes
        const val KIND_MELODY = 0
        const val KIND_BASS = 1
        const val KIND_BELL = 2

        // C major pentatonic over two octaves: C5 D5 E5 G5 A5 C6 D6 E6
        val PENTA = floatArrayOf(
            523.25f, 587.33f, 659.25f, 783.99f, 880f, 1046.5f, 1174.66f, 1318.51f,
        )

        // 64-step (4-bar) melody; -1 = rest. Music-box arpeggio feel.
        val MELODY = intArrayOf(
            0, -1, 2, -1, 4, -1, 3, -1, 2, -1, 0, -1, -1, -1, 1, -1,
            2, -1, 3, -1, 5, -1, 4, -1, 3, -1, 2, -1, -1, -1, -1, -1,
            0, -1, 2, -1, 4, -1, 6, -1, 5, -1, 4, -1, 3, -1, 2, -1,
            4, -1, 3, -1, 2, -1, 1, -1, 0, -1, 2, -1, 1, -1, -1, -1,
        )

        // Quarter-note bass under each bar: C, Am, F, G.
        val BASS_LINE = floatArrayOf(130.81f, 110f, 87.31f, 98f)
    }
}
