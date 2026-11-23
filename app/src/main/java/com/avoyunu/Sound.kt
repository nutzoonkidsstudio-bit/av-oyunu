package com.avoyunu

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sesler kodla üretilir. res/raw klasörüne aynı isimde dosya (shot0.ogg, reload.wav ...)
 * koyarsan üretilen ses yerine o dosya çalınır.
 * İsimler: shot0..shot9, reload, step, hit, kill, click, empty, buy
 */
class Sound(private val ctx: Context) {
    private val sr = 22050
    private val twoPi = (2 * PI).toFloat()
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val fmt = AudioFormat.Builder().setSampleRate(sr)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
    private val raw = HashMap<String, Int>()
    private val tracks = HashMap<String, AudioTrack>()
    private val checked = HashSet<String>()
    private var amb: AudioTrack? = null
    private var ambN = 0

    private val dur = intArrayOf(450, 520, 560, 420, 260, 200, 700, 300, 850, 900)
    private val freq = floatArrayOf(70f, 70f, 80f, 95f, 150f, 0f, 80f, 100f, 60f, 50f)
    private val nz = floatArrayOf(0.9f, 0.9f, 0.9f, 0.8f, 0.7f, 0.25f, 0.9f, 0.8f, 1f, 1f)

    @Synchronized
    fun play(n: String, vol: Float = 1f) {
        try {
            if (checked.add(n)) {
                val id = ctx.resources.getIdentifier(n, "raw", ctx.packageName)
                if (id != 0) raw[n] = pool.load(ctx, id, 1)
            }
            val r = raw[n]
            if (r != null) { pool.play(r, vol, vol, 1, 0, 1f); return }
            val t = tracks.getOrPut(n) { build(gen(n)) }
            t.stop(); t.reloadStaticData(); t.setVolume(vol); t.play()
        } catch (e: Exception) { }
    }

    @Synchronized
    fun ambient(on: Boolean) {
        try {
            if (!on) { amb?.stop(); return }
            if (amb == null) {
                ambN = sr * 3
                val a = ShortArray(ambN); var lp = 0f; val r = Random(3)
                for (i in 0 until ambN) {
                    lp += (r.nextFloat() * 2f - 1f - lp) * 0.02f
                    a[i] = (lp * 25000f * (0.6f + 0.4f * sin(twoPi * i / ambN))).toInt().toShort()
                }
                amb = build(a)
                amb!!.setVolume(0.35f)
            }
            amb!!.stop(); amb!!.reloadStaticData(); amb!!.setLoopPoints(0, ambN, -1); amb!!.play()
        } catch (e: Exception) { }
    }

    fun release() {
        try { pool.release(); tracks.values.forEach { it.release() }; amb?.release() } catch (e: Exception) { }
    }

    private fun build(data: ShortArray): AudioTrack {
        val t = AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(fmt)
            .setBufferSizeInBytes(data.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        t.write(data, 0, data.size)
        return t
    }

    private fun gen(n: String): ShortArray = when {
        n.startsWith("shot") -> { val i = n.substring(4).toInt(); gun(dur[i], freq[i], nz[i]) }
        n == "reload" -> clicks(floatArrayOf(0f, 0.35f, 0.7f), 0.9f)
        n == "step" -> thud(90, 90f, 0.5f)
        n == "hit" -> thud(140, 140f, 0.9f)
        n == "kill" -> thud(300, 60f, 1f)
        n == "empty" -> clicks(floatArrayOf(0f), 0.1f)
        n == "buy" -> tone(200, 660f)
        else -> tone(40, 900f)
    }

    private fun gun(ms: Int, f: Float, noise: Float): ShortArray {
        val n = sr * ms / 1000; val a = ShortArray(n); var lp = 0f; val r = Random(7)
        for (i in 0 until n) {
            val t = i / sr.toFloat()
            val env = exp(-t * (1000f / ms) * 4.5f)
            lp += (r.nextFloat() * 2f - 1f - lp) * 0.35f
            val s = (lp * noise + sin(twoPi * f * t) * 0.8f * exp(-t * 18f)) * env
            a[i] = (s.coerceIn(-1f, 1f) * 30000f).toInt().toShort()
        }
        return a
    }

    private fun thud(ms: Int, f: Float, amp: Float): ShortArray {
        val n = sr * ms / 1000; val a = ShortArray(n)
        for (i in 0 until n) {
            val t = i / sr.toFloat()
            val s = sin(twoPi * f * t * (1f - t * 2f)) * exp(-t * (1000f / ms) * 5f) * amp
            a[i] = (s.coerceIn(-1f, 1f) * 28000f).toInt().toShort()
        }
        return a
    }

    private fun tone(ms: Int, f: Float): ShortArray {
        val n = sr * ms / 1000; val a = ShortArray(n)
        for (i in 0 until n) {
            val t = i / sr.toFloat()
            a[i] = (sin(twoPi * f * t) * exp(-t * (1000f / ms) * 3f) * 20000f).toInt().toShort()
        }
        return a
    }

    private fun clicks(offs: FloatArray, total: Float): ShortArray {
        val a = ShortArray((sr * total).toInt()); val r = Random(11)
        for (o in offs) {
            val st = (o * sr).toInt()
            for (i in 0 until sr / 50) {
                if (st + i >= a.size) break
                val e = exp(-i / (sr / 400f))
                a[st + i] = ((r.nextFloat() * 2f - 1f) * e * 18000f).toInt().toShort()
            }
        }
        return a
    }
}
