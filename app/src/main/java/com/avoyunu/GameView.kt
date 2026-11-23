package com.avoyunu

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.*
import kotlin.random.Random

enum class Scr { MENU, LEVELS, WEAPONS, PLAY, RESULT }

class Obj(val t: Int, val x: Float, val z: Float, val w: Float, val h: Float, val seed: Int) {
    // t: 0 çam, 1 geniş yapraklı, 2 kuru ağaç, 3 kaktüs, 4 bina, 5 kaya, 6 ot, 7 çalı
    val solid = t in 0..5
    val rad = when (t) { 4 -> w / 2; 5 -> w * 0.5f; else -> 0.5f }
}

class Animal(val id: Int, val sp: Species, var x: Float, var z: Float) {
    var hp = sp.hp
    var heading = Random.nextFloat() * 6.28f
    var fleeT = 0f; var turnT = 0f; var idle = 0f; var spd = 0f
    var dead = false; var deadT = 0f; var alt = 0f
    var flap = Random.nextFloat() * 6f; var face = 1
}

class Btn(val r: RectF, val id: String)
class Pop(val text: String, var t: Float)
class Vis(val dep: Float, val lat: Float, val th: Any)

class GameView(private val ctx: Context) : SurfaceView(ctx), SurfaceHolder.Callback, Runnable {
    private val d = resources.displayMetrics.density
    private val prefs = ctx.getSharedPreferences("av", Context.MODE_PRIVATE)
    val snd = Sound(ctx)
    private var th: Thread? = null
    private val lock = Any()
    @Volatile private var alive = false
    private var W = 1080f
    private var H = 540f
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val path = Path()

    // ---- kalıcı veriler ----
    private var scr = Scr.MENU
    private var points = prefs.getInt("pts", START_POINTS)
    private var lvlMask = prefs.getInt("lv", 1)
    private var wpMask = prefs.getInt("wp", 1)
    private var equipped = prefs.getInt("eq", 0)
    private var selW = equipped
    private var cur = 0
    @Volatile private var btns: List<Btn> = emptyList()
    private var nb = ArrayList<Btn>()
    private var toast = ""
    private var toastT = 0f
    private val wp get() = WEAPONS[equipped]

    // ---- oyun durumu ----
    private var lv = LEVELS[0]
    private var objs = ArrayList<Obj>()
    private var animals = ArrayList<Animal>()
    private var px = 0f; private var pz = 0f; private var yaw = 0f; private var pitch = 0f
    private var joyId = -1; private var joyX0 = 0f; private var joyY0 = 0f
    private var joyX = 0f; private var joyY = 0f
    private var lookId = -1; private var lx = 0f; private var ly = 0f
    private var fireId = -1; private var firing = false
    private var scoped = false
    private var ammo = 0; private var reloading = false
    private var reloadEnd = 0L; private var lastShot = 0L
    private var timeLeft = 0f; private var earned = 0; private var kills = 0; private var lastEarned = 0
    private var recoil = 0f; private var stepT = 0f; private var spawnT = 0f; private var tm = 0f
    private var showMap = false
    private val pops = ArrayList<Pop>()
    private var skyShader: Shader? = null
    private val cl = FloatArray(24)
    private val rain = FloatArray(240)
    private var hyG = 0f
    private val sprites = HashMap<Int, Bitmap?>()

    // ---- ekran düğmeleri (oyun içi) ----
    private val fireX get() = W - 95 * d
    private val fireY get() = H - 95 * d
    private val scopeX get() = W - 95 * d
    private val scopeY get() = H - 190 * d
    private val relX get() = W - 185 * d
    private val relY get() = H - 60 * d
    private val exitX get() = W - 40 * d
    private val exitY get() = 40 * d

    init { holder.addCallback(this) }

    // =============== yaşam döngüsü ===============
    override fun surfaceCreated(h: SurfaceHolder) {
        alive = true
        th = Thread(this).also { it.start() }
        if (scr == Scr.PLAY) snd.ambient(true)
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
        W = w.toFloat(); H = hh.toFloat(); buildSky()
    }

    override fun surfaceDestroyed(h: SurfaceHolder) {
        alive = false
        try { th?.join() } catch (e: Exception) { }
        snd.ambient(false)
    }

    override fun run() {
        var last = SystemClock.uptimeMillis()
        while (alive) {
            val now = SystemClock.uptimeMillis()
            val dt = ((now - last) / 1000f).coerceAtMost(0.05f); last = now
            synchronized(lock) { update(dt) }
            var cv: Canvas? = null
            try {
                cv = if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
                if (cv != null) synchronized(lock) { render(cv) }
            } catch (e: Exception) {
            } finally {
                if (cv != null) try { holder.unlockCanvasAndPost(cv) } catch (e: Exception) { }
            }
            val sl = 16 - (SystemClock.uptimeMillis() - now)
            if (sl > 0) try { Thread.sleep(sl) } catch (e: Exception) { }
        }
    }

    fun onBack(): Boolean = synchronized(lock) { backImpl() }

    private fun backImpl(): Boolean {
        when (scr) {
            Scr.PLAY -> endLevel()
            Scr.LEVELS, Scr.WEAPONS -> scr = Scr.MENU
            Scr.RESULT -> scr = Scr.LEVELS
            Scr.MENU -> return false
        }
        return true
    }

    private fun save() {
        prefs.edit().putInt("pts", points).putInt("lv", lvlMask).putInt("wp", wpMask).putInt("eq", equipped).apply()
    }

    private fun showToast(s: String) { toast = s; toastT = 2.2f }
    private fun fmt(n: Int) = String.format(java.util.Locale.US, "%,d", n).replace(',', '.')

    // =============== bölüm başlat / bitir ===============
    private fun buildSky() {
        skyShader = LinearGradient(0f, 0f, 0f, H * 0.55f, lv.sky1, lv.sky2, Shader.TileMode.CLAMP)
    }

    private fun startLevel(i: Int) {
        cur = i; lv = LEVELS[i]
        val rnd = Random(i * 7919 + 13)
        val list = ArrayList<Obj>()
        val wsum = lv.mix.sum()
        repeat(lv.count) {
            var r = rnd.nextInt(wsum); var k = 0
            while (k < lv.mix.size - 1 && r >= lv.mix[k]) { r -= lv.mix[k]; k++ }
            val t = if (k == 6) 7 else k
            val x = 4f + rnd.nextFloat() * (MAP - 8f)
            val z = 4f + rnd.nextFloat() * (MAP - 8f)
            if (hypot(x - MAP / 2, z - MAP / 2) > 7f) {
                val a = rnd.nextFloat()
                val sz = when (t) {
                    0 -> Pair(2.6f + a, 9f + a * 7f)
                    1 -> Pair(4f + a * 2f, 7f + a * 3f)
                    2 -> Pair(2f + a, 6f + a * 3f)
                    3 -> Pair(1.4f, 3f + a * 1.5f)
                    4 -> Pair(7f + a * 8f, 10f + a * 26f)
                    5 -> Pair(2f + a * 5f, 1.4f + a * 3.5f)
                    else -> Pair(1.8f, 1.1f)
                }
                list.add(Obj(t, x, z, sz.first, sz.second, rnd.nextInt()))
            }
        }
        repeat(450) {
            list.add(Obj(6, 4f + rnd.nextFloat() * (MAP - 8f), 4f + rnd.nextFloat() * (MAP - 8f), 0.7f, 0.55f, rnd.nextInt()))
        }
        objs = list
        px = MAP / 2; pz = MAP / 2; yaw = 0f; pitch = 0f
        animals = ArrayList()
        repeat(32) { spawn() }
        ammo = wp.cap; reloading = false; scoped = false; showMap = false
        timeLeft = 300f; earned = 0; kills = 0; pops.clear()
        joyId = -1; lookId = -1; fireId = -1; firing = false; joyX = 0f; joyY = 0f
        for (j in 0 until 8) {
            cl[j * 3] = rnd.nextFloat() * 6.28f
            cl[j * 3 + 1] = 0.12f + rnd.nextFloat() * 0.25f
            cl[j * 3 + 2] = 0.12f + rnd.nextFloat() * 0.15f
        }
        for (j in rain.indices) rain[j] = Random.nextFloat()
        buildSky()
        scr = Scr.PLAY
        snd.ambient(true)
    }

