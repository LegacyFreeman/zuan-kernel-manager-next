# 📖 Dokumentasi ZKM Next

**Zuan Kernel Manager Next** — panduan lengkap semua menu, dalam Bahasa Indonesia.
Dokumen ini sama isinya dengan sistem **Bantuan & Dokumentasi** di dalam aplikasi
(menu About → Help & Documentation, atau tombol **?** di pojok kanan atas setiap layar).

> [!WARNING]
> ZKM Next melakukan operasi ber-privilese (root) dan bisa mengubah parameter
> kernel/sistem. Nilai yang salah bisa menyebabkan instabilitas, boot issue,
> overheat, atau kehilangan data. **Gunakan fitur lanjutan dengan risiko sendiri.**

---

## 🚀 Memulai

1. **Onboarding** — saat pertama dibuka, aplikasi menjelaskan fitur utama dan
   meminta persetujuan. Menolak = aplikasi keluar (karena fungsi inti butuh root).
2. **Splash 3D** — animasi pembuka. **Ketuk dua kali di mana saja untuk melewati.**
3. **Root check** — jika root tidak ditemukan, dialog peringatan muncul.
4. **Izin tambahan** (Usage Access, overlay, notifikasi) hanya diminta saat
   kamu membuka menu yang membutuhkannya.

---

## 🗂️ Panduan Semua Menu

### Dasbor

Ringkasan perangkatmu: frekuensi CPU/GPU realtime, suhu, RAM, storage, baterai, dan lainnya, tampil sebagai widget kaca di atas wallpaper.

**Cara pakai:**
- Tap tombol FAB untuk menambah, menghapus, mengurutkan, atau mengganti gaya widget.
- Di mode edit, tahan widget lalu seret ke posisi yang kamu mau.
- Tap widget untuk langsung lompat ke layar alat terkait.
- Atur interval refresh (polling) di Settings > Dashboard untuk menyeimbangkan kelancaran dan baterai.
- Kartu profil, bingkai avatar, dan sapaan bisa dikustomisasi dari widget profil.

### Home

Pusat semua alat ZKM Next, dikelompokkan per kategori. Kartu yang tidak didukung otomatis disembunyikan di perangkat tanpa hardware yang dibutuhkan.

**Cara pakai:**
- Gunakan chip kategori di atas (All, System, Battery, Display, ...) untuk memfilter alat.
- Tap kartu mana pun untuk membuka alatnya.
- Alat yang butuh izin tambahan akan memintanya saat pertama dipakai.

### SoC & Performa

Jantung tuning ZKM Next. Empat tab mencakup prosesor, scheduler, memori, dan stack jaringan chipset-mu.

**Cara pakai:**
- CPU & GPU: ubah governor, frekuensi min/maks, dan status per-core. Baca nilai saat ini sebelum mengubah apa pun.
- Scheduler: atur parameter scheduler kernel yang memengaruhi responsivitas dan konsumsi daya.
- Memory: tuning VM, swappiness, ukuran/algoritma ZRAM, dan cache pressure.
- Network: TCP congestion control dan tweak sysctl terkait.
- Nilai diterapkan langsung via root; node yang tidak didukung ditandai tidak tersedia, bukan gagal diam-diam.

> ⚠️ **Hati-hati:** Frekuensi, governor, atau nilai VM yang salah bisa menyebabkan freeze, overheat, atau bootloop. Ubah satu per satu dan uji stabilitasnya.

### Pengaturan

Semua preferensi aplikasi: tema, warna, font, gaya navigation bar, perilaku kaca/blur, opsi dasbor, language pack, backup, dan lainnya.

**Cara pakai:**
- Pengaturan tampilan (warna tema, dynamic color, dark mode) langsung diterapkan.
- Gaya navigasi bisa diganti antara Liquid Capsule, Liquid Capsule 2, Classic Material, dan Modern Tabs.
- Intensitas kaca, radius blur, dan gaya kartu bisa diatur tanpa menghilangkan tampilan wallpaper.
- Ekspor/impor pengaturan atau language pack dari bagian backup.

### Tentang

Informasi aplikasi: versi, developer, tautan, changelog, lisensi open-source, dan pusat Bantuan & Dokumentasi.

**Cara pakai:**
- Buka Help & Documentation untuk panduan lengkap semua menu.
- Changelog berisi perubahan di tiap rilis.
- Open Source menampilkan library pihak ketiga dan lisensinya.

### Studio Wallpaper & Gaya

