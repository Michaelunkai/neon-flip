package com.michaelunkai.neonflip.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Fully procedural audio: every SFX and the music loop are synthesized into PCM
 * buffers at init (background thread) and played via static-mode AudioTracks.
 * Zero asset files. Fails soft: any audio problem just means silence.
 */
object AudioSynth {

    private const val SR = 44100

    enum class Sfx { FLIP, COIN, GATE, DEATH, NEAR, UI, COMBO, BUY, QUEST, DROP }

    @Volatile private var ready = false
    @Volatile private var sfxOn = true
    @Volatile private var musicOn = true

    private val sfxPcm = HashMap<Sfx, ShortArray>()
    private val sfxPools = HashMap<Sfx, Array<AudioTrack?>>()
    private val sfxCursor = HashMap<Sfx, Int>()
    private var musicTrack: AudioTrack? = null
    private val lock = Any()

    fun init() {
        sfxOn = com.michaelunkai.neonflip.retention.SaveStore.data.sfxOn
        musicOn = com.michaelunkai.neonflip.retention.SaveStore.data.musicOn
        Thread {
            try {
                for (s in Sfx.values()) {
                    sfxPcm[s] = renderSfx(s)
                    sfxPools[s] = arrayOfNulls(3)
                    sfxCursor[s] = 0
                }
                buildMusic()
                ready = true
                if (musicOn) startMusic()
            } catch (t: Throwable) {
                Log.w("AudioSynth", "audio init failed; staying silent", t)
            }
        }.apply { name = "audio-synth"; isDaemon = true; start() }
    }

    // ------------------------------------------------------------------ public API

    fun play(s: Sfx, pitch: Float = 1f) {
        if (!ready || !sfxOn) return
        try {
            synchronized(lock) {
                val pool = sfxPools[s] ?: return
                val pcm = sfxPcm[s] ?: return
                val idx = (sfxCursor[s] ?: 0) % pool.size
                sfxCursor[s] = idx + 1
                var tr = pool[idx]
                if (tr == null) {
                    tr = staticTrack(pcm.size)
                    tr.write(pcm, 0, pcm.size)
                    pool[idx] = tr
                }
                if (pitch != 1f) tr.playbackParams = tr.playbackParams.setPitch(pitch.coerceIn(0.5f, 2f))
                else tr.playbackParams = tr.playbackParams.setPitch(1f)
                tr.setVolume(0.9f)
                try { tr.pause(); tr.flush() } catch (_: Throwable) {}
                tr.reloadStaticData()
                tr.play()
            }
        } catch (_: Throwable) {}
    }

    fun startMusic() {
        if (!musicOn) return
        try {
            val tr = musicTrack ?: return
            if (tr.playState != AudioTrack.PLAYSTATE_PLAYING) tr.play()
        } catch (_: Throwable) {}
    }

    fun pauseMusic() {
        try { musicTrack?.pause() } catch (_: Throwable) {}
    }

    fun setMusicOn(on: Boolean) {
        musicOn = on
        if (on) startMusic() else pauseMusic()
    }

    fun setSfxOn(on: Boolean) { sfxOn = on }

    // ------------------------------------------------------------------ SFX rendering

    private fun renderSfx(s: Sfx): ShortArray = when (s) {
        Sfx.FLIP -> blip(660.0, 880.0, 0.07, 0.55, square)
        Sfx.COIN -> blip(1320.0, 1560.0, 0.11, 0.5, sineWithHarmonic)
        Sfx.GATE -> sweep(320.0, 980.0, 0.16, 0.55)
        Sfx.DEATH -> deathBurst()
        Sfx.NEAR -> shimmer()
        Sfx.UI -> blip(440.0, 500.0, 0.045, 0.4, sine)
        Sfx.COMBO -> arpeggio(doubleArrayOf(660.0, 880.0, 1108.7), 0.055, 0.5)
        Sfx.BUY -> arpeggio(doubleArrayOf(523.3, 659.3, 784.0, 1046.5), 0.06, 0.5)
        Sfx.QUEST -> arpeggio(doubleArrayOf(784.0, 987.8, 1174.7), 0.07, 0.55)
        Sfx.DROP -> sweep(220.0, 1320.0, 0.30, 0.55)
    }

    private fun toSamples(seconds: Double): Int = (SR * seconds).toInt()

    private fun writeSample(buf: ShortArray, i: Int, v: Double) {
        buf[i] = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
    }

    private val sine = { ph: Double -> kotlin.math.sin(ph) }
    private val square = { ph: Double -> if (kotlin.math.sin(ph) >= 0) 0.6 else -0.6 }
    private val sineWithHarmonic = { ph: Double ->
        kotlin.math.sin(ph) * 0.75 + kotlin.math.sin(ph * 2) * 0.25
    }

