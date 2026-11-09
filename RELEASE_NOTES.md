## Zalith Launcher Mod 1.4.1.8.4

### Baru di versi ini
- **YSM (Yes Steve Model):** lib JNA native kini dipilih otomatis sesuai versi JNA milik game (5.13.0 - 5.19.1), seperti launcher resmi generasi baru. Ini upaya memperbaiki YSM yang macet di Fabric.
- **YSM semua versi:** lib native Android YSM dicari di semua versi YSM yang membawanya, tidak hanya 2.6.5 untuk 1.21.1.
- **LWJGL 3.4.1:** komponen diperiksa kelengkapannya sebelum dipakai; jika kurang, otomatis kembali ke LWJGL 3.3.x, tidak crash. Log mencatat kapan 3.4.1 aktif.
- **Layar crash:** menambah petunjuk penyebab untuk error LWJGL (fungsi tidak cocok, library native tidak ditemukan).
- **Mod Management:** mod nonaktif kini ditandai (nama abu-abu dicoret + lencana NONAKTIF, ikon redup, akhiran .disabled disembunyikan); ikon mod dibaca dari dalam jar dan ditampilkan dengan sudut membulat.
- **HP RAM kecil:** jumlah thread kompilasi JIT dibatasi agar tidak berebut CPU dengan thread render.

### Catatan jujur
- Perbaikan YSM dan LWJGL 3.4.1 di atas belum terbukti di semua HP. Jika masih macet atau crash, kirim `latest.log` saat membuat laporan.
- "Paksa LWJGL 3.4.1" di game lama (mis. 1.21.1) bisa crash karena game dibuat untuk LWJGL 3.3.x; biarkan mati kecuali perlu.
- APK diberi tanda tangan debug.keystore; hapus versi lama dulu sebelum memasang jika terjadi konflik tanda tangan.

### Pilih APK
- `arm64` untuk HP ARM 64-bit (mis. Oppo A3s), `arm` untuk 32-bit, `all` kalau ragu.

---

### Sebelumnya (1.4.1.8)
- Video background, LWJGL 3.4.1, renderer NG-GL4ES, skin offline, CrashAnalyzer, dukungan YSM, ikon mod, konverter layout kontrol ZL2.