Studio visual ZKM Next: pilih gaya live wallpaper, gambar/video galeri sendiri, tema Expressive animasi, overlay cuaca, kekuatan blur, saturasi, dan kontras.

**Cara pakai:**
- Tipe latar: warna tema sistem, warna solid preset, tema animasi Expressive, atau gambar/video milikmu dari galeri.
- Efek cuaca (hujan, salju, kabut, sinar matahari, awan, es) digambar di atas latar apa pun dengan intensitas yang bisa diatur.
- Slider blur, saturasi, dan kontras hanya memengaruhi lapisan wallpaper, tidak pernah UI.
- Wallpaper video otomatis berhenti untuk hemat daya saat aplikasi tidak terlihat.

> ⚠️ **Hati-hati:** Wallpaper video dan efek cuaca berat memakai daya GPU ekstra. Di perangkat low-end, kombinasikan secukupnya.

### Umum

Kontrol umum perangkat dan aksi cepat dalam satu tempat.

**Cara pakai:**
- Periksa tiap toggle sebelum mengaktifkannya; perubahan langsung diterapkan.
- Opsi yang butuh root diberi tanda dan hanya bekerja di perangkat rooted.

### Terminal

Emulator terminal penuh di dalam aplikasi, dengan dukungan shell root, tampilan yang bisa dikustomisasi, dan latar video.

**Cara pakai:**
- Jalankan perintah shell apa pun; awali dengan su atau aktifkan sesi root untuk perintah privilese.
- Tap ikon gear untuk ukuran font, warna, dan preferensi perilaku.
- Kunci sesi agar tidak salah ketik, maksimalkan untuk kerja full-screen.
- Ringkasan sistem ala fastfetch tersedia dari tampilan awal.

> ⚠️ **Hati-hati:** Perintah shell root bisa mengubah atau merusak sistem. Jalankan hanya perintah yang kamu pahami.

### SetEdit

Editor Settings Database: lihat dan ubah nilai di tabel system, secure, dan global - data yang sama yang diubah perintah 'settings'.

**Cara pakai:**
- Jelajahi tabel System, Secure, dan Global, lalu gunakan pencarian untuk menemukan key dengan cepat.
- Tap entri untuk mengubah nilainya; perubahan langsung berlaku.
- Tweak umum (skala animasi, batas refresh rate) juga tersedia dalam bentuk yang lebih ramah di menu Display.

> ⚠️ **Hati-hati:** Mengubah key yang tidak dikenal bisa merusak aplikasi atau fitur sistem. Catat nilai aslinya sebelum diubah.

### FPS Manager

Toolkit frame-rate realtime dengan empat tab: Home (FPS live), Stats (sesi rekaman), Games (profil launcher per game), dan Overlay (meteran FPS melayang).

**Cara pakai:**
- Home menampilkan FPS aplikasi foreground saat ini dan bisa mulai/berhenti merekam.
- Stats menyimpan rekaman di database lokal dengan rata-rata, 1% low, dan grafik; tersedia ekspor.
- Games memungkinkan mendaftarkan game dan meluncurkannya dengan pelacakan FPS.
- Overlay menggambar meteran FPS melayang di atas aplikasi lain; butuh izin 'Tampil di atas aplikasi lain'.

> ⚠️ **Hati-hati:** Izin overlay diberikan di pengaturan sistem. Merekam memakai sedikit daya ekstra.

### Process Manager

Task manager realtime: lihat proses berjalan, pemakaian CPU/memori per proses, dan kelola langsung.

**Cara pakai:**
- Urutkan berdasarkan CPU atau memori untuk menemukan proses berat.
- Tap proses untuk detail dan aksi (mis. kill).
- Jendela proses melayang tersedia untuk monitoring sambil memakai aplikasi lain.

> ⚠️ **Hati-hati:** Mematikan proses sistem bisa membuat aplikasi crash atau memaksa reboot. Hentikan hanya proses yang kamu kenali.

### Kernel Flasher

Flash kernel, boot image, dan backup partisi langsung dari aplikasi, ditenagai mesin Kernel Flasher bawaan.

**Cara pakai:**
- Pilih file image, periksa partisi tujuan, lalu flash.
- Selalu simpan backup boot image saat ini sebelum flash kernel baru.
- Reboot diperlukan agar kernel hasil flash aktif.

> ⚠️ **Hati-hati:** Flash image yang salah bisa soft-brick perangkat. Periksa ulang file dan partisi, dan simpan backup.

### Activity Launcher