    private fun blip(f0: Double, f1: Double, dur: Double, gain: Double,
                     osc: (Double) -> Double): ShortArray {
        val n = toSamples(dur)
        val out = ShortArray(n)
        var ph = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = f0 + (f1 - f0) * t
            ph += 2.0 * Math.PI * f / SR
            val env = Math.exp(-4.5 * t)
            writeSample(out, i, osc(ph) * env * gain)
        }
        return out
    }

    private fun sweep(f0: Double, f1: Double, dur: Double, gain: Double): ShortArray {
        val n = toSamples(dur)
        val out = ShortArray(n)
        var ph = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = f0 + (f1 - f0) * (1.0 - Math.pow(1.0 - t, 2.0))
            ph += 2.0 * Math.PI * f / SR
            val env = 0.35 + 0.65 * Math.sin(Math.PI * t)
            writeSample(out, i, kotlin.math.sin(ph) * env * gain)
        }
        return out
    }

    private fun arpeggio(freqs: DoubleArray, noteDur: Double, gain: Double): ShortArray {
        val noteN = toSamples(noteDur)
        val out = ShortArray(noteN * freqs.size)
        for ((k, f) in freqs.withIndex()) {
            for (i in 0 until noteN) {
                val t = i.toDouble() / noteN
                val env = Math.exp(-3.2 * t)
                val v = (kotlin.math.sin(2.0 * Math.PI * f * i / SR) * 0.8 +
                        kotlin.math.sin(4.0 * Math.PI * f * i / SR) * 0.2) * env * gain
                writeSample(out, k * noteN + i, v)
            }
        }
        return out
    }

    private fun deathBurst(): ShortArray {
        val n = toSamples(0.42)
        val out = ShortArray(n)
        var seed = 0x9E3779B9L
        var ph = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val noise = ((seed shr 33).toDouble() / (1L shl 30).toDouble()) - 1.0
            ph += 2.0 * Math.PI * (240.0 * (1.0 - t) + 40.0) / SR
            val env = Math.exp(-6.0 * t)
            writeSample(out, i, (noise * 0.55 + kotlin.math.sin(ph) * 0.45) * env * 0.85)
        }
        return out
    }

    private fun shimmer(): ShortArray {
        val n = toSamples(0.22)
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val env = Math.sin(Math.PI * t) * Math.exp(-1.6 * t)
            val v = (kotlin.math.sin(2.0 * Math.PI * 2400.0 * i / SR) * 0.4 +
                    kotlin.math.sin(2.0 * Math.PI * 3170.0 * i / SR) * 0.3) * env * 0.45
            writeSample(out, i, v)
        }
        return out
    }

    // ------------------------------------------------------------------ music loop

    /**
     * 8-bar arpeggio loop (Am - F - C - G), bass + arp + soft bell, ~19s at 100 BPM.
     * Rendered once, looped in hardware via static-mode loop points.
     */
    private fun buildMusic() {
        val bpm = 100.0
        val beat = 60.0 / bpm
        val bar = beat * 4
        val totalN = toSamples(bar * 8)
        val out = ShortArray(totalN)
        // A minor pentatonic-ish progression: Am F C G (x2)
        val roots = doubleArrayOf(220.0, 174.6, 130.8, 196.0, 220.0, 174.6, 130.8, 196.0)
        val arps = arrayOf(
            doubleArrayOf(220.0, 261.6, 329.6, 440.0), doubleArrayOf(174.6, 220.0, 261.6, 349.2),
            doubleArrayOf(130.8, 164.8, 196.0, 261.6), doubleArrayOf(196.0, 246.9, 293.7, 392.0),
            doubleArrayOf(220.0, 261.6, 329.6, 440.0), doubleArrayOf(174.6, 220.0, 261.6, 349.2),
            doubleArrayOf(130.8, 164.8, 196.0, 261.6), doubleArrayOf(196.0, 246.9, 293.7, 392.0)
        )
        val eighth = beat / 2.0
        val eighthN = toSamples(eighth)
        for (b in 0 until 8) {                     // bar
            for (e in 0 until 8) {                 // eighth notes
                val note = arps[b][e % 4]
                val start = b * toSamples(bar) + e * eighthN
                val len = minOf(eighthN * 2, totalN - start)
                for (i in 0 until len) {
                    val t = i.toDouble() / SR
                    val env = Math.exp(-4.0 * t / (eighth * 2))
                    val v = (kotlin.math.sin(2.0 * Math.PI * note * t) * 0.30 +
                            kotlin.math.sin(4.0 * Math.PI * note * t) * 0.08) * env * 0.16
                    val idx = start + i
                    if (idx in 0 until totalN) out[idx] = (out[idx] + (v * Short.MAX_VALUE)).toInt().toShort()
                }
            }
            // bass root: one soft long note per bar
            val bStart = b * toSamples(bar)
            val bLen = toSamples(bar)
            for (i in 0 until bLen) {
                val t = i.toDouble() / SR
                val env = 0.7 + 0.3 * Math.sin(Math.PI * i.toDouble() / bLen)
                val v = kotlin.math.sin(2.0 * Math.PI * roots[b] / 2 * t) * env * 0.13
                val idx = bStart + i
                if (idx in 0 until totalN) out[idx] = (out[idx] + (v * Short.MAX_VALUE)).toInt().toShort()
            }
        }
        val tr = staticTrack(totalN)
        tr.write(out, 0, totalN)
        tr.setLoopPoints(0, totalN / 2 * 2, -1) // frame-aligned even loop
        tr.setVolume(0.6f)
        musicTrack = tr
    }

    private fun staticTrack(frames: Int): AudioTrack {
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SR)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        return AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(fmt)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(frames * 2)
            .build()
    }
}
