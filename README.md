# Av Oyunu (Android, Kotlin)

Birinci şahıs mobil av oyunu. Menüler (Bölüm Seçme, Silah Seçimi) tasarımına göre yapıldı.

## İçerik
- **6 bölüm:** Orman, Karlı, Yağmurlu, Dağlık, Çöl, Şehir (her biri farklı ağaç/kaya/bina, hava ve ufuk çizgisi)
- **10 silah:** Çifte Av Tüfeği, Süperpoze, Pompalı, Yarı Otomatik, Tabanca, Yay, Sürgülü Tüfek, Otomatik Tüfek, Keskin Nişancı, Büyük Kalibre
- **21 hayvan türü:** bölüme göre değişir (geyik, yaban domuzu, ayı, kurt, deve, kartal, ördek, kar tavşanı, ...)
- **Harita:** 400x400 m dünya, köşede mini harita, dokununca tam harita
- **Hareket:** sol ekranda joystick, sağ ekranda parmakla etrafa bakma
- Puan sistemi, kilit açma, silah satın alma ve ilerleme kaydı (telefonda saklanır)
- Kodla üretilen sesler (silah, doldurma, ayak sesi, rüzgâr)

## Kontroller
| Kontrol | İşlev |
|---|---|
| Sol ekranı sürükle | Yürü |
| Sağ ekranı sürükle | Etrafa bak |
| ATEŞ | Ateş et (otomatik silahta basılı tut) |
| NİŞAN | Dürbün / yakınlaştırma |
| DOLDUR | Mermi doldur |
| Mini harita | Tam haritayı aç |

## Çalıştırma
1. Android Studio'da **Open** ile bu klasörü aç (JDK 17 gerekir, Android Studio ile gelir).
2. Gradle senkronizasyonunu bekle, telefonu bağla ve **Run** de.
3. APK için: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Komut satırından derlemek istersen önce bir kez `gradle wrapper` çalıştırman gerekir
(`gradle-wrapper.jar` dosyası pakette yok).

## Ayarlar
- `Data.kt` içinde `START_POINTS`: başlangıç puanı (varsayılan 12.500, tasarımındaki gibi). Sıfırdan başlatmak için 0 yap.
- Silah, hayvan ve bölüm değerleri (hasar, menzil, fiyat, hayvan hızı, ...) yine `Data.kt` içinde.

## Grafikleri ve sesleri geliştirme
Oyun kodla çizilir (2.5D, perspektifli). Gerçekçiliği artırmak için kendi dosyalarını ekleyebilirsin:

**Hayvan görselleri (PNG):** `app/src/main/res/drawable-nodpi/` klasörüne `animal_<numara>.png` koy.
Görsel sağa bakan, arka planı şeffaf olmalı. Numaralar `Data.kt` içindeki `SPECIES` sırasıdır:
0 Tavşan, 1 Geyik, 2 Yaban Domuzu, 3 Tilki, 4 Kurt, 5 Ayı, 6 Sülün, 7 Karaca, 8 Kar Tavşanı, 9 Ren Geyiği,
10 Kutup Tilkisi, 11 Dağ Keçisi, 12 Ördek, 13 Porsuk, 14 Kartal, 15 Ceylan, 16 Deve, 17 Çakal, 18 Rakun, 19 Karga, 20 Muflon.
Dosya varsa çizilen şeklin yerine o kullanılır.

**Sesler:** `app/src/main/res/raw/` klasörüne şu isimlerle .ogg/.wav/.mp3 koy:
`shot0` ... `shot9` (silah sırasıyla), `reload`, `step`, `hit`, `kill`, `click`, `empty`, `buy`.
Dosya varsa üretilen ses yerine o çalınır. (Dosya adları küçük harf olmalı.)

## GitHub'a yükleme
```
git init
git add .
git commit -m "Av Oyunu ilk sürüm"
git branch -M main
git remote add origin https://github.com/KULLANICI_ADIN/av-oyunu.git
git push -u origin main
```

## Not
Bu proje derleme ortamı olmadan yazıldı ve henüz Android Studio'da derlenmedi.
Küçük bir derleme hatası çıkarsa hata mesajını göndermen yeterli.