Jelajahi semua activity internal aplikasi terpasang dan luncurkan langsung - termasuk layar tersembunyi tanpa ikon launcher.

**Cara pakai:**
- Cari aplikasi, bentangkan untuk melihat activity-nya, lalu tap untuk meluncurkan.
- Sebagian activity menolak diluncurkan dari luar atau crash saat dibuka di luar konteks; itu normal.
- Jendela launcher melayang tersedia untuk akses cepat di mana saja.

### KernelSU WebUI

Menjalankan WebUI modul KernelSU di dalam ZKM Next: WebView terisolasi dengan jembatan file system ber-root untuk modul yang menyediakannya.

**Cara pakai:**
- Modul KernelSU yang punya WebUI muncul otomatis.
- Jembatan file memungkinkan UI modul membaca/menulis file konfigurasinya dengan aman lewat aplikasi.
- Jika tidak ada modul yang muncul, pasang dulu modul KernelSU yang mendukung WebUI.

### GPU Adreno

Tuning mendalam GPU Qualcomm Adreno dalam lima tab: Frekuensi, Power level, Thermal, Bus/DDR, dan parameter driver Advanced.

**Cara pakai:**
- Freq: clock GPU min/maks dan tabel frekuensi yang tersedia.
- Power: power level, bus split, dan opsi force-clock.
- Thermal: batas termal GPU dan perilaku throttling.
- Bus: vote bandwidth interconnect/DDR terkait GPU.
- Advanced: node driver kgsl tingkat rendah untuk yang sudah paham.

> ⚠️ **Hati-hati:** Overclock GPU dan perubahan power level bisa memanaskan chip atau merusak tampilan. Tetap dalam tabel frekuensi yang disediakan kernel-mu.

### GPU MediaTek

Pusat tuning MediaTek/Dimensity: frekuensi GPU, kebijakan PPM, CPU boost, DRAM, dan kontrol thermal khusus platform MTK.

**Cara pakai:**
- Freq: operating point GPU dan kunci min/maks.
- PPM: aktif/nonaktifkan kebijakan Power Policy Management MTK.
- CPU: boost MTKS dan bantuan scheduler.
- DRAM: frekuensi DRAM dan opsi terkait latensi.
- Thermal: kebijakan thermal platform.

> ⚠️ **Hati-hati:** Node MTK berbeda per generasi chipset; opsi yang tidak ada berarti memang tidak diekspos kernel-mu.

### Debloat & Freeze

Nonaktifkan atau bekukan aplikasi bawaan (bloatware) tanpa uninstall, jadi update dan aplikasi dependen tetap berfungsi.

**Cara pakai:**
- Freeze menyembunyikan aplikasi dan menghentikan semua aktivitas latar belakangnya; unfreeze mengembalikannya seketika.
- Gunakan pencarian dan filter untuk meninjau bloatware berdasarkan nama package.
- Aplikasi sistem yang dibekukan bisa muncul lagi setelah OTA di sebagian perangkat - bekukan ulang saja.

> ⚠️ **Hati-hati:** Membekukan komponen sistem penting bisa merusak telepon, kamera, atau boot. Kalau ragu, cari dulu nama package-nya.

### Thermal Devices

Monitor semua zona termal perangkat (cluster CPU, GPU, baterai, modem, sensor skin) dan atur kebijakan thermal yang didukung.

**Cara pakai:**
- Pembacaan live menampilkan tiap sensor dengan suhu saat ini.
- Opsi kebijakan/tweak thermal bergantung pada chipset dan kernel-mu.
- Reaksi suhu (efek visual) bisa diatur dari widget thermal di dasbor.

> ⚠️ **Hati-hati:** Mematikan thermal throttling berarti menghapus proteksi hardware. Suhu tinggi berkelanjutan merusak baterai dan komponen.

### Display

Kontrol layar: resolusi dan DPI, paksa refresh rate, saturasi warna, perilaku kecerahan, skala font, dan kecepatan animasi.

**Cara pakai:**
- Tab Dashboard merangkum resolusi, DPI, dan refresh rate saat ini.
- Perubahan resolusi/DPI memakai perintah 'wm' dan kembali aman setelah reboot jika perangkat tidak bisa dipakai.
- Paksa refresh rate menulis nilai min/peak; sebagian ROM OEM mengabaikannya.
- Slider skala animasi mempercepat atau memperlambat animasi sistem secara global.

> ⚠️ **Hati-hati:** Nilai DPI ekstrem bisa membuat UI tidak bisa dipakai. Jika itu terjadi, reboot atau reset dari layar ini dengan tombol reset.

