# Panduan CI/CD ZKM Next (untuk pemula)

## Konsep singkat
- **GitHub Actions** = "robot" milik GitHub. Kamu tulis resep di file `.yml`, robot menjalankannya di komputer GitHub (gratis untuk repo publik).
- **Workflow** = satu resep. Ada dua: `nightly.yml` (build tiap malam) dan `beta.yml` (build saat kamu membuat tag beta).
- **Secret** = brankas rahasia repo untuk token/password. Tidak pernah tampil di log.
- **Release** = halaman unduhan di GitHub. App ZKM Next membaca halaman ini untuk dialog update.

## 1. Taruh file di repo
```
repo-kamu/
├── .github/
│   └── workflows/
│       ├── nightly.yml
│       └── beta.yml
└── app/ ...
```
Folder harus persis `.github/workflows/` (pakai titik di depan `github`). Commit dan push ke branch `main`.

## 2. Buat kunci tanda tangan (sekali saja, WAJIB disimpan)
Tanpa ini setiap APK memakai kunci acak, jadi tidak bisa meng-update APK sebelumnya (harus uninstall dulu).
```
keytool -genkeypair -v -keystore zkmnext.keystore -alias zkmnext \
  -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 zkmnext.keystore > keystore.b64     # isi file ini dipakai di secret
```
Simpan `zkmnext.keystore` + passwordnya di tempat aman (jangan di-commit). Kalau hilang, kamu tidak bisa merilis update untuk pengguna lama. Kunci yang sama harus dipakai untuk rilis stabil nanti.

## 3. Bot Telegram
1. Chat **@BotFather** → `/newbot` → ikuti langkahnya → salin **token** (bentuk `123456:ABC...`).
2. Tambahkan bot ke channel `@zuankernelmanagernext` sebagai **Administrator** (izin: kirim pesan).
3. Chat ID: untuk channel publik cukup tulis `@zuankernelmanagernext`. Untuk grup/channel privat: kirim satu pesan, buka `https://api.telegram.org/bot<TOKEN>/getUpdates`, cari `"chat":{"id":-100...}`.

## 4. Isi GitHub Secrets
Repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**. Buat 6 secret:

| Nama | Isi |
|---|---|
| `TELEGRAM_BOT_TOKEN` | token dari BotFather |
| `TELEGRAM_CHAT_ID` | `@zuankernelmanagernext` atau angka `-100...` |
| `KEYSTORE_BASE64` | isi file `keystore.b64` |
| `KEYSTORE_PASSWORD` | password keystore |
| `KEY_ALIAS` | `zkmnext` |
| `KEY_PASSWORD` | password key (sama dengan keystore jika tidak dibedakan) |

Juga: **Settings → Actions → General → Workflow permissions → Read and write permissions** (supaya robot boleh membuat Release).

## 5. Cara pemicu bekerja
- **Nightly**: otomatis tiap 00:00 WIB (cron `0 17 * * *` = 17:00 UTC). Dilewati jika tidak ada commit baru 24 jam terakhir. Bisa dijalankan manual: tab **Actions → Nightly → Run workflow**. Hasil: release `nightly-YYYYMMDD` (pre-release), 7 terbaru disimpan.
- **Beta**: push tag, contoh:
  ```
  git tag v1.1.0-beta.1
  git push origin v1.1.0-beta.1
  ```
  Atau **Actions → Beta → Run workflow** lalu isi versi `1.1.0-beta.1`. Hasil: pre-release `v1.1.0-beta.1`, itulah yang dideteksi toggle "Periksa pembaruan beta" di app.
- **Rilis stabil** (manual, tanpa workflow): buat Release biasa bertag `v1.1.0`, lampirkan APK bertanda tangan yang sama. Naikkan `versionCode` fallback di `app/build.gradle.kts` di atas semua nightly/beta (mis. 10100 → 11000) supaya stabil tidak dianggap "downgrade".

## 6. @GitHubBot vs bot sendiri
Setahu saya GitHub tidak punya bot Telegram resmi. Bot "GitHubBot" yang ada dibuat komunitas (mis. githubbotrevised) dan hanya mengabari issue, PR, dan push.
- Kelebihan: tanpa coding/YAML, cukup klik.
- Kekurangan: **tidak bisa mengirim file APK**, tidak tahu status build, bergantung pada pihak ketiga yang bisa berhenti.
Bot sendiri (yang dipakai di workflow) mengirim APK + checksum + daftar commit langsung ke channel. Keduanya boleh dipakai bersamaan.

## 7. Jika gagal
- Tab **Actions** → klik run yang merah → buka langkah yang merah.
- `Resource not accessible by integration` → izin "Read and write" belum diaktifkan (langkah 4).
- Telegram tidak terkirim → bot belum admin di channel / chat ID salah. APK > 50 MB dikirim sebagai link saja (batas Telegram).
- Beta ingin build release: pilih `release` di Run workflow (perlu secret keystore). Jika gagal karena proguard, pakai `debug`.
