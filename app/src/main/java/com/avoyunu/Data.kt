package com.avoyunu

// Başlangıç puanı (tasarımındaki 12.500). Sıfırdan başlatmak istersen 0 yap.
const val START_POINTS = 12500
const val MAP = 400f   // harita boyutu (metre)
const val CAMH = 1.7f  // göz yüksekliği (metre)

class Weapon(
    val name: String, val cost: Int, val dmg: Int, val range: Float, val delay: Long,
    val cap: Int, val reload: Long, val zoom: Float, val tol: Float, val noise: Float,
    val auto: Boolean, val kind: Int, val len: Float
) {
    val dmgStars get() = ((dmg + 29) / 30).coerceIn(1, 5)
    val rangeStars get() = ((range + 29f) / 30f).toInt().coerceIn(1, 5)
    val rateStars get() = (3000L / delay).toInt().coerceIn(1, 5)
}

// kind: 0 çifte/pompalı, 1 tüfek, 2 tabanca, 3 yay
// Sıra, Sound.kt içindeki shot0..shot9 ile aynı olmalı.
val WEAPONS = listOf(
    Weapon("ÇİFTE AV TÜFEĞİ", 0, 55, 40f, 700, 2, 1800, 1.2f, 0.06f, 90f, false, 0, 0.46f),
    Weapon("SÜPERPOZE TÜFEK", 5000, 70, 45f, 600, 2, 1600, 1.2f, 0.05f, 90f, false, 0, 0.5f),
    Weapon("POMPALI TÜFEK", 12000, 65, 35f, 800, 6, 2600, 1.1f, 0.07f, 100f, false, 0, 0.5f),
    Weapon("YARI OTOMATİK", 20000, 40, 85f, 250, 10, 2000, 2.0f, 0.025f, 110f, false, 1, 0.52f),
    Weapon("TABANCA", 1500, 25, 25f, 350, 8, 1500, 1.0f, 0.035f, 60f, false, 2, 0.22f),
    Weapon("YAY", 3000, 50, 55f, 1300, 1, 900, 1.6f, 0.02f, 15f, false, 3, 0.4f),
    Weapon("SÜRGÜLÜ TÜFEK", 8000, 100, 120f, 1100, 5, 2500, 3.0f, 0.015f, 130f, false, 1, 0.55f),
    Weapon("OTOMATİK TÜFEK", 25000, 35, 70f, 140, 20, 2200, 1.8f, 0.035f, 120f, true, 1, 0.52f),
    Weapon("KESKİN NİŞANCI", 30000, 150, 220f, 1500, 5, 2800, 5.0f, 0.008f, 140f, false, 1, 0.6f),
    Weapon("BÜYÜK KALİBRE", 40000, 220, 70f, 900, 2, 2400, 1.4f, 0.04f, 150f, false, 0, 0.5f)
)

// kind: 0 dört ayaklı, 1 uçan kuş, 2 küçük (tavşan), 3 uzun boyunlu (deve)
// horns: 0 yok, 1 geyik boynuzu, 2 kıvrık boynuz, 3 azı dişi
class Species(
    val name: String, val hp: Int, val speed: Float, val h: Float, val len: Float,
    val color: Int, val pts: Int, val kind: Int, val horns: Int, val skit: Float
)

val SPECIES = listOf(
    Species("Tavşan", 30, 7f, 0.35f, 0.45f, 0xFF8B7355.toInt(), 100, 2, 0, 1.3f),            // 0
    Species("Geyik", 90, 9f, 1.5f, 1.7f, 0xFF8B5A2B.toInt(), 500, 0, 1, 1.0f),               // 1
    Species("Yaban Domuzu", 120, 7f, 0.9f, 1.4f, 0xFF4A3B2A.toInt(), 400, 0, 3, 0.7f),       // 2
    Species("Tilki", 35, 8f, 0.5f, 0.9f, 0xFFC8662B.toInt(), 300, 0, 0, 1.2f),               // 3
    Species("Kurt", 70, 9f, 0.85f, 1.3f, 0xFF6E6E6E.toInt(), 600, 0, 0, 0.9f),               // 4
    Species("Ayı", 250, 6f, 1.5f, 1.9f, 0xFF3B2A1E.toInt(), 1500, 0, 0, 0.6f),               // 5
    Species("Sülün", 15, 12f, 0.6f, 0.6f, 0xFFA0522D.toInt(), 350, 1, 0, 1.0f),              // 6
    Species("Karaca", 60, 10f, 1.0f, 1.2f, 0xFFB5834A.toInt(), 350, 0, 1, 1.1f),             // 7
    Species("Kar Tavşanı", 30, 7f, 0.35f, 0.45f, 0xFFF0F0F0.toInt(), 120, 2, 0, 1.3f),       // 8
    Species("Ren Geyiği", 110, 8f, 1.6f, 1.8f, 0xFFA89F91.toInt(), 700, 0, 1, 1.0f),         // 9
    Species("Kutup Tilkisi", 35, 8f, 0.5f, 0.9f, 0xFFEDEDED.toInt(), 350, 0, 0, 1.2f),       // 10
    Species("Dağ Keçisi", 80, 9f, 1.0f, 1.3f, 0xFFE6E0D0.toInt(), 600, 0, 2, 1.0f),          // 11
    Species("Ördek", 15, 11f, 0.4f, 0.5f, 0xFF3E7F5A.toInt(), 250, 1, 0, 1.0f),              // 12
    Species("Porsuk", 50, 5f, 0.5f, 0.9f, 0xFF55504A.toInt(), 300, 0, 0, 0.8f),              // 13
    Species("Kartal", 20, 14f, 0.8f, 0.8f, 0xFF5C4A32.toInt(), 800, 1, 0, 1.0f),             // 14
    Species("Ceylan", 50, 12f, 1.0f, 1.1f, 0xFFD1A36A.toInt(), 450, 0, 2, 1.2f),             // 15
    Species("Deve", 200, 6f, 2.1f, 2.2f, 0xFFC9A15F.toInt(), 900, 3, 0, 0.6f),               // 16
    Species("Çakal", 45, 9f, 0.6f, 1.0f, 0xFFB08D57.toInt(), 350, 0, 0, 1.0f),               // 17
    Species("Rakun", 40, 5f, 0.5f, 0.8f, 0xFF6D6D6D.toInt(), 250, 0, 0, 0.8f),               // 18
    Species("Karga", 12, 13f, 0.35f, 0.4f, 0xFF1A1A1A.toInt(), 200, 1, 0, 1.0f),             // 19
    Species("Muflon", 90, 9f, 1.0f, 1.3f, 0xFF8C5A3C.toInt(), 650, 0, 2, 1.0f)               // 20
)