### Battery Controller

Kontrol pengisian untuk kernel yang mendukung: batas charge, toggle fast-charge, dan pengaturan arus idle/input yang melindungi kesehatan baterai.

**Cara pakai:**
- Atur level charge maksimum (mis. 80%) untuk mengurangi keausan baterai saat pemakaian lama.
- Matikan fast charging saat suhu lebih penting daripada kecepatan.
- Ketersediaan sepenuhnya bergantung pada kernel-mu mengekspos node kontrol.

> ⚠️ **Hati-hati:** Nilai yang tidak didukung ditolak dengan aman, tapi tweak pengisian tetap memengaruhi suhu - pantau temperatur saat menguji.

### Doze Mode

Periksa dan atur perilaku deep-sleep Android: parameter device idle (doze), deteksi gerakan, dan perilaku app standby.

**Cara pakai:**
- Baca dulu status doze dan nilai parameter saat ini.
- Timeout lebih pendek lebih hemat daya tapi bisa menunda notifikasi.
- Aplikasi whitelist mengabaikan doze - kelola daftarnya dari pengaturan baterai sistem.

### Kompiler Dex2oat

Kompilasi ulang aplikasi ahead-of-time dengan filter kompiler berbeda (speed, everything, verify, ...) untuk menukar ukuran instal dengan performa runtime.

**Cara pakai:**
- 'speed-profile' sama seperti yang dilakukan Play Store setelah instal; 'everything' mengompilasi semua kode untuk kecepatan runtime maksimum.
- Mengompilasi banyak package butuh waktu dan memanaskan perangkat - jalankan sambil charging.
- Aplikasi sistem juga bisa dikompilasi ulang di perangkat rooted.

> ⚠️ **Hati-hati:** Kompilasi 'everything' penuh membuat pemakaian storage membengkak. Membatalkan kompilasi itu aman, tapi aplikasi jadi teroptimasi sebagian.

### App Manager

Inventaris aplikasi yang cepat: aplikasi user dan sistem dengan ikon, ukuran, versi, dan aksi cepat.

**Cara pakai:**
- Gunakan search bar dan filter (user/system) untuk mempersempit daftar.
- Tap aplikasi untuk detail dan aksi seperti buka, uninstall, atau app-info.
- Ikon diperkecil saat dimuat agar daftar tetap mulus walau aplikasinya ratusan.

### MIUI Thermal

Kelola perilaku mesin thermal MIUI/HyperOS di perangkat Xiaomi, termasuk penanganan konfigurasi thermal terenkripsi.

**Cara pakai:**
- Periksa status layanan thermal saat ini sebelum mengubah apa pun.
- Modifikasi bertahan setelah reboot sampai update MIUI berikutnya mengembalikan default.
- Hanya muncul di perangkat MIUI/HyperOS.

> ⚠️ **Hati-hati:** Mematikan kontrol thermal MIUI berarti menghapus proteksi thermal Xiaomi. Pantau suhu dengan ketat.

### Screen Time

Statistik pemakaian ala digital wellbeing: berapa lama tiap aplikasi berada di foreground, per hari atau per minggu.

**Cara pakai:**
- Berikan izin Usage Access saat diminta (pengaturan sistem).
- Ganti periode untuk membandingkan hari ini vs minggu ini.
- Data berasal dari layanan UsageStats Android - riwayat mulai dihitung setelah izin diberikan.

### Pemakaian Baterai

Rincian konsumsi baterai per aplikasi dengan detail wake-lock dan aktivitas latar belakang.

**Cara pakai:**
- Berikan Usage Access untuk statistik per aplikasi yang lengkap.
- Kombinasikan dengan Screen Time untuk menemukan aplikasi yang menguras baterai tanpa dipakai.

### Statistik Charge

Telemetri pengisian live: arus, voltase, watt, suhu, dan status charge, dengan riwayat sesi.

**Cara pakai:**
- Pantau watt realtime untuk membandingkan charger dan kabel.
- Riwayat menunjukkan perubahan kecepatan charging selama satu sesi.
- Nilai bergantung pada apa yang diekspos fuel-gauge bateraimu.

### Storage

Inteligensi storage: rincian pemakaian, file besar, gambaran cache dan junk, plus info kesehatan UFS di perangkat yang mendukung.

**Cara pakai:**
- Pindai kategori untuk melihat apa yang memenuhi storagemu.
- UFS health menampilkan estimasi keausan flash memory jika kernel mengeksposnya.
- Aksi bersih-bersih membuka layar sistem terkait untuk konfirmasi - tidak ada yang dihapus diam-diam.

