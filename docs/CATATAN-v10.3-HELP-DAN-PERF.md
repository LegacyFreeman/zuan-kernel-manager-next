# Catatan Perubahan — v10.3 (In-App Help + Perf Audit)

## 1. Sistem Bantuan In-App (baru)

**Semua menu sekarang punya help.** Tiap layar punya tombol **?** di bar atas
yang membuka dialog kaca berisi: fungsi menu, cara pakai (poin-poin), dan
peringatan keamanan (untuk menu berbahaya).

### File baru
| File | Isi |
|---|---|
| `feature/help/HelpContent.kt` | Registry 31 topik help (per-route) + 4 FAQ umum. Konten mengacu ke `R.string.help_*` jadi otomatis ikut sistem language pack. |
| `feature/help/ZkmHelpButton.kt` | `ZkmHelpButton(route)` — tombol self-contained (state dialog internal, render kosong kalau route tak punya topik → selalu aman dipasang) + `ZkmHelpDialog` (pakai `BlurredAlertDialog` agar konsisten dengan gaya glass app). |
| `feature/help/HelpCenterScreen.kt` | Pusat Bantuan & Dokumentasi: pencarian full-text, semua menu (expandable), FAQ umum, kartu `zkmGlassCard` + app bar haze progresif sesuai tema. |

### Integrasi
- Route baru `NavigationRoute.HelpCenter` (`help_center`) + entry NavHost di `MainActivity`.
- Entri "Help & Documentation" di About → bagian Application.
- Tombol `?` disisipkan ke 30 titik di 27 file layar (semua TopAppBar M3 dan
  header kaca custom: Overall, Home, SoC, Settings, About, General, Adreno, MTK,
  Kernel Flasher, Storage, Changelogs, Open Source, Activity Launcher (2 header),
  Debloat, Thermal Devices, Display, Battery Controller, Doze, Dex2oat, FPS
  Manager, Process Manager, SetEdit, MIUI Thermal, KsuWebUI, App Manager,
  Terminal, Wallpaper Studio (preview + overlay efek), Screen Time, Battery
  Usage, Charge Stats (ketiganya via param `helpRoute` baru di
  `UsageScreenScaffold`).
- `MainSwipeContainer` (Modern Tabs) sengaja tidak diberi tombol — itu hanya
  pager pembungkus; tiap layar di dalamnya sudah punya tombol sendiri.

### Lokalisasi
- `values/strings.xml`: +203 string `help_*` (English, default).
- `values-in/strings.xml` (baru): Bahasa Indonesia untuk seluruh konten
  bantuan — aktif otomatis di perangkat ber-locale Indonesia, tanpa impor.
- `docs/ZKM-Help-Pack-ID.json`: language pack Indonesia siap impor (format
  `ZKM_LANGUAGE_PACK` v1). **Catatan:** impor pack menggantikan seluruh
  override yang sedang aktif (perilaku `LanguagePackManager.import`).

## 2. Performa / loading

Prinsip: **tidak ada efek, animasi, blur, atau fitur yang dikurangi.** Hasil
audit terhadap keluhan "loading delay":

- Arsitektur threading sudah benar: init kernel async di `Dispatchers.IO`,
  cek root di thread terpisah, polling dashboard/SoC memakai IO dispatcher,
  tidak ada pemanggilan blocking (`suBlocking`/`runBlocking`/baca file)
  langsung di `LaunchedEffect` atau body composable.
- Optimasi glass rilis sebelumnya (cache `HazeStyle` global, style app bar
  ter-memoize, radius 22dp, ikon App Manager downscale 144px, key stabil di
  list, filter ter-memoize) diverifikasi masih utuh dan konsisten.
- R8 + resource shrinking release sudah aktif; pager sudah dibatasi
  (`beyondViewportPageCount`).

**Satu-satunya perubahan:** hint *"Double-tap anywhere to skip"* di splash 3D
(muncul fade-in setelah 1.6 detik, ikut fade saat exit). Splash sudah punya
gestur double-tap-skip sejak lama tapi tidak terlihat — sekarang jelas.
Timeline, fisika, partikel, dan durasi splash **tidak diubah sama sekali**.
String: `help_splash_skip` (EN + ID).

## 3. Verifikasi

- Semua key `R.string.help_*` yang direferensikan Kotlin ada di `strings.xml`;
  `values-in` subset penuh dari default; XML valid.
- 31/31 topik help punya route yang cocok dengan `NavigationRoute`; semua
  route NavHost (kecuali container swipe, by design) punya tombol help.
- Balance kurung per file yang diedit dibandingkan dengan baseline asli
  (tidak ada delta).
- Build APK tetap perlu mesin dengan Android SDK: `./gradlew assembleRelease`.
