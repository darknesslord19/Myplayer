# Myplayer (Oynat)

Android telefon ve Android TV için Media3/ExoPlayer motorlu video oynatıcı.

- Video bağlantısı ekleme (mp4, mkv, m3u8, mpd) + isteğe bağlı altyazı
- Cihazdaki videoları listeleme, doğrudan dosya bağlantılarını indirme
- Kaldığın yerden devam, Türkçe ses/altyazı önceliği
- **Keşfet**: eklenti (provider) mimarisi. Internet Archive yerleşik; kendi JSON kataloğunu ekleyebilirsin (`ornek/katalog.json`)

## Yeni kaynak yazmak
`app/src/main/java/tr/oynat/Providers.kt` içindeki `Provider` arayüzünü uygula ve `Providers.all` listesine ekle.

## APK
Her push'ta GitHub Actions APK'yı derler: **Actions → son çalıştırma → Artifacts** veya **Releases** bölümü.
