<div align="center">

<img src="../images/jiumi-icon.png" alt="Ikon Jiumi" width="80" />

# Jiumi 揪咪

Lihat apa yang dilakukan aplikasi di latar depan dan latar belakang, lalu putuskan untuk mempertahankan, membatasi, menonaktifkan, atau mencopotnya.
<br>Tidak memerlukan akses root atau komputer. Catatan tetap tersimpan di ponsel.

**[Unduh APK](https://github.com/ybblab/Jiumi/releases/latest)** · [Panduan](https://jiumi.ybblab.com/en/download/) · [Situs resmi](https://jiumi.ybblab.com/en/)

<sub>Android 11 atau lebih baru · ARM64 · APK sekitar 4 MB · <a href="../../LICENSE">Apache 2.0</a></sub>

[简体中文](../../README.md) · [English](README.en.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [Español](README.es.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [हिन्दी](README.hi.md) · **Bahasa Indonesia** · [Português (Brasil)](README.pt-BR.md) · [Русский](README.ru.md)

</div>

## Sekilas tentang aplikasi

<p align="center">
  <a href="../images/monitor.en.png"><img src="../images/monitor.en.png" width="196" alt="Aktivitas 30 hari dengan peta panas" /></a>&nbsp;
  <a href="../images/monitor-week.en.png"><img src="../images/monitor-week.en.png" width="196" alt="Tren 7 hari dengan peta panas" /></a>&nbsp;
  <a href="../images/by-app.en.png"><img src="../images/by-app.en.png" width="196" alt="Peringkat aplikasi menurut akses dan aktivitas latar belakang" /></a>&nbsp;
  <a href="../images/app-detail.en.png"><img src="../images/app-detail.en.png" width="196" alt="Kurva aktivitas aplikasi selama 30 hari" /></a>
</p>

<p align="center">
  <a href="../images/timeline.en.png"><img src="../images/timeline.en.png" width="196" alt="Linimasa aktivitas aplikasi" /></a>&nbsp;
  <a href="../images/by-type.en.png"><img src="../images/by-type.en.png" width="196" alt="Aktivitas menurut jenis" /></a>&nbsp;
  <a href="../images/apps.en.png"><img src="../images/apps.en.png" width="196" alt="Aplikasi menurut sumber" /></a>&nbsp;
  <a href="../images/connection.en.png"><img src="../images/connection.en.png" width="196" alt="Empat cara memulai: proses debug nirkabel, proses debug jaringan, Shizuku, dan root" /></a>
</p>

<p align="center"><sub>Layar ini memakai data contoh. Aplikasi dan alamatnya fiktif. Ketuk gambar untuk memperbesar.</sub></p>

## Fitur

### Memahami aktivitas

| Fitur | Yang dapat Anda lakukan |
| --- | --- |
| Ringkasan aktivitas | Lihat total, tren, rata-rata, puncak, dan jumlah akses latar belakang untuk **sesi, hari ini, 7 hari, atau 30 hari**. Ketuk atau geser kurva untuk membaca rentang waktu. Rentang tanpa pemantauan maupun catatan ditandai **No records**. |
| Perlu perhatian | Gunakan **peta panas** untuk menemukan hari dan jam yang menonjol, lalu tinjau pengiriman data dan aktivitas sensitif di latar belakang, pembacaan berulang, serta percobaan berulang setelah ditolak. |
| Peringkat aplikasi | Temukan aplikasi paling aktif menurut semua akses atau akses latar belakang dan bandingkan data yang diakses. Pekerjaan rutin sistem dilipat terpisah. |
| Detail aplikasi | Ikuti satu aplikasi dengan kurva yang sama. Ketuk jenis data untuk menampilkan hanya jenis itu pada kurva dan linimasa. |
| Catatan aktivitas | Lihat **56 jenis akses dalam 12 kelompok**, termasuk lokasi, papan klip, dan pengenal perangkat. Lihat catatan berdasarkan waktu, aplikasi, atau jenis, serta saring aktivitas latar belakang. |
| Aktivitas jaringan | Lihat alamat koneksi dan pengukuran lalu lintas yang tersedia untuk tiap aplikasi. Jika pencatatan domain diaktifkan, koneksi yang berhasil dicocokkan dapat menampilkan nama domain. |
| Riwayat sistem | Buka detail aplikasi untuk melihat catatan dan jumlah akses izin yang disimpan Android, termasuk sebagian aktivitas sebelum pemantauan dimulai. |

### Pemantauan dan catatan

| Fitur | Yang dapat Anda lakukan |
| --- | --- |
| Daftar pengecualian pemantauan | Pilih aplikasi yang tidak dipantau di **Settings › Monitoring › Monitoring allowlist**. Jiumi tidak membuat catatan baru dan menyembunyikan catatan yang ada. Catatan tampil lagi setelah aplikasi dikeluarkan dari daftar. Aktivitas selama dikecualikan tidak dilengkapi kembali. |
| Notifikasi | Selama pemantauan, notifikasi menampilkan durasi dan tombol berhenti. Notifikasi ini dapat dimatikan di **Settings › Notifications**. Dengan **Show summary**, notifikasi menampilkan **jumlah akses sesi dan akses latar belakang**, tanpa nama aplikasi. Dengan **Attention alerts**, Anda diberi tahu tentang aktivitas baru yang perlu diperhatikan, sekali per aplikasi dan jenis peringatan dalam satu sesi. |
| Ekspor catatan | Di **Settings › Logs › Export logs**, simpan catatan dari rentang waktu yang dipilih sebagai arsip ZIP berisi berkas CSV. |

## Mengelola aplikasi

Aplikasi dikelompokkan menurut sumber pemasangan. Buka detail aplikasi untuk meninjau aktivitasnya dan memutuskan apakah perlu melakukan perubahan. Anda yang memulai tindakan pengelolaan. Setelah setiap perubahan, Jiumi membaca kembali status dari Android untuk memastikan perubahan diterapkan. Jika Android menolak, Jiumi menampilkan alasannya.

<p align="center">
  <a href="../images/app-disable.en.png"><img src="../images/app-disable.en.png" width="250" alt="Demo situs web yang memperlihatkan konfirmasi penonaktifan aplikasi" /></a>&nbsp;
  <a href="../images/app-delete.en.png"><img src="../images/app-delete.en.png" width="250" alt="Demo situs web yang memperlihatkan konfirmasi pencopotan aplikasi dan pemberitahuan pencadangan" /></a>&nbsp;
  <a href="../images/app-restore.en.png"><img src="../images/app-restore.en.png" width="250" alt="Demo situs web yang memperlihatkan opsi untuk memulihkan aplikasi yang dicopot atau membuang cadangannya" /></a>
</p>

| Tindakan | Hasil |
| --- | --- |
| Izin | Aktifkan atau nonaktifkan izin satu per satu di **Permissions** pada detail aplikasi. **Manageable permissions** menonaktifkan semua izin yang dapat dikelola sekaligus. Saat sakelar dinyalakan kembali, hanya izin yang telah dinonaktifkan oleh Jiumi yang dipulihkan. Izin yang dikunci sistem ditampilkan sebagai hanya baca. |
| Akses jaringan | Pada perangkat yang didukung, blokir atau pulihkan akses jaringan satu aplikasi dengan firewall bawaan Android tanpa menggunakan slot VPN. Sakelar dikunci dan alasannya ditampilkan jika aplikasi berbagi UID dengan aplikasi lain, aturan dibuat oleh alat lain, atau Jiumi tidak dapat memastikan bahwa hanya aplikasi tersebut yang terdampak. |
| Nonaktifkan atau aktifkan aplikasi | Android menonaktifkan aplikasi untuk pengguna saat ini. Aplikasi dan datanya tetap disimpan. Aktifkan kembali melalui **Apps › Disabled**. Aplikasi yang dinonaktifkan ditandai ikon kepingan salju. |
| Aktif kembali | Jika Jiumi menemukan aplikasi yang dinonaktifkannya sudah aktif kembali, status **Re-enabled** muncul di daftar dan detail aplikasi. Linimasa mencatat waktu terdeteksinya perubahan. Aktifkan **Settings › App management › Disable again when re-enabled** agar Jiumi menonaktifkannya lagi. |
| Copot aplikasi yang Anda pasang | Masukkan nama paket lengkap untuk konfirmasi. Jiumi mencadangkan APK utama beserta APK terpisahnya jika ada. Anda dapat mencoba memasang ulang aplikasi dari **Apps › Uninstalled**. **Mencopot aplikasi menghapus datanya. Cadangan hanya berisi berkas pemasangan.** |
| Copot aplikasi bawaan | Aktifkan **Allow uninstalling preinstalled apps** di **Settings › App management** untuk mencopot aplikasi bawaan yang didukung bagi pengguna saat ini. Salinan bawaan dan data aplikasi tetap disimpan. Anda dapat mencoba memulihkan aplikasi dari **Apps › Uninstalled**. Aplikasi yang dicopot ditandai ikon tempat sampah. Jika opsi ini nonaktif, mengetuk **Uninstall** menjelaskannya dan membawa Anda ke **App management**. |

Di **Settings › App management**, **Runs as** menunjukkan identitas pengguna yang menjalankan tindakan. Dengan akses root atau Shizuku yang dijalankan sebagai root, tindakan menonaktifkan dan mengaktifkan aplikasi serta mencopot dan memulihkan aplikasi bawaan dijalankan sebagai root. Dengan begitu, Jiumi bisa mengubah aplikasi sistem yang tidak dapat diubah oleh pengguna shell. Dengan proses debug nirkabel atau jaringan, tindakan tersebut dijalankan sebagai pengguna shell. Jika ponsel memblokir perubahan pada aplikasi sistem tertentu, Jiumi menampilkan alasannya. Mencopot aplikasi yang Anda pasang hanya memengaruhi pengguna saat ini. Salinan di profil kerja atau ruang kedua tetap terpasang.

Komponen inti sistem dilindungi secara bawaan. Mengubah komponen yang dibutuhkan ponsel dapat memengaruhi fungsi sistem. Jiumi menampilkan peringatan sebelum melakukan perubahan tersebut.

## Bagaimana Jiumi melihat aktivitas latar belakang tanpa root?

Saat aplikasi berjalan, Android menyediakan catatan akses izin, tabel koneksi, rekaman penelusuran sistem, dan log peristiwa. Monitor Jiumi membaca sinyal sistem yang tersedia sebagai **pengguna shell**. Aplikasi mengelompokkannya berdasarkan aturan dan menyimpan catatan di basis data pada ponsel. Pemantauan tidak menyuntikkan kode ke aplikasi lain. Anda perlu memulai sendiri tindakan seperti menonaktifkan atau mencopot aplikasi, menonaktifkan izin, atau memblokir akses jaringan melalui pengelolaan aplikasi.

<p align="center"><img src="../images/how-it-works.en.png" alt="Aplikasi menghasilkan sinyal sistem Android. Monitor Jiumi membacanya sebagai pengguna shell. Aplikasi Jiumi mengelompokkan dan menyimpan catatan di ponsel. Pencatatan domain opsional menerima kueri DNS melalui VPN lokal." width="900" /></p>

| Sumber sinyal | Yang dapat dilihat Jiumi |
| --- | --- |
| Catatan akses izin (AppOps) | Aplikasi yang menggunakan izin dan waktu penggunaannya, beserta riwayat yang disimpan Android. |
| Tabel koneksi | Alamat yang dihubungi oleh aplikasi. Secara bawaan, sampel diambil setiap lima detik. |
| Penelusuran sistem (Perfetto) | Panggilan layanan sistem yang dapat dikenali dan lalu lintas per koneksi jika didukung sistem. |
| Log peristiwa | Waktu suatu proses dimulai dan alasan yang dicatat Android. |

### Empat cara terhubung

| Metode | Cocok untuk | Penyiapan pertama | Setelah ponsel dimulai ulang |
| --- | --- | --- | --- |
| **Proses debug nirkabel** (disarankan) | Sebagian besar ponsel, tanpa root atau komputer | Sambungkan Wi-Fi dan masukkan kode sekali di ponsel | Penyambungan tersimpan. Wi-Fi dan proses debug nirkabel harus aktif |
| **Proses debug jaringan** | Ponsel dengan port ADB yang sudah terbuka | Cari atau masukkan port dan izinkan di Android jika diminta. Tanpa kode | Ponsel harus membuka port lagi |
| **Shizuku** | Pengguna Shizuku | Jalankan Shizuku dan izinkan Jiumi | Shizuku harus berjalan |
| **Root** | Ponsel yang sudah di-root | Izinkan Jiumi di pengelola root | Berfungsi selama izin berlaku |

Keempatnya menjalankan monitor sebagai pengguna shell. Hanya root, atau Shizuku yang dijalankan sebagai root, yang menjalankan tindakan pengelolaan di atas sebagai root.

> **Keamanan proses debug jaringan:** Jiumi hanya terhubung ke alamat ponsel itu sendiri dan tidak membuka port untuk Anda. Port yang dibuka dengan `adb tcpip` atau cara serupa biasanya dapat diakses perangkat lain di jaringan yang sama. Gunakan hanya di jaringan tepercaya, jangan izinkan permintaan debug yang tidak dikenal, dan tutup port saat tidak diperlukan.

**Pencatatan domain opsional** menggunakan VPN lokal untuk menerima kueri DNS dan meneruskannya tanpa perubahan ke server DNS jaringan yang sedang digunakan. Lalu lintas lainnya langsung menuju tujuan. Pencatatan domain memakai slot VPN sistem. Kueri yang menggunakan DNS terenkripsi bawaan aplikasi atau penyedia DNS Pribadi yang ditentukan melalui nama host mungkin tidak tercatat.

## Mengunduh dan mulai memantau

1. Pada ponsel ARM64 dengan Android 11 atau lebih baru, unduh dan pasang APK dari [GitHub Releases](https://github.com/ybblab/Jiumi/releases/latest).
2. Saat pertama membuka Jiumi, pilih metode untuk memulai. Jika menggunakan proses debug nirkabel, yaitu metode yang disarankan, sambungkan ke Wi-Fi dan ikuti panduan untuk mengizinkan notifikasi dan mengaktifkan opsi developer. Masukkan kode penyambungan enam digit yang ditampilkan sistem ke notifikasi Jiumi. Jika notifikasi tidak dapat digunakan, ketuk “Can’t type in the notification?” di langkah 3 lalu masukkan kode lewat layar terpisah, jendela kecil, atau kartu Setelan Cepat. Jika port ADB sudah terbuka, Anda juga dapat memilih proses debug jaringan.
3. Ketuk **Start monitoring** pada halaman **Monitor**. Selama pemantauan berlangsung, notifikasi tetap muncul. Anda dapat menghentikan pemantauan dari notifikasi atau dari aplikasi.

<p align="center">
  <a href="../images/pairing-split.en.png"><img src="../images/pairing-split.en.png" width="196" alt="Cara lain menyambungkan: layar terpisah atau jendela kecil" /></a>&nbsp;
  <a href="../images/pairing-tile.en.png"><img src="../images/pairing-tile.en.png" width="196" alt="Cara lain menyambungkan: kartu Setelan Cepat" /></a>
</p>

Setelah ponsel dimulai ulang, Jiumi diperbarui, atau layanan debug terputus, Jiumi mencoba melanjutkan sesi yang sama saat koneksi yang dipilih tersedia, dan mencoba lagi saat Anda kembali ke aplikasi. Jika kartu menampilkan **Monitoring interrupted**, pulihkan koneksi lalu ketuk **Resume**. Waktu saat pemantauan terputus tidak dihitung sebagai durasi pemantauan.

Jika monitor berhenti saat memakai proses debug jaringan, Jiumi mencoba port terakhir atau mencari kembali port lokal untuk melanjutkan sesi. Kartu memberi tahu jika port tidak ditemukan atau izin diperlukan.

Saat menggunakan proses debug nirkabel, mengaktifkan proses debug USB juga dapat membantu monitor tetap berjalan jika Android mematikannya ketika Wi-Fi terputus. **Settings › Connection › Backup connection** nonaktif secara bawaan dan hanya untuk ponsel yang sistemnya sudah menyediakan port ADB terbatas pada perangkat itu sendiri. Jiumi memverifikasi dan memakai kembali port itu tanpa membuka port baru. Lihat [panduan penyiapan](https://jiumi.ybblab.com/en/download/) untuk informasi lebih lanjut.

## Privasi dan batasan

- **Catatan tetap tersimpan di ponsel.** Jiumi tidak menggunakan akun dan tidak mengunggah catatan pemantauan. Aplikasi dapat memeriksa pembaruan di GitHub. Jika Anda memilih untuk memperbarui, Jiumi mengunduh APK dan memverifikasi ukuran, hash SHA-256, serta tanda tangannya. Pemeriksaan pembaruan dapat dinonaktifkan.
- **Catatan menunjukkan aktivitas dan koneksi.** Jiumi dapat menunjukkan bahwa izin digunakan atau akses ditolak. Jiumi tidak dapat melihat pesan, kata sandi, atau isi lalu lintas jaringan.
- **Cakupan bergantung pada sistem.** Koneksi singkat dapat berakhir di antara pengambilan sampel. Tindakan seperti membaca berkas secara langsung mungkin tidak menghasilkan sinyal yang dapat digunakan. Pengukuran lalu lintas per koneksi memerlukan Android 14 atau lebih baru dan dukungan sistem. Untuk mengurangi peringatan keliru, Jiumi mengabaikan sebagian aktivitas yang tidak dapat dikonfirmasi.
- **Ringkasan dan peringkat menghitung secara berbeda.** Ringkasan menambahkan hitungan per rentang waktu yang disimpan Android, yang dapat mencakup waktu sebelum pemantauan. Peringkat hanya menghitung catatan Jiumi satu per satu. Jika sesi melebihi 31 hari, ringkasan **Session** dan notifikasi hanya menghitung 31 hari pertama; gunakan **30 days**.
- **Pemantauan menggunakan lebih banyak daya baterai.** Besarnya bergantung pada aktivitas aplikasi. Hentikan pemantauan dari notifikasi jika sudah tidak diperlukan.

## Membangun dari kode sumber

Diperlukan JDK 17 dan Android SDK.

```sh
cd code
./gradlew :app:assembleDebug
```

Kategori pemantauan, aturan peringatan, dan aplikasi yang dilindungi dikonfigurasi di [direktori aturan](../../code/app/src/main/assets/rules/). Lihat [CONTRIBUTING.md](../../CONTRIBUTING.md) untuk struktur kode dan cara berkontribusi.

## Kontribusi dan lisensi

Kami menyambut laporan masalah dan pull request. Laporkan masalah keamanan secara privat sesuai petunjuk di [SECURITY.md](../../SECURITY.md).

Jiumi menggunakan [Lisensi Apache 2.0](../../LICENSE). Komponen pihak ketiga beserta lisensinya tercantum di [NOTICE](../../NOTICE). Gunakan Jiumi pada perangkat milik Anda atau yang penggunaannya telah diizinkan.

## Riwayat bintang

<a href="https://www.star-history.com/#ybblab/Jiumi&amp;Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date&amp;theme=dark" />
    <img alt="Grafik riwayat bintang Jiumi di GitHub" src="https://api.star-history.com/svg?repos=ybblab/Jiumi&amp;type=Date" />
  </picture>
</a>

<div align="center">

**Jika Jiumi membantu Anda memahami aktivitas aplikasi, beri proyek ini ⭐ di GitHub.**

</div>