    private fun endLevel() {
        firing = false; joyId = -1; lookId = -1; fireId = -1; joyX = 0f; joyY = 0f
        points += earned; lastEarned = earned
        save(); snd.ambient(false); scr = Scr.RESULT
    }

    private fun spawn() {
        val id = lv.animals.random()
        var x = 0f; var z = 0f; var tries = 0
        do {
            x = 6f + Random.nextFloat() * (MAP - 12f)
            z = 6f + Random.nextFloat() * (MAP - 12f)
            tries++
        } while (hypot(x - px, z - pz) < 45f && tries < 20)
        animals.add(Animal(id, SPECIES[id], x, z))
    }

    // =============== dokunma ===============
    override fun onTouchEvent(e: MotionEvent): Boolean = synchronized(lock) { handleTouch(e) }

    private fun handleTouch(e: MotionEvent): Boolean {
        if (scr != Scr.PLAY) {
            if (e.actionMasked == MotionEvent.ACTION_UP) tap(e.x, e.y)
            return true
        }
        val idx = e.actionIndex
        val id = e.getPointerId(idx)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(id, e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(id)
            MotionEvent.ACTION_CANCEL -> { for (i in 0 until e.pointerCount) up(e.getPointerId(i)) }
        }
        return true
    }

    private fun inC(x: Float, y: Float, cx: Float, cy: Float, r: Float) =
        (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r

    private fun down(id: Int, x: Float, y: Float) {
        if (showMap) { showMap = false; return }
        when {
            inC(x, y, fireX, fireY, 55 * d) -> { fireId = id; firing = true; tryFire() }
            inC(x, y, scopeX, scopeY, 34 * d) -> { scoped = !scoped; snd.play("click") }
            inC(x, y, relX, relY, 34 * d) -> startReload()
            inC(x, y, exitX, exitY, 30 * d) -> endLevel()
            inC(x, y, 70 * d, 70 * d, 58 * d) -> showMap = true
            x < W * 0.45f && joyId == -1 -> { joyId = id; joyX0 = x; joyY0 = y; joyX = 0f; joyY = 0f }
            lookId == -1 -> { lookId = id; lx = x; ly = y }
        }
    }

    private fun move(id: Int, x: Float, y: Float) {
        if (id == joyId) {
            var dx = (x - joyX0) / (60 * d); var dy = (y - joyY0) / (60 * d)
            val m = hypot(dx, dy)
            if (m > 1f) { dx /= m; dy /= m }
            joyX = dx; joyY = dy
        } else if (id == lookId) {
            val k = 0.0045f / (if (scoped) wp.zoom else 1f)
            yaw += (x - lx) * k
            pitch = (pitch - (y - ly) * k).coerceIn(-0.6f, 0.6f)
            lx = x; ly = y
        }
    }

    private fun up(id: Int) {
        if (id == joyId) { joyId = -1; joyX = 0f; joyY = 0f }
        if (id == lookId) lookId = -1
        if (id == fireId) { fireId = -1; firing = false }
    }

    private fun tap(x: Float, y: Float) {
        for (b in btns) if (b.r.contains(x, y)) { onBtn(b.id); return }
    }

    private fun onBtn(id: String) {
        snd.play("click")
        when {
            id == "play" -> scr = Scr.LEVELS
            id == "weapons" -> { selW = equipped; scr = Scr.WEAPONS }
            id == "quit" -> (ctx as? android.app.Activity)?.finish()
            id == "back" -> scr = Scr.MENU
            id == "cont" -> scr = Scr.LEVELS
            id.startsWith("p") && id.length == 2 -> startLevel(id.substring(1).toInt())
            id.startsWith("buyL") -> buyLevel(id.substring(4).toInt())
            id.startsWith("w") && id.length >= 2 && id[1].isDigit() -> selW = id.substring(1).toInt()
            id == "equip" -> { equipped = selW; save() }
            id == "buyW" -> buyWeapon(selW)
        }
    }

    private fun buyLevel(i: Int) {
        val l = LEVELS[i]
        if (points >= l.cost) {
            points -= l.cost; lvlMask = lvlMask or (1 shl i); save(); snd.play("buy"); showToast("${l.name} açıldı!")
        } else { snd.play("empty"); showToast("Yetersiz puan! ${fmt(l.cost - points)} puan daha gerek.") }
    }

    private fun buyWeapon(i: Int) {
        val w = WEAPONS[i]
        if (points >= w.cost) {
            points -= w.cost; wpMask = wpMask or (1 shl i); equipped = i; save(); snd.play("buy"); showToast("${w.name} satın alındı!")
        } else { snd.play("empty"); showToast("Yetersiz puan! ${fmt(w.cost - points)} puan daha gerek.") }
    }

    // =============== oyun mantığı ===============
    private fun focal() = W / (2f * tan(0.61f)) * (if (scoped) wp.zoom else 1f)

    private fun wrap(a: Float): Float {
        var x = a; val p = PI.toFloat()
        while (x > p) x -= 2 * p
        while (x < -p) x += 2 * p
        return x
    }

    private fun startReload() {
        if (reloading || ammo == wp.cap) return
        reloading = true; reloadEnd = SystemClock.uptimeMillis() + wp.reload
        snd.play("reload")
    }

    private fun tryFire() {
        val now = SystemClock.uptimeMillis(); val w = wp
        if (reloading || now - lastShot < w.delay) return
        if (ammo <= 0) { snd.play("empty"); startReload(); return }
        ammo--; lastShot = now; recoil = 1f
        snd.play("shot$equipped")
        for (a in animals) {
            if (!a.dead && hypot(a.x - px, a.z - pz) < w.noise && a.fleeT < 0.5f) {
                a.fleeT = 5f; a.heading = atan2(a.x - px, a.z - pz)
            }
        }
        val f = focal()
        val fx = sin(yaw); val fz = cos(yaw); val rx = cos(yaw); val rz = -sin(yaw)
        val hy = H * 0.5f + tan(pitch) * f
        var best: Animal? = null; var bd = 1e9f
        for (a in animals) {
            if (a.dead) continue
            val dx = a.x - px; val dz = a.z - pz
            val dep = dx * fx + dz * fz
            if (dep < 1f || dep > w.range || dep >= bd) continue
            val lat = dx * rx + dz * rz; val s = f / dep
            val sx = W / 2 + lat * s
            val base = hy + (CAMH - a.alt) * s
            val top = base - a.sp.h * s
            val tol = w.tol * H
            if (abs(W / 2 - sx) <= a.sp.len * s / 2 + tol && H / 2 >= top - tol && H / 2 <= base + tol) {
                var blocked = false
                for (o in objs) {
                    if (!o.solid || o.h < 2.2f) continue
                    val od = (o.x - px) * fx + (o.z - pz) * fz
                    if (od > 1f && od < dep && abs((o.x - px) * rx + (o.z - pz) * rz) < o.rad * 0.8f) { blocked = true; break }
                }
                if (!blocked) { best = a; bd = dep }
            }
        }
        val b = best
        if (b != null) {
            b.hp -= (w.dmg * (1f - 0.4f * bd / w.range)).toInt()
            b.fleeT = 5f
            if (b.hp <= 0) {
                b.dead = true; earned += b.sp.pts; kills++
                pops.add(Pop("+${b.sp.pts}  ${b.sp.name}", 2.5f)); snd.play("kill")
            } else snd.play("hit")
        }
        if (ammo == 0) startReload()
    }

    private fun update(dt: Float) {
        tm += dt
        if (toastT > 0f) toastT -= dt
        if (scr != Scr.PLAY) return
        timeLeft -= dt
        if (timeLeft <= 0f) { endLevel(); return }
        val now = SystemClock.uptimeMillis()
        var moving = false
        if (hypot(joyX, joyY) > 0.12f) {
            moving = true
            val sp = (if (scoped) 2.2f else 4.8f) * dt
            val fx = sin(yaw); val fz = cos(yaw); val rx = cos(yaw); val rz = -sin(yaw)
            var nx = px + (fx * (-joyY) + rx * joyX) * sp
            var nz = pz + (fz * (-joyY) + rz * joyX) * sp
            for (o in objs) {
                if (!o.solid) continue
                val dx = nx - o.x; val dz = nz - o.z; val r = o.rad + 0.45f; val dd = dx * dx + dz * dz
                if (dd < r * r && dd > 1e-4f) { val s = sqrt(dd); nx = o.x + dx / s * r; nz = o.z + dz / s * r }
            }
            px = nx.coerceIn(2f, MAP - 2f); pz = nz.coerceIn(2f, MAP - 2f)
            stepT += dt
            if (stepT > 0.5f) { stepT = 0f; snd.play("step", 0.4f) }
        }
        if (reloading && now >= reloadEnd) { reloading = false; ammo = wp.cap }
        if (firing && wp.auto) tryFire()
        recoil = max(0f, recoil - dt * 6f)

        val rx = cos(yaw); val rz = -sin(yaw)
        for (a in animals) {
            if (a.dead) { a.deadT += dt; continue }
            val dx = a.x - px; val dz = a.z - pz; val dist = hypot(dx, dz)
            val alert = (if (moving) 30f else 16f) * a.sp.skit
            if (a.fleeT <= 0f && dist < alert) {
                a.fleeT = 4f + Random.nextFloat() * 3f
                a.heading = atan2(dx, dz) + (Random.nextFloat() - 0.5f) * 1.2f
            }
            var spd: Float
            if (a.fleeT > 0f) { a.fleeT -= dt; spd = a.sp.speed }
            else {
                a.turnT -= dt
                if (a.turnT <= 0f) {
                    a.turnT = 2f + Random.nextFloat() * 4f
                    a.heading += (Random.nextFloat() - 0.5f) * 2.5f
                    a.idle = if (Random.nextFloat() < 0.4f) 1f else 0f
                }
                spd = if (a.idle > 0f) 0f else a.sp.speed * 0.22f
            }
            if (a.sp.kind == 1) spd = max(spd, 4f)
            val vx = sin(a.heading); val vz = cos(a.heading)
            a.x += vx * spd * dt; a.z += vz * spd * dt
            if (a.x < 3f || a.x > MAP - 3f || a.z < 3f || a.z > MAP - 3f) {
                a.heading += PI.toFloat(); a.x = a.x.coerceIn(3f, MAP - 3f); a.z = a.z.coerceIn(3f, MAP - 3f)
            }
            a.spd = spd
            if (a.sp.kind == 1) { a.alt = 2.5f + sin(tm * 0.8f + a.id) * 0.8f; a.flap += dt * 14f }
            else if (spd > 0.5f) a.flap += dt * (6f + spd)
            a.face = if (vx * rx + vz * rz >= 0f) 1 else -1
        }
        animals.removeAll { it.dead && it.deadT > 12f }
        spawnT += dt
        if (spawnT > 3f) { spawnT = 0f; if (animals.count { !it.dead } < 32) spawn() }

        var i = 0
        while (i < rain.size) {
            when (lv.weather) {
                1 -> { rain[i + 1] += dt * 1.8f; rain[i] -= dt * 0.08f }
                2 -> { rain[i + 1] += dt * 0.3f; rain[i] += sin(tm + i) * dt * 0.03f }
                3 -> { rain[i] -= dt * 0.9f; rain[i + 1] += sin(tm + i) * dt * 0.02f }
            }
            if (rain[i + 1] > 1f) rain[i + 1] -= 1f
            if (rain[i + 1] < 0f) rain[i + 1] += 1f
            if (rain[i] < 0f) rain[i] += 1f
            if (rain[i] > 1f) rain[i] -= 1f
            i += 2
        }
        val iter = pops.iterator()
        while (iter.hasNext()) { val p = iter.next(); p.t -= dt; if (p.t <= 0f) iter.remove() }
    }

    // =============== yardımcılar ===============
    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * (1 - k) + Color.red(b) * k).toInt(),
            (Color.green(a) * (1 - k) + Color.green(b) * k).toInt(),
            (Color.blue(a) * (1 - k) + Color.blue(b) * k).toInt()
        )
    }

    private fun shade(c: Int, k: Int) = Color.rgb(
        (Color.red(c) + k).coerceIn(0, 255), (Color.green(c) + k).coerceIn(0, 255), (Color.blue(c) + k).coerceIn(0, 255)
    )

    private fun fogc(c: Int, f: Float) = mix(c, lv.fog, f)
    private fun fill(c: Int) { pt.style = Paint.Style.FILL; pt.color = c }

    private fun line(cv: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, col: Int) {
        pt.style = Paint.Style.STROKE; pt.strokeWidth = max(1f, w); pt.strokeCap = Paint.Cap.ROUND; pt.color = col
        cv.drawLine(x1, y1, x2, y2, pt)
        pt.style = Paint.Style.FILL
    }

    private fun text(cv: Canvas, s: String, x: Float, y: Float, size: Int, col: Int, al: Paint.Align = Paint.Align.CENTER) {
        tx.textSize = size * d; tx.color = col; tx.textAlign = al
        cv.drawText(s, x, y, tx)
    }

    private fun button(cv: Canvas, id: String, r: RectF, label: String, bg: Int, sz: Int = 16, fg: Int = Color.WHITE) {
        fill(bg); cv.drawRoundRect(r, 10 * d, 10 * d, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 2 * d; pt.color = Color.argb(160, 255, 255, 255)
        cv.drawRoundRect(r, 10 * d, 10 * d, pt); pt.style = Paint.Style.FILL
        text(cv, label, r.centerX(), r.centerY() + sz * d * 0.35f, sz, fg)
        if (id.isNotEmpty()) nb.add(Btn(r, id))
    }

    private fun sprite(id: Int): Bitmap? {
        if (!sprites.containsKey(id)) {
            val rid = resources.getIdentifier("animal_$id", "drawable", ctx.packageName)
            sprites[id] = if (rid != 0) BitmapFactory.decodeResource(resources, rid) else null
        }
        return sprites[id]
    }

    // =============== çizim ===============
    private fun render(cv: Canvas) {
        nb = ArrayList()
        when (scr) {
            Scr.MENU -> drawMenu(cv)
            Scr.LEVELS -> drawLevels(cv)
            Scr.WEAPONS -> drawWeapons(cv)
            Scr.PLAY -> { drawWorld(cv); drawHud(cv) }
            Scr.RESULT -> drawResult(cv)
        }
        if (toastT > 0f && scr != Scr.PLAY) {
            fill(Color.argb(210, 0, 0, 0))
            cv.drawRoundRect(W / 2 - 200 * d, H - 56 * d, W / 2 + 200 * d, H - 18 * d, 10 * d, 10 * d, pt)
            text(cv, toast, W / 2, H - 30 * d, 14, Color.WHITE)
        }
        btns = nb
    }

    private fun bg(cv: Canvas) {
        pt.shader = LinearGradient(0f, 0f, 0f, H, 0xFF1B2B22.toInt(), 0xFF3A2E20.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, W, H, pt); pt.shader = null
        val r = Random(5)
        for (i in 0 until 18) {
            val x = r.nextFloat() * W; val h = (60 + r.nextFloat() * 120) * d
            fill(Color.argb(120, 10, 25, 15))
            path.reset(); path.moveTo(x - h * 0.2f, H); path.lineTo(x, H - h); path.lineTo(x + h * 0.2f, H); path.close()
            cv.drawPath(path, pt)
        }
    }

    private fun drawMenu(cv: Canvas) {
        bg(cv)
        text(cv, "AV OYUNU", W / 2, H * 0.27f, 46, 0xFFF3E3B0.toInt())
        text(cv, "TOPLAM PUAN: ${fmt(points)}", W / 2, H * 0.27f + 32 * d, 16, Color.WHITE)
        val bw = 230 * d; val bh = 46 * d; val x = W / 2 - bw / 2; var y = H * 0.45f
        button(cv, "play", RectF(x, y, x + bw, y + bh), "BAŞLA", 0xFF2E7D32.toInt(), 20); y += bh + 12 * d
        button(cv, "weapons", RectF(x, y, x + bw, y + bh), "SİLAHLAR", 0xFF6D4C41.toInt(), 20); y += bh + 12 * d
        button(cv, "quit", RectF(x, y, x + bw, y + bh), "ÇIKIŞ", 0xFF424242.toInt(), 18)
    }

    private fun lock(cv: Canvas, cx: Float, cy: Float, s: Float) {
        fill(0xFFE0E0E0.toInt())
        cv.drawRoundRect(cx - s * 0.6f, cy - s * 0.1f, cx + s * 0.6f, cy + s * 0.7f, s * 0.12f, s * 0.12f, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = s * 0.2f; pt.color = 0xFFE0E0E0.toInt()
        cv.drawArc(cx - s * 0.38f, cy - s * 0.7f, cx + s * 0.38f, cy + s * 0.2f, 180f, 180f, false, pt)
        pt.style = Paint.Style.FILL
    }

    private fun drawLevels(cv: Canvas) {
        bg(cv)
        button(cv, "back", RectF(12 * d, 10 * d, 100 * d, 48 * d), "< GERİ", 0xFF5D4037.toInt(), 14)
        text(cv, "BÖLÜM SEÇME", W / 2, 40 * d, 24, 0xFFF3E3B0.toInt())
        text(cv, "TOPLAM PUAN: ${fmt(points)}", W - 14 * d, 38 * d, 14, Color.WHITE, Paint.Align.RIGHT)
        val pad = 12 * d; val top = 62 * d
        val cw = (W - 4 * pad) / 3; val ch = (H - top - 2 * pad) / 2
        for (i in LEVELS.indices) {
            val l = LEVELS[i]
            val x = pad + (i % 3) * (cw + pad); val y = top + (i / 3) * (ch + pad)
            val un = (lvlMask and (1 shl i)) != 0
            pt.shader = LinearGradient(0f, y, 0f, y + ch * 0.55f, l.sky1, l.sky2, Shader.TileMode.CLAMP)
            cv.drawRect(x, y, x + cw, y + ch * 0.55f, pt); pt.shader = null
            fill(l.ground); cv.drawRect(x, y + ch * 0.55f, x + cw, y + ch, pt)
            fill(l.sil); cv.drawRect(x, y + ch * 0.5f, x + cw, y + ch * 0.56f, pt)
            for (k in 0 until 6) {
                val tx0 = x + cw * (0.1f + 0.15f * k); val th0 = ch * (0.18f + 0.04f * (k % 3))
                fill(l.foliage); path.reset()
                path.moveTo(tx0 - th0 * 0.25f, y + ch * 0.62f); path.lineTo(tx0, y + ch * 0.62f - th0); path.lineTo(tx0 + th0 * 0.25f, y + ch * 0.62f)
                path.close(); cv.drawPath(path, pt)
            }
            fill(Color.argb(160, 0, 0, 0)); cv.drawRect(x, y + ch * 0.64f, x + cw, y + ch, pt)
            text(cv, l.name, x + cw / 2, y + ch * 0.64f + 22 * d, 16, Color.WHITE)
            if (un) {
                text(cv, "AÇIK", x + 12 * d, y + ch - 12 * d, 12, 0xFF81C784.toInt(), Paint.Align.LEFT)
                button(cv, "p$i", RectF(x + cw - 76 * d, y + ch - 32 * d, x + cw - 8 * d, y + ch - 6 * d), "BAŞLA", 0xFF2E7D32.toInt(), 12)
            } else {
                fill(Color.argb(120, 0, 0, 0)); cv.drawRect(x, y, x + cw, y + ch * 0.64f, pt)
                lock(cv, x + cw / 2, y + ch * 0.3f, 20 * d)
                text(cv, "KİLİTLİ - ${fmt(l.cost)} PUAN", x + cw / 2, y + ch - 12 * d, 12, 0xFFFFCC80.toInt())
                nb.add(Btn(RectF(x, y, x + cw, y + ch), "buyL$i"))
            }
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 2 * d; pt.color = Color.argb(180, 230, 230, 230)
            cv.drawRect(x, y, x + cw, y + ch, pt); pt.style = Paint.Style.FILL
        }
    }

    // Silah çizimi: (x0,y0) dipçik, (x1,y1) namlu ucu
    private fun drawGun(cv: Canvas, w: Weapon, x0: Float, y0: Float, x1: Float, y1: Float) {
        val dx = x1 - x0; val dy = y1 - y0; val ln = hypot(dx, dy)
        val nx = -dy / ln; val ny = dx / ln
        fun gx(t: Float) = x0 + dx * t
        fun gy(t: Float) = y0 + dy * t
        val wood = 0xFF6B3B1E.toInt(); val steel = 0xFF2B2B2B.toInt()
        when (w.kind) {
            3 -> {
                line(cv, gx(0.3f), gy(0.3f), gx(0.7f), gy(0.7f), 3 * d, 0xFFE0E0E0.toInt())
                pt.style = Paint.Style.STROKE; pt.strokeWidth = 7 * d; pt.color = wood
                cv.drawArc(gx(0.7f) - ln * 0.25f, gy(0.7f) - ln * 0.25f, gx(0.7f) + ln * 0.25f, gy(0.7f) + ln * 0.25f, 0f, 360f, false, pt)
                pt.style = Paint.Style.FILL
            }
            2 -> {
                line(cv, x0, y0, gx(0.45f), gy(0.45f), 18 * d, wood)
                line(cv, gx(0.4f), gy(0.4f), x1, y1, 12 * d, steel)
            }
            else -> {
                line(cv, x0, y0, gx(0.35f), gy(0.35f), 20 * d, wood)
                if (w.kind == 0) {
                    line(cv, gx(0.3f) + nx * 5 * d, gy(0.3f) + ny * 5 * d, x1 + nx * 5 * d, y1 + ny * 5 * d, 7 * d, steel)
                    line(cv, gx(0.3f) - nx * 5 * d, gy(0.3f) - ny * 5 * d, x1 - nx * 5 * d, y1 - ny * 5 * d, 7 * d, steel)
                } else {
                    line(cv, gx(0.3f), gy(0.3f), x1, y1, 9 * d, steel)
                    if (w.zoom >= 1.8f) line(cv, gx(0.4f) + nx * 12 * d, gy(0.4f) + ny * 12 * d, gx(0.7f) + nx * 12 * d, gy(0.7f) + ny * 12 * d, 8 * d, 0xFF111111.toInt())
                }
            }
        }
        fill(0xFF4E5B3A.toInt())
        cv.drawCircle(gx(0.55f), gy(0.55f), 12 * d, pt)
        cv.drawCircle(gx(0.12f), gy(0.12f), 12 * d, pt)
    }

    private fun bar(cv: Canvas, label: String, v: Int, x: Float, y: Float, w: Float) {
        text(cv, label, x, y, 12, Color.WHITE, Paint.Align.LEFT)
        val sw = (w * 0.5f - 4 * d) / 5
        for (i in 0 until 5) {
            fill(if (i < v) 0xFFFFB300.toInt() else 0xFF4E4E4E.toInt())
            cv.drawRect(x + w * 0.5f + i * (sw + 1 * d), y - 11 * d, x + w * 0.5f + i * (sw + 1 * d) + sw, y, pt)
        }
    }

    private fun drawWeapons(cv: Canvas) {
        bg(cv)
        button(cv, "back", RectF(12 * d, 10 * d, 100 * d, 48 * d), "< GERİ", 0xFF5D4037.toInt(), 14)
        text(cv, "SİLAH SEÇİMİ", W * 0.4f, 40 * d, 24, 0xFFF3E3B0.toInt())
        text(cv, "TOPLAM PUAN: ${fmt(points)}", W - 14 * d, 38 * d, 14, Color.WHITE, Paint.Align.RIGHT)
        val top = 58 * d; val pad = 8 * d; val lw = W * 0.56f
        val cw = (lw - 3 * pad) / 2; val ch = (H - top - pad) / 5 - pad
        for (i in WEAPONS.indices) {
            val w = WEAPONS[i]
            val x = pad + (i % 2) * (cw + pad); val y = top + (i / 2) * (ch + pad)
            val own = (wpMask and (1 shl i)) != 0
            fill(if (i == selW) 0xFF3E5C3A.toInt() else 0xFF2A2F2A.toInt())
            val r = RectF(x, y, x + cw, y + ch); cv.drawRoundRect(r, 8 * d, 8 * d, pt)
            if (i == selW) { pt.style = Paint.Style.STROKE; pt.strokeWidth = 2 * d; pt.color = 0xFF81C784.toInt(); cv.drawRoundRect(r, 8 * d, 8 * d, pt); pt.style = Paint.Style.FILL }
            text(cv, w.name, x + 10 * d, y + ch * 0.45f, 12, Color.WHITE, Paint.Align.LEFT)
            val st = if (i == equipped) "KUŞANILDI" else if (own) "SAHİP" else "${fmt(w.cost)} PUAN"
            val sc = if (i == equipped) 0xFF81C784.toInt() else if (own) 0xFFB0BEC5.toInt() else 0xFFFFCC80.toInt()
            text(cv, st, x + 10 * d, y + ch * 0.8f, 11, sc, Paint.Align.LEFT)
            if (!own) lock(cv, x + cw - 18 * d, y + ch * 0.4f, 9 * d)
            nb.add(Btn(r, "w$i"))
        }
        val w = WEAPONS[selW]; val own = (wpMask and (1 shl selW)) != 0
        val rx0 = lw + 2 * pad; val rx1 = W - 12 * d
        fill(Color.argb(120, 0, 0, 0)); cv.drawRoundRect(rx0, top, rx1, H - pad, 12 * d, 12 * d, pt)
        text(cv, w.name, (rx0 + rx1) / 2, top + 26 * d, 17, Color.WHITE)
        drawGun(cv, w, rx0 + 24 * d, top + 100 * d, rx1 - 24 * d, top + 62 * d)
        val bw = rx1 - rx0 - 28 * d; val bx = rx0 + 14 * d; var by = top + 130 * d
        bar(cv, "HASAR", w.dmgStars, bx, by, bw); by += 22 * d
        bar(cv, "MENZİL", w.rangeStars, bx, by, bw); by += 22 * d
        bar(cv, "ATIŞ HIZI", w.rateStars, bx, by, bw); by += 22 * d
        text(cv, "KAPASİTE: ${w.cap}/${w.cap}", bx, by, 12, Color.WHITE, Paint.Align.LEFT)
        val br = RectF(bx, H - pad - 52 * d, rx1 - 14 * d, H - pad - 10 * d)
        when {
            selW == equipped -> button(cv, "", br, "KUŞANILDI", 0xFF2E7D32.toInt(), 16)
            own -> button(cv, "equip", br, "KUŞAN", 0xFF2E7D32.toInt(), 16)
            else -> button(cv, "buyW", br, "SATIN AL - ${fmt(w.cost)}", if (points >= w.cost) 0xFF1565C0.toInt() else 0xFF616161.toInt(), 14)
        }
    }

    private fun drawResult(cv: Canvas) {
        bg(cv)
        fill(Color.argb(150, 0, 0, 0)); cv.drawRoundRect(W / 2 - 190 * d, H * 0.15f, W / 2 + 190 * d, H * 0.85f, 14 * d, 14 * d, pt)
        text(cv, "BÖLÜM BİTTİ", W / 2, H * 0.15f + 42 * d, 28, 0xFFF3E3B0.toInt())
        text(cv, "Avlanan hayvan: $kills", W / 2, H * 0.15f + 82 * d, 18, Color.WHITE)
        text(cv, "Kazanılan puan: +${fmt(lastEarned)}", W / 2, H * 0.15f + 112 * d, 18, 0xFFFFCC80.toInt())
        text(cv, "Toplam puan: ${fmt(points)}", W / 2, H * 0.15f + 142 * d, 18, Color.WHITE)
        button(cv, "cont", RectF(W / 2 - 90 * d, H * 0.85f - 62 * d, W / 2 + 90 * d, H * 0.85f - 14 * d), "DEVAM", 0xFF2E7D32.toInt(), 20)
    }

    private fun skyH(a: Float): Float = when (lv.skyline) {
        0 -> 0.035f + 0.03f * (sin(a * 5f) * 0.5f + sin(a * 11f + 1f) * 0.3f + 0.5f)
        1 -> 0.06f + 0.12f * abs(sin(a * 3f + 0.5f)) * (0.6f + 0.4f * sin(a * 8f))
        2 -> if (sin(a * 4f) > 0.2f) 0.09f else 0.03f
        else -> 0.03f + 0.07f * ((floor(a * 14f).toInt() and 7) / 7f)
    }

    private fun drawWorld(cv: Canvas) {
        val f = focal(); val zf = if (scoped) wp.zoom else 1f
        val hy = H * 0.5f + tan(pitch) * f; hyG = hy
        pt.shader = skyShader; cv.drawRect(0f, 0f, W, H, pt); pt.shader = null
        if (lv.weather != 1) {
            val a = wrap(1.0f - yaw)
            if (abs(a) < 1.2f) {
                val x = W / 2 + tan(a) * f; val y = hy - H * 0.3f * zf
                fill(Color.argb(60, 255, 240, 200)); cv.drawCircle(x, y, 60 * d, pt)
                fill(Color.argb(230, 255, 250, 225)); cv.drawCircle(x, y, 22 * d, pt)
            }
        }
        for (j in 0 until 8) {
            val a = wrap(cl[j * 3] - yaw)
            if (abs(a) > 1.1f) continue
            val x = W / 2 + tan(a) * f; val y = hy - cl[j * 3 + 1] * H * zf; val sz = cl[j * 3 + 2] * W * zf
            fill(if (lv.weather == 1) Color.argb(170, 110, 118, 126) else Color.argb(130, 255, 255, 255))
            cv.drawOval(x - sz / 2, y - sz * 0.1f, x + sz / 2, y + sz * 0.1f, pt)
            cv.drawOval(x - sz * 0.3f, y - sz * 0.18f, x + sz * 0.25f, y + sz * 0.05f, pt)
            cv.drawOval(x - sz * 0.05f, y - sz * 0.14f, x + sz * 0.45f, y + sz * 0.08f, pt)
        }
        path.reset(); path.moveTo(0f, hy)
        var xx = 0f
        while (xx <= W + 12f) {
            val ang = yaw + atan((xx - W / 2) / f)
            path.lineTo(xx, hy - skyH(ang) * H * zf); xx += 10f
        }
        path.lineTo(W + 12f, hy); path.close(); fill(lv.sil); cv.drawPath(path, pt)
        pt.shader = LinearGradient(0f, hy, 0f, hy + H * 0.55f, lv.fog, lv.ground, Shader.TileMode.CLAMP)
        cv.drawRect(0f, hy, W, H, pt); pt.shader = null

        val fx = sin(yaw); val fz = cos(yaw); val rx = cos(yaw); val rz = -sin(yaw)
        val vis = ArrayList<Vis>()
        for (o in objs) {
            val dx = o.x - px; val dz = o.z - pz
            val dep = dx * fx + dz * fz
            if (dep < 0.8f) continue
            val maxD = if (o.t == 6) 45f else if (o.t == 4) 220f else 140f
            if (dep > maxD) continue
            val lat = dx * rx + dz * rz
            if (abs(lat) * f / dep > W / 2 + o.w * f / dep) continue
            vis.add(Vis(dep, lat, o))
        }
        for (a in animals) {
            val dx = a.x - px; val dz = a.z - pz
            val dep = dx * fx + dz * fz
            if (dep < 0.8f || dep > 200f) continue
            val lat = dx * rx + dz * rz
            if (abs(lat) * f / dep > W / 2 + 80f) continue
            vis.add(Vis(dep, lat, a))
        }
        vis.sortByDescending { it.dep }
        for (v in vis) {
            val s = f / v.dep; val sx = W / 2 + v.lat * s
            val fg = ((v.dep - 20f) / lv.fogD).coerceIn(0f, 1f) * 0.85f
            val th = v.th
            if (th is Obj) drawObj(cv, th, sx, s, fg) else if (th is Animal) drawAnimal(cv, th, sx, s, fg)
        }
        when (lv.weather) {
            1 -> {
                fill(Color.argb(40, 20, 30, 40)); cv.drawRect(0f, 0f, W, H, pt)
                var i = 0
                while (i < rain.size) {
                    val x = rain[i] * W; val y = rain[i + 1] * H
                    line(cv, x, y, x - 5 * d, y + 18 * d, 1.2f * d, Color.argb(140, 200, 215, 230)); i += 2
                }
            }
            2 -> {
                var i = 0
                while (i < rain.size) { fill(Color.argb(220, 255, 255, 255)); cv.drawCircle(rain[i] * W, rain[i + 1] * H, (1.5f + (i % 4) * 0.5f) * d, pt); i += 2 }
            }
            3 -> {
                fill(Color.argb(45, 230, 190, 120)); cv.drawRect(0f, 0f, W, H, pt)
                var i = 0
                while (i < rain.size) { val x = rain[i] * W; val y = rain[i + 1] * H; line(cv, x, y, x - 30 * d, y + 2 * d, 1.2f * d, Color.argb(90, 240, 210, 150)); i += 4 }
            }
        }
    }

    private fun drawObj(cv: Canvas, o: Obj, sx: Float, s: Float, fg: Float) {
        val base = hyG + CAMH * s; val w = o.w * s; val h = o.h * s
        val sh = (((o.seed ushr 3) and 15) - 8) * 2
        val fol = fogc(shade(lv.foliage, sh), fg); val trk = fogc(shade(lv.trunk, sh), fg)
        val snow = fogc(0xFFF4F8FB.toInt(), fg)
        when (o.t) {
            0 -> {
                fill(trk); cv.drawRect(sx - w * 0.06f, base - h * 0.25f, sx + w * 0.06f, base, pt)
                for (k in 0..2) {
                    val bl = base - h * (0.14f + 0.24f * k); val ap = bl - h * 0.36f; val hw = w * (0.5f - 0.1f * k)
                    fill(fol); path.reset(); path.moveTo(sx - hw, bl); path.lineTo(sx, ap); path.lineTo(sx + hw, bl); path.close(); cv.drawPath(path, pt)
                    if (lv.snow) {
                        val cy = ap + (bl - ap) * 0.45f
                        fill(snow); path.reset(); path.moveTo(sx - hw * 0.55f, cy); path.lineTo(sx, ap); path.lineTo(sx + hw * 0.55f, cy); path.close(); cv.drawPath(path, pt)
                    }
                }
            }
            1 -> {
                fill(trk); cv.drawRect(sx - w * 0.07f, base - h * 0.55f, sx + w * 0.07f, base, pt)
                fill(fol)
                cv.drawCircle(sx, base - h * 0.72f, w * 0.36f, pt)
                cv.drawCircle(sx - w * 0.26f, base - h * 0.58f, w * 0.26f, pt)
                cv.drawCircle(sx + w * 0.27f, base - h * 0.6f, w * 0.25f, pt)
                fill(fogc(shade(lv.foliage, sh + 25), fg)); cv.drawCircle(sx - w * 0.08f, base - h * 0.8f, w * 0.2f, pt)
            }
            2 -> {
                fill(trk); cv.drawRect(sx - w * 0.06f, base - h * 0.8f, sx + w * 0.06f, base, pt)
                line(cv, sx, base - h * 0.55f, sx - w * 0.4f, base - h * 0.85f, w * 0.05f, trk)
                line(cv, sx, base - h * 0.65f, sx + w * 0.4f, base - h * 0.95f, w * 0.05f, trk)
                line(cv, sx, base - h * 0.8f, sx - w * 0.2f, base - h, w * 0.04f, trk)
                if (lv.snow) { fill(snow); cv.drawRect(sx - w * 0.06f, base - h * 0.82f, sx + w * 0.06f, base - h * 0.78f, pt) }
            }
            3 -> {
                fill(fol); cv.drawRoundRect(sx - w * 0.18f, base - h, sx + w * 0.18f, base, w * 0.18f, w * 0.18f, pt)
                line(cv, sx - w * 0.18f, base - h * 0.4f, sx - w * 0.42f, base - h * 0.4f, w * 0.18f, fol)
                line(cv, sx - w * 0.42f, base - h * 0.4f, sx - w * 0.42f, base - h * 0.68f, w * 0.18f, fol)
                line(cv, sx + w * 0.18f, base - h * 0.55f, sx + w * 0.42f, base - h * 0.55f, w * 0.18f, fol)
                line(cv, sx + w * 0.42f, base - h * 0.55f, sx + w * 0.42f, base - h * 0.8f, w * 0.18f, fol)
            }
            4 -> {
                fill(fogc(shade(0xFF6B7075.toInt(), sh), fg)); cv.drawRect(sx - w / 2, base - h, sx + w / 2, base, pt)
                fill(fogc(0xFF2B3036.toInt(), fg)); cv.drawRect(sx - w / 2, base - h, sx + w / 2, base - h + h * 0.03f, pt)
                if (w > 14f) {
                    val rows = ((o.h / 3.2f).toInt()).coerceIn(2, 9)
                    for (r in 0 until rows) for (c in 0 until 3) {
                        fill(fogc(if (((o.seed shr (r + c)) and 1) == 0) 0xFF202A33.toInt() else 0xFF8FA6B8.toInt(), fg))
                        val wx = sx - w / 2 + w * (0.12f + 0.3f * c); val wy = base - h + h * (0.08f + 0.9f / rows * r)
                        cv.drawRect(wx, wy, wx + w * 0.18f, wy + h * 0.5f / rows, pt)
                    }
                }
            }
            5 -> {
                fill(fogc(shade(0xFF7A7D80.toInt(), sh), fg)); path.reset()
                path.moveTo(sx - w / 2, base); path.lineTo(sx - w * 0.4f, base - h * 0.6f); path.lineTo(sx - w * 0.1f, base - h)
                path.lineTo(sx + w * 0.3f, base - h * 0.8f); path.lineTo(sx + w / 2, base); path.close(); cv.drawPath(path, pt)
                if (lv.snow) {
                    fill(snow); path.reset(); path.moveTo(sx - w * 0.3f, base - h * 0.75f); path.lineTo(sx - w * 0.1f, base - h)
                    path.lineTo(sx + w * 0.25f, base - h * 0.82f); path.lineTo(sx, base - h * 0.7f); path.close(); cv.drawPath(path, pt)
                }
            }
            6 -> {
                val g = fogc(shade(lv.foliage, 20 + sh), fg)
                for (k in -1..1) line(cv, sx + k * w * 0.3f, base, sx + k * w * 0.45f, base - h, max(1f, w * 0.1f), g)
            }
            else -> {
                fill(fogc(shade(lv.foliage, 15 + sh), fg)); cv.drawOval(sx - w / 2, base - h, sx + w / 2, base, pt)
            }
        }
    }

    private fun drawAnimal(cv: Canvas, a: Animal, sx: Float, s: Float, fg: Float) {
        val sp = a.sp; val base = hyG + (CAMH - a.alt) * s
        val h = sp.h * s; val len = sp.len * s; val fc = a.face.toFloat()
        val col = fogc(sp.color, fg); val dk = fogc(shade(sp.color, -45), fg)
        val bmp = sprite(a.id)
        if (bmp != null) {
            val hh = h * 1.15f; val ww = hh * bmp.width / bmp.height
            cv.save(); cv.scale(fc, 1f, sx, base)
            if (a.dead) cv.rotate(90f, sx, base)
            pt.style = Paint.Style.FILL; pt.color = Color.WHITE
            cv.drawBitmap(bmp, null, RectF(sx - ww / 2, base - hh, sx + ww / 2, base), pt)
            cv.restore(); return
        }
        if (a.dead) {
            fill(col); cv.drawOval(sx - len * 0.5f, base - h * 0.32f, sx + len * 0.5f, base, pt)
            fill(dk); cv.drawCircle(sx + fc * len * 0.5f, base - h * 0.12f, h * 0.12f, pt)
            return
        }
        when (sp.kind) {
            1 -> {
                val cy = base - h * 0.5f; val wing = sin(a.flap) * h * 0.9f
                line(cv, sx, cy, sx - len * 0.55f, cy - wing, h * 0.16f, dk)
                line(cv, sx, cy, sx + len * 0.55f, cy - wing, h * 0.16f, dk)
                fill(col); cv.drawOval(sx - len * 0.4f, cy - h * 0.22f, sx + len * 0.4f, cy + h * 0.22f, pt)
                cv.drawCircle(sx + fc * len * 0.42f, cy - h * 0.12f, h * 0.15f, pt)
                line(cv, sx + fc * len * 0.5f, cy - h * 0.12f, sx + fc * len * 0.68f, cy - h * 0.08f, h * 0.06f, Color.rgb(230, 170, 40))
            }
            2 -> {
                val cy = base - h * 0.32f
                fill(col); cv.drawOval(sx - len * 0.5f, cy - h * 0.3f, sx + len * 0.5f, cy + h * 0.3f, pt)
                val hx = sx + fc * len * 0.42f; val hyy = base - h * 0.62f
                cv.drawCircle(hx, hyy, h * 0.2f, pt)
                line(cv, hx - fc * h * 0.05f, hyy - h * 0.1f, hx - fc * h * 0.12f, hyy - h * 0.55f, h * 0.08f, col)
                line(cv, hx + fc * h * 0.02f, hyy - h * 0.1f, hx - fc * h * 0.02f, hyy - h * 0.55f, h * 0.08f, col)
                fill(fogc(0xFFF5F5F5.toInt(), fg)); cv.drawCircle(sx - fc * len * 0.5f, cy - h * 0.05f, h * 0.1f, pt)
            }
            else -> {
                val cy = base - h * 0.52f; val bh = h * 0.42f
                val swing = sin(a.flap) * len * 0.06f
                val offs = floatArrayOf(-0.34f, -0.2f, 0.2f, 0.34f)
                for (i in 0 until 4) {
                    val sw = if (i % 2 == 0) swing else -swing
                    line(cv, sx + offs[i] * len, cy, sx + offs[i] * len + sw, base, h * 0.09f, dk)
                }
                fill(col); cv.drawOval(sx - len * 0.5f, cy - bh / 2, sx + len * 0.5f, cy + bh / 2, pt)
                if (sp.kind == 3) cv.drawCircle(sx, cy - bh * 0.55f, bh * 0.35f, pt)
                val hx = sx + fc * len * (if (sp.kind == 3) 0.5f else 0.58f)
                val hyy = base - h * (if (sp.kind == 3) 0.92f else 0.76f)
                line(cv, sx + fc * len * 0.36f, cy - bh * 0.2f, hx, hyy, h * 0.16f, col)
                fill(col); cv.drawOval(hx - h * 0.15f, hyy - h * 0.09f, hx + h * 0.15f, hyy + h * 0.09f, pt)
                val tan = fogc(0xFFD8C7A0.toInt(), fg)
                when (sp.horns) {
                    1 -> {
                        line(cv, hx, hyy - h * 0.08f, hx - fc * h * 0.05f, hyy - h * 0.38f, h * 0.03f, tan)
                        line(cv, hx - fc * h * 0.05f, hyy - h * 0.22f, hx + fc * h * 0.12f, hyy - h * 0.38f, h * 0.03f, tan)
                        line(cv, hx - fc * h * 0.05f, hyy - h * 0.3f, hx - fc * h * 0.2f, hyy - h * 0.42f, h * 0.03f, tan)
                    }
                    2 -> {
                        line(cv, hx, hyy - h * 0.08f, hx - fc * h * 0.08f, hyy - h * 0.25f, h * 0.04f, tan)
                        line(cv, hx - fc * h * 0.08f, hyy - h * 0.25f, hx - fc * h * 0.2f, hyy - h * 0.28f, h * 0.04f, tan)
                    }
                    3 -> line(cv, hx + fc * h * 0.12f, hyy + h * 0.03f, hx + fc * h * 0.2f, hyy - h * 0.05f, h * 0.03f, Color.WHITE)
                }
                line(cv, sx - fc * len * 0.5f, cy - bh * 0.1f, sx - fc * len * 0.62f, cy + bh * 0.1f, h * 0.06f, dk)
            }
        }
    }

    private fun arrow(cv: Canvas, x: Float, y: Float, ya: Float, s: Float) {
        path.reset()
        path.moveTo(x + sin(ya) * s, y - cos(ya) * s)
        path.lineTo(x + sin(ya - 2.5f) * s * 0.8f, y - cos(ya - 2.5f) * s * 0.8f)
        path.lineTo(x + sin(ya + 2.5f) * s * 0.8f, y - cos(ya + 2.5f) * s * 0.8f)
        path.close(); fill(0xFFFFEB3B.toInt()); cv.drawPath(path, pt)
    }

    private fun objColor(t: Int) = when (t) {
        0, 1 -> 0xFF2E7D32.toInt(); 2 -> 0xFF8D6E63.toInt(); 3 -> 0xFF7CB342.toInt()
        4 -> 0xFF9E9E9E.toInt(); 5 -> 0xFFBDBDBD.toInt(); else -> 0xFF558B2F.toInt()
    }

    private fun miniMap(cv: Canvas) {
        val cx = 70 * d; val cy = 70 * d; val r = 58 * d; val range = 60f; val sc = r / range
        fill(Color.argb(150, 0, 0, 0)); cv.drawCircle(cx, cy, r, pt)
        cv.save(); path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW); cv.clipPath(path)
        val fx = sin(yaw); val fz = cos(yaw); val rx = cos(yaw); val rz = -sin(yaw)
        for (o in objs) {
            if (o.t == 6) continue
            val dx = o.x - px; val dz = o.z - pz
            val dep = dx * fx + dz * fz; val lat = dx * rx + dz * rz
            if (abs(lat) > range || abs(dep) > range) continue
            fill(objColor(o.t)); cv.drawCircle(cx + lat * sc, cy - dep * sc, max(1.5f * d, o.w * sc * 0.4f), pt)
        }
        for (a in animals) {
            if (a.dead) continue
            val dx = a.x - px; val dz = a.z - pz
            val dep = dx * fx + dz * fz; val lat = dx * rx + dz * rz
            if (abs(lat) > range || abs(dep) > range) continue
            fill(0xFFFF5252.toInt()); cv.drawCircle(cx + lat * sc, cy - dep * sc, 3 * d, pt)
        }
        cv.restore()
        arrow(cv, cx, cy, 0f, 9 * d)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 2 * d; pt.color = Color.argb(200, 255, 255, 255)
        cv.drawCircle(cx, cy, r, pt); pt.style = Paint.Style.FILL
    }

    private fun fullMap(cv: Canvas) {
        fill(Color.argb(200, 0, 0, 0)); cv.drawRect(0f, 0f, W, H, pt)
        val S = min(W, H) * 0.86f; val x0 = W / 2 - S / 2; val y0 = H / 2 - S / 2; val k = S / MAP
        fill(mix(lv.ground, Color.BLACK, 0.3f)); cv.drawRect(x0, y0, x0 + S, y0 + S, pt)
        for (o in objs) {
            if (o.t == 6) continue
            fill(objColor(o.t)); cv.drawCircle(x0 + o.x * k, y0 + S - o.z * k, max(1.5f, o.w * k * 0.5f), pt)
        }
        for (a in animals) {
            if (a.dead || hypot(a.x - px, a.z - pz) > 70f) continue
            fill(0xFFFF5252.toInt()); cv.drawCircle(x0 + a.x * k, y0 + S - a.z * k, 3 * d, pt)
        }
        arrow(cv, x0 + px * k, y0 + S - pz * k, yaw, 11 * d)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 2 * d; pt.color = Color.WHITE
        cv.drawRect(x0, y0, x0 + S, y0 + S, pt); pt.style = Paint.Style.FILL
        text(cv, "HARİTA  (kapatmak için dokun)  - kırmızı: 70 m içindeki hayvanlar", W / 2, y0 - 6 * d, 12, Color.WHITE)
    }

    private fun drawHud(cv: Canvas) {
        if (scoped) {
            val r = min(W, H) * 0.42f
            path.reset(); path.fillType = Path.FillType.EVEN_ODD
            path.addRect(0f, 0f, W, H, Path.Direction.CW); path.addCircle(W / 2, H / 2, r, Path.Direction.CW)
            fill(Color.BLACK); cv.drawPath(path, pt); path.fillType = Path.FillType.WINDING
            line(cv, W / 2 - r, H / 2, W / 2 + r, H / 2, 1.5f * d, Color.argb(200, 0, 0, 0))
            line(cv, W / 2, H / 2 - r, W / 2, H / 2 + r, 1.5f * d, Color.argb(200, 0, 0, 0))
            fill(Color.RED); cv.drawCircle(W / 2, H / 2, 2 * d, pt)
        } else {
            val sway = sin(tm * 2f) * 3 * d + (if (hypot(joyX, joyY) > 0.12f) sin(tm * 9f) * 6 * d else 0f)
            val kick = recoil * 22 * d
            val f = wp.len / 0.5f
            val x0 = W * 0.84f + sway; val y0 = H * 1.03f + kick
            val x1 = x0 + (W * 0.58f - x0) * f; val y1 = y0 + (H * 0.58f - y0) * f
            drawGun(cv, wp, x0, y0, x1, y1)
            line(cv, W / 2 - 10 * d, H / 2, W / 2 - 3 * d, H / 2, 1.5f * d, Color.WHITE)
            line(cv, W / 2 + 3 * d, H / 2, W / 2 + 10 * d, H / 2, 1.5f * d, Color.WHITE)
            line(cv, W / 2, H / 2 - 10 * d, W / 2, H / 2 - 3 * d, 1.5f * d, Color.WHITE)
            line(cv, W / 2, H / 2 + 3 * d, W / 2, H / 2 + 10 * d, 1.5f * d, Color.WHITE)
        }
        if (joyId != -1) {
            fill(Color.argb(70, 255, 255, 255)); cv.drawCircle(joyX0, joyY0, 60 * d, pt)
            fill(Color.argb(150, 255, 255, 255)); cv.drawCircle(joyX0 + joyX * 60 * d, joyY0 + joyY * 60 * d, 24 * d, pt)
        }
        fill(Color.argb(140, 200, 40, 40)); cv.drawCircle(fireX, fireY, 50 * d, pt)
        text(cv, "ATEŞ", fireX, fireY + 5 * d, 14, Color.WHITE)
        fill(Color.argb(140, 40, 90, 160)); cv.drawCircle(scopeX, scopeY, 32 * d, pt)
        text(cv, "NİŞAN", scopeX, scopeY + 4 * d, 11, Color.WHITE)
        fill(Color.argb(140, 90, 90, 90)); cv.drawCircle(relX, relY, 32 * d, pt)
        text(cv, "DOLDUR", relX, relY + 4 * d, 10, Color.WHITE)
        fill(Color.argb(140, 60, 60, 60)); cv.drawCircle(exitX, exitY, 26 * d, pt)
        text(cv, "ÇIK", exitX, exitY + 4 * d, 11, Color.WHITE)
        miniMap(cv)
        val ts = timeLeft.toInt()
        text(cv, String.format("%d:%02d", ts / 60, ts % 60) + "   Puan: " + fmt(earned), W / 2, 28 * d, 16, Color.WHITE)
        text(cv, wp.name, W / 2, H - 38 * d, 12, Color.WHITE)
        text(cv, if (reloading) "Dolduruluyor..." else "Mermi: $ammo/${wp.cap}", W / 2, H - 18 * d, 15, if (ammo == 0) 0xFFFF8A80.toInt() else Color.WHITE)
        for ((i, p) in pops.withIndex()) {
            text(cv, p.text, W / 2, H * 0.3f + i * 24 * d, 18, Color.argb((min(1f, p.t) * 255).toInt(), 255, 224, 130))
        }
        if (showMap) fullMap(cv)
    }
}