### Changelog

Riwayat versi ZKM Next: fitur baru, perbaikan, dan optimasi di tiap rilis.

**Cara pakai:**
- Entri diambil/ditampilkan per tag rilis.
- Entri terbaru menjelaskan perubahan sejak versi sebelumnya.

### Open Source

Library pihak ketiga dan lisensi yang dipakai ZKM Next, plus kredit kontributor.

**Cara pakai:**
- Tap library untuk membaca lisensinya.
- ZKM Next sendiri berlisensi GPL-3.0.

### Bantuan & Dokumentasi

Panduan lengkap di dalam aplikasi untuk ZKM Next: semua menu dijelaskan, plus FAQ umum tentang root, keamanan, dan language pack.

**Cara pakai:**
- Gunakan pencarian untuk langsung lompat ke topik.
- Tiap layar juga punya tombol '?' di bar atas yang membuka topik yang sama.
- Semua teks bantuan bisa diterjemahkan lewat language pack.

---

## ❓ FAQ Umum

### Root, Shizuku, dan izin

ZKM Next adalah aplikasi root-first. Menu tuning kernel, debloat, thermal, dan GPU butuh root asli (Magisk/KernelSU). Shizuku atau izin khusus (Usage Access, Tampil di atas aplikasi lain, notifikasi) membuka fitur tertentu dan hanya diminta saat kamu membuka menu terkait.

### Keamanan dan backup

Setiap aksi berbahaya menampilkan konfirmasi. Meski begitu: ekspor pengaturanmu dari Settings > Backup sebelum tuning berat, simpan backup boot image sebelum flash kernel, dan ubah nilai tuning satu per satu agar selalu tahu apa penyebab masalah.

### Language pack

ZKM Next mendukung language pack runtime: file JSON yang menimpa string bawaan apa pun. Ekspor pack saat ini dari Settings, terjemahkan nilainya, lalu impor kembali. Pack Bahasa Indonesia untuk sistem bantuan disertakan di rilis ini. Catatan: mengimpor pack menggantikan semua override yang ada.

### Kalau aplikasi terasa berat

Tampilan kaca memang sengaja dibuat kaya, tapi tidak ada yang sia-sia: style blur di-cache, ikon diperkecil, dan daftar memakai key. Kalau tetap ingin lebih ringan, kurangi intensitas cuaca, pakai wallpaper statis daripada video, atau pilih bingkai avatar statis - semuanya dari Wallpaper & Style Studio dan widget profil.

---

## 🌐 Language Pack (terjemahan)

Sistem bantuan ini sepenuhnya bisa diterjemahkan lewat **language pack** ZKM
(Settings → Language Pack):

1. **Export** pack bawaan (JSON) dari Settings.
2. Terjemahkan value-nya (key jangan diubah).
3. **Import** kembali — langsung diterapkan tanpa restart.

File `docs/ZKM-Help-Pack-ID.json` di repo ini adalah pack Bahasa Indonesia
siap impor yang berisi semua string bantuan. **Catatan:** mengimpor pack
menggantikan seluruh override yang sedang aktif.

Selain itu, perangkat dengan locale **Bahasa Indonesia** otomatis menampilkan
konten bantuan berbahasa Indonesia (`values-in`) tanpa perlu impor apa pun.

---

## ⚡ Catatan Performa (v10.3)

Tidak ada efek, animasi, blur, atau fitur visual yang dikurangi/dihapus.
Prinsip optimasi rilis ini: *jangan sentuh apa yang sudah sempurna.*

- Splash 3D tetap utuh ~5 detik dengan seluruh fisika/partikelnya — kini
  ditambah hint "ketuk dua kali untuk skip" agar kontrolnya terlihat jelas.
- Audit ulang jalur loading: `KernelCore.initialize` sudah async (IO), cek root
  sudah di thread terpisah, `SettingsPreference` berbasis SharedPreferences
  (murah), seluruh polling dashboard/SoC sudah `Dispatchers.IO`, tidak ada
  pemanggilan blocking di `LaunchedEffect`.
- Optimasi glass/blur dari rilis sebelumnya (cache `HazeStyle`, radius app bar
  22dp, ikon App Manager downscale, key stabil di list) tetap dipertahankan
  penuh.

---

*Dokumentasi ini digenerate dari sumber konten yang sama dengan in-app help,
jadi isinya selalu sinkron dengan aplikasi.*