// mix: ağırlıklar [çam, geniş yapraklı, kuru ağaç, kaktüs, bina, kaya, çalı]
// weather: 0 yok, 1 yağmur, 2 kar, 3 toz | skyline: 0 tepeler, 1 dağlar, 2 mesalar, 3 şehir
class Level(
    val name: String, val cost: Int, val sky1: Int, val sky2: Int, val ground: Int, val fog: Int,
    val foliage: Int, val trunk: Int, val sil: Int, val mix: IntArray, val count: Int,
    val weather: Int, val skyline: Int, val fogD: Float, val snow: Boolean, val animals: IntArray
)

val LEVELS = listOf(
    Level("ORMAN", 0, 0xFF5B9BD5.toInt(), 0xFFCFE3F0.toInt(), 0xFF3F6B2F.toInt(), 0xFFBFD6D8.toInt(),
        0xFF2F5E2B.toInt(), 0xFF5A3B22.toInt(), 0xFF5C7F6B.toInt(),
        intArrayOf(5, 5, 0, 0, 0, 1, 3), 380, 0, 0, 140f, false, intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)),
    Level("KARLI", 2500, 0xFF8FA3B5.toInt(), 0xFFE3EAF0.toInt(), 0xFFEAF0F5.toInt(), 0xFFD8E2EA.toInt(),
        0xFF2D4B3A.toInt(), 0xFF4A3A2C.toInt(), 0xFF9DB0C3.toInt(),
        intArrayOf(8, 0, 2, 0, 0, 2, 1), 340, 2, 1, 100f, true, intArrayOf(8, 9, 10, 4, 5, 11)),
    Level("YAĞMURLU", 5000, 0xFF4A5560.toInt(), 0xFF8A949B.toInt(), 0xFF34552B.toInt(), 0xFF7D878C.toInt(),
        0xFF27492A.toInt(), 0xFF3C2A1B.toInt(), 0xFF55705F.toInt(),
        intArrayOf(4, 4, 2, 0, 0, 1, 3), 360, 1, 0, 90f, false, intArrayOf(1, 2, 12, 13, 4, 7, 18)),
    Level("DAĞLIK", 10000, 0xFF4F86C6.toInt(), 0xFFD8E6F2.toInt(), 0xFF7A7F6C.toInt(), 0xFFC5D3DF.toInt(),
        0xFF3C5B34.toInt(), 0xFF5B4630.toInt(), 0xFF8497AA.toInt(),
        intArrayOf(3, 0, 1, 0, 0, 8, 1), 320, 0, 1, 150f, false, intArrayOf(11, 20, 5, 14, 4, 0)),
    Level("ÇÖL", 15000, 0xFF6BB3E0.toInt(), 0xFFF3D9A8.toInt(), 0xFFC99A5B.toInt(), 0xFFEBCB97.toInt(),
        0xFF5E8C3A.toInt(), 0xFF7A5230.toInt(), 0xFFC79E6C.toInt(),
        intArrayOf(0, 0, 2, 5, 0, 4, 2), 300, 3, 2, 110f, false, intArrayOf(15, 16, 17, 0, 14)),
    Level("ŞEHİR", 20000, 0xFF6A7C8C.toInt(), 0xFFB9C2C8.toInt(), 0xFF5A5C5C.toInt(), 0xFFA9B1B6.toInt(),
        0xFF3F5A33.toInt(), 0xFF4B3A2A.toInt(), 0xFF6D767D.toInt(),
        intArrayOf(0, 1, 1, 0, 6, 2, 1), 260, 0, 3, 130f, false, intArrayOf(2, 18, 4, 19, 1, 13))
)
