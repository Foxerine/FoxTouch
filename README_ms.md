# FoxTouch

[English](README.md) | [简体中文](README_zh-CN.md) | [繁體中文](README_zh-TW.md) | [日本語](README_ja.md) | [한국어](README_ko.md) | [Español](README_es.md) | **Bahasa Melayu**

FoxTouch ialah ejen telefon berkuasa AI untuk Android, diilhamkan oleh [Claude Code](https://docs.anthropic.com/en/docs/claude-code). Ia memerhati skrin melalui Perkhidmatan Kebolehcapaian, memahami elemen UI, dan melakukan tindakan bagi pihak pengguna melalui arahan bahasa semula jadi — membawa pengalaman ejen pengkodan AI ke skrin telefon.

> **Status: Bukti Konsep / Dalam Pembangunan**
>
> Projek ini berada dalam peringkat eksperimen awal. Ciri-ciri mungkin tidak lengkap, tidak stabil, atau berubah dengan ketara.

### ⚠️ Penafian Penting

**Kod dijana oleh AI**: Sebahagian besar kod projek ini dijana oleh AI (Claude), dengan hala tuju dan semakan manusia. Ini termasuk seni bina aplikasi, pelaksanaan UI, reka bentuk sistem alat, integrasi kebolehcapaian, dan prompt sistem.

**Guna atas risiko sendiri**: Perisian ini disediakan "seadanya", tanpa sebarang jaminan. Ejen AI boleh melakukan tindakan pada telefon anda termasuk ketik, taip, leret, dan lancarkan aplikasi. **Anda bertanggungjawab sepenuhnya terhadap sebarang akibat penggunaan perisian ini.** Penulis tidak menanggung sebarang liabiliti untuk kerosakan, kehilangan data, pembelian tidak disengajakan, atau masalah lain yang mungkin timbul daripada penggunaannya.

### Cara ia berfungsi

1. Pengguna menerangkan tugas dalam bahasa semula jadi
2. FoxTouch membaca skrin menggunakan API Kebolehcapaian Android
3. LLM (boleh dikonfigurasi) menganalisis UI dan memutuskan tindakan
4. FoxTouch melaksanakan tindakan (ketik, taip, tatal, leret, dll.) melalui Perkhidmatan Kebolehcapaian
5. Kitaran pemerhatian-fikir-bertindak berulang sehingga tugas selesai

### Kes Penggunaan

<details>
<summary><b>🛵 Pesanan Makanan Pintar dengan Kupon</b></summary>

> **Anda:** Tolong pesankan makanan dari Grab Food, nak capai RM25 untuk guna kupon diskaun 30%. Ikut keutamaan rasa yang saya bagitahu semalam — tak nak pedas, lebih suka ayam.
>
> **FoxTouch:** ✅ Faham. Saya ingat keutamaan anda dari semalam: elak pedas, lebih suka ayam. Biar saya buka Grab Food dan cari pilihan yang sesuai.
>
> **FoxTouch:** 🔍 Saya jumpa beberapa pilihan ayam berhampiran anda. Nasi Ayam Penyet (RM14.90) + Sup Ayam (RM8.90) + Air Teh Ais (RM3.50) = RM27.30, layak untuk kupon diskaun 30%. Nak saya teruskan pesanan ini?
>
> **Anda:** Ok teruskan
>
> **FoxTouch:** ✅ Kupon digunakan, diskaun RM8.19. Jumlah akhir RM19.11. Bersedia untuk buat pembayaran. Sahkan untuk teruskan?

*Keupayaan yang digunakan: memori (ingat keutamaan), pemerhatian (semak harga dan kupon), sahkan sebelum bayar*
</details>

<details>
<summary><b>🎮 Daftar Masuk Harian Permainan</b></summary>

> **Anda:** Tolong daftar masuk harian untuk Genshin Impact
>
> **FoxTouch:** 🎮 Buka Genshin Impact sekarang...
>
> **FoxTouch:** ✅ Daftar masuk harian selesai! Anda dapat 60 Primogem hari ini. Nak saya simpan ini sebagai kemahiran supaya boleh guna semula esok?
>
> **Anda:** Ya simpan
>
> **FoxTouch:** 💾 Disimpan sebagai kemahiran "Daftar Masuk Genshin". Lain kali cakap je "daftar masuk Genshin" dan saya akan jalankan terus.

*Keupayaan yang digunakan: kemahiran (simpan dan guna semula), navigasi (buka dan navigasi aplikasi)*
</details>

<details>
<summary><b>📱 Hantar Mesej dan Tetapkan Peringatan</b></summary>

> **Anda:** Hantar mesej WhatsApp kepada Ahmad cakap "Jumpa pukul 3 petang di KLCC" dan tetapkan penggera pukul 2:30
>
> **FoxTouch:** 📤 Buka WhatsApp, cari Ahmad...
>
> **FoxTouch:** ✅ Mesej dihantar kepada Ahmad. Sekarang tetapkan penggera...
>
> **FoxTouch:** ⏰ Penggera ditetapkan untuk 2:30 PM. Kedua-dua tugas selesai!

*Keupayaan yang digunakan: aliran kerja berbilang aplikasi (WhatsApp + Jam), input teks (taip mesej)*
</details>

<details>
<summary><b>🛒 Beli-belah Pintar di Shopee</b></summary>

> **Anda:** Cari power bank 20000mAh di Shopee, nak yang paling murah tapi rating 4.5 ke atas, dan ada penghantaran percuma
>
> **FoxTouch:** 🔍 Buka Shopee dan cari "power bank 20000mAh"...
>
> **FoxTouch:** 📋 Saya jumpa 3 pilihan terbaik:
> 1. Xiaomi 20000mAh — RM59.90 ⭐4.8 (Penghantaran percuma)
> 2. Baseus 20000mAh — RM65.90 ⭐4.7 (Penghantaran percuma)
> 3. Anker 20000mAh — RM89.90 ⭐4.9 (Penghantaran percuma)
>
> Yang paling murah dengan syarat anda ialah Xiaomi. Nak saya masukkan ke troli?

*Keupayaan yang digunakan: pemerhatian (semak harga, rating, penghantaran), mod rancangan (bandingkan pilihan)*
</details>

### Ciri-ciri Utama

- **Sokongan pelbagai penyedia LLM** — OpenAI, Anthropic Claude, Google Gemini, OpenRouter dan mana-mana API serasi OpenAI
- **Pemahaman skrin** — Penghuraian pokok elemen UI + tangkapan skrin beranotasi dengan grid koordinat dan label elemen
- **Interaksi penuh peranti** — ketik, taip, tatal, leret, tekan lama, cubit, kembali/utama, lancar aplikasi
- **Mod rancangan** — tugas kompleks dirancang dan disemak sebelum dilaksanakan
- **Sistem kemahiran** — simpan dan guna semula pelan tindakan
- **Penjejakan tugas** — kemajuan pelbagai langkah boleh dilihat dalam masa nyata
- **Tindanan terapung** — kawal ejen dari mana-mana aplikasi
- **IME terbina dalam** — kaedah input tidak kelihatan untuk input teks dan akses papan keratan yang boleh dipercayai
- **Tangkapan skrin dipertingkat** — sandaran MediaProjection untuk aplikasi yang menyekat tangkapan skrin kebolehcapaian
- **Pemampatan konteks** — ringkasan perbualan automatik untuk kekal dalam had token
- **Input suara & TTS** — perbualan suara + bacaan respons dengan kuat
- **Memori kekal** — ejen mengingat arahan dan konteks merentas sesi
- **Perlindungan keselamatan** — kelulusan setiap alat, kebenaran tahap risiko, togol mod YOLO
- **UI pelbagai bahasa** — English, 简体中文, 繁體中文, 日本語, 한국어, Español, Bahasa Melayu

### Reka Bentuk Terperinci

<details>
<summary><b>Pemampatan Konteks Automatik</b> — Ringkasan perbualan tanpa gangguan apabila menghampiri had token</summary>

Apabila tugasan panjang memenuhi tetingkap konteks LLM, FoxTouch memampatkan perbualan secara automatik dan bukannya gagal atau kehilangan konteks.

**Ambang bertingkat** — Tetingkap konteks yang lebih kecil dimampatkan lebih awal (70% untuk ≤128K); tetingkap lebih besar boleh diisi lebih banyak (85% untuk >500K). Pangkalan data terbina dalam 100+ model dengan penemuan API masa jalan.

**Ringkasan pintar** — Prompt pemampatan menuntut pemeliharaan butiran yang boleh diambil tindakan: mesej pengguna asal, ID elemen, koordinat, nama pakej, laluan ralat dan "langkah seterusnya" yang konkrit.

**Kesinambungan lancar** — Selepas meringkaskan, bersihkan sejarah, suntik semula prompt sistem dengan konteks peranti terkini, dan tambahkan ringkasan sebagai mesej pengguna dengan arahan "teruskan seolah-olah gangguan tidak pernah berlaku".

</details>

<details>
<summary><b>Sistem Anotasi Tangkapan Skrin</b> — Empat lapisan visual bebas untuk penaakulan ruang LLM</summary>

Ejen boleh meminta sehingga empat lapisan anotasi pada tangkapan skrin:

1. **Grid koordinat** — Garisan setiap 200px sebagai rujukan ruang
2. **Sempadan elemen** — Segi empat berwarna: hijau=boleh klik, biru=boleh tatal, oren=boleh edit, kelabu=lain
3. **Label teks/kelas** — Nama kelas dan kandungan teks elemen
4. **Penanda klik** — Pengesahan selepas tindakan menunjukkan kedudukan ketikan tepat

</details>

<details>
<summary><b>Abstraksi LLM Pelbagai Penyedia</b> — Satu antara muka, tiga API yang berbeza</summary>

Semua penyedia mengeluarkan `Flow<LLMEvent>` bersatu, tetapi mengendalikan perbezaan API secara dalaman:

- **Claude** — Penstriman SSE asli Anthropic. Penormalan peranan mesej dan penggabungan mesej berturut-turut. Sokongan pemikiran lanjutan.

- **Gemini** — Memaksa mod bukan penstriman apabila alat hadir. Tandatangan mesti dikembalikan dalam mesej seterusnya.

- **OpenAI** — Mengubah sifat pilihan kepada jenis nullable dan menjadikan semua sifat wajib.

</details>

<details>
<summary><b>Input Teks 3 Peringkat</b> — Sandaran bertingkat untuk keserasian input teks universal</summary>

1. **IME terbina dalam** (FoxTouchIME) — InputMethodService tidak kelihatan. Paling dipercayai untuk WebView, Flutter dan kawalan tersuai. **Dikecualikan daripada sekatan baca papan keratan Android 10+**.

2. **ACTION_SET_TEXT** — Tindakan kebolehcapaian standard pada nod fokus.

3. **Tampal papan keratan** — Pilihan terakhir: tulis ke papan keratan dan hantar `ACTION_PASTE`.

</details>

<details>
<summary><b>Sistem Keselamatan & Kebenaran</b> — Tahap risiko berperingkat dengan penukaran mod masa jalan</summary>

Setiap alat mengisytiharkan tahap risiko (rendah/sederhana/tinggi). Pengguna boleh membalas permintaan kelulusan dengan: Benarkan, Sentiasa Benarkan, Benarkan Semua (mod YOLO untuk pusingan semasa sahaja) atau Tolak.

</details>

<details>
<summary><b>Mod Rancangan</b> — Fasa pemerhatian terhad sebelum pelaksanaan</summary>

Dalam tugasan kompleks, ejen memasuki mod rancangan di mana hanya alat baca sahaja dan alat perancangan boleh digunakan. Pelan berstruktur dicipta untuk semakan pengguna; alat interaksi hanya dibuka kunci selepas kelulusan.

</details>

<details>
<summary><b>Seni Bina Tindanan</b> — Jetpack Compose dirender dalam perkhidmatan tindanan sistem</summary>

Panel terapung adalah `ComposeView` + `TYPE_APPLICATION_OVERLAY`, dirender dengan `LifecycleOwner` tersuai. Menukar mod fokus secara dinamik mengikut keadaan ejen. Menyembunyikan tindanan sebelum setiap tangkapan skrin dan menunggu 50ms untuk bingkai bersih.

</details>

<details>
<summary><b>Akses Papan Keratan</b> — Pintasan 3 peringkat sekatan papan keratan Android</summary>

Terhadap sekatan baca papan keratan Android 10+: laluan IME (dikecualikan) → konteks perkhidmatan kebolehcapaian → Activity telus (mendapat fokus tetingkap sementara, selesai dalam 2 saat).

</details>

### Muat Turun

APK release hanya **~9 MB** sahaja.

### Keperluan

- Android 11+ (API 30)
- Kebenaran Perkhidmatan Kebolehcapaian
- Kebenaran tindanan (papar atas aplikasi lain)
- Kunci API untuk sekurang-kurangnya satu penyedia LLM yang disokong

### Lesen

Semua hak terpelihara. Projek ini belum diterbitkan di bawah lesen sumber terbuka.
