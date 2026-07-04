# n2live Sprite Engine (Android + OpenGL ES 2.0)

Kerangka kerja Sprite Engine sesuai arsitektur yang diminta:
- Rendering via `GLSurfaceView` + shader custom (bukan `ImageView`).
- VBO quad dibuat sekali (`SpriteRenderer.buildQuadBuffers`), tidak dihitung ulang tiap frame.
- Config JSON (`config.json`) disimpan di Internal Storage lewat `ConfigManager`.
- Folder Scanner via Storage Access Framework (`AssetScanner`).
- Settings UI: Layer Manager (Z-order naik/turun), Animation Builder (state/fps/frame picker), dan Preview Panel real-time (30 FPS) yang terpisah dari Live Mode (60 FPS).

## Cara Import
1. Buka Android Studio -> Open -> pilih folder `n2live-sprite-engine`.
2. Sync Gradle.
3. Jalankan module `app`.

## Alur Pakai
1. Buka **Settings** -> **Pilih Folder PNG** -> pilih folder berisi sprite (mis. `idle_1.png`, `talk_1.png`, dst). File otomatis di-scan & di-copy ke internal storage.
2. **+ Tambah Layer** untuk tiap bagian karakter (mis. `body`, `mata`, `mulut`). Atur urutan tampil dengan tombol ▲▼ (Z-order).
3. Di **Animation Builder**: pilih layer -> pilih nama state (`idle`/`talk`/`angry`) -> centang PNG yang jadi frame -> atur FPS & Loop -> **Simpan State ke Layer**.
4. Lihat hasilnya langsung di **Preview Panel** (tombol Idle/Talk/Angry).
5. **SIMPAN config.json** untuk menulis manifest ke Internal Storage.
6. Kembali ke **MainActivity** (Live Mode) -> otomatis load `config.json` terbaru saat `onResume()`.

## Integrasi dengan AI (state listener)
Panggil dari mana saja setelah `SpriteGLSurfaceView.init()`:
```kotlin
glSurfaceView.setState("talk")   // saat AI deteksi audio/teks masuk
glSurfaceView.setState("angry")  // saat AI deteksi ekspresi tertentu
```
State non-loop (mis. `angry`) otomatis memicu `StateListener.onStateFinished()` saat frame terakhir tercapai — dipakai `MainActivity` untuk auto-kembali ke `idle`.

## Catatan Optimasi Lanjutan (belum diimplementasikan di versi ini)
- **Texture Atlas**: saat ini tiap PNG dimuat sebagai texture GPU terpisah (`TextureLoader`), supaya arsitekturnya sederhana untuk MVP. Untuk produksi, gabungkan PNG jadi 1 atlas (TexturePacker/Free Texture Packer), lalu ubah `TextureLoader` agar hanya bind 1 texture dan `SpriteRenderer` menghitung offset UV per frame dari koordinat atlas (butuh file mapping tambahan, mis. `atlas.json` berisi x,y,w,h tiap frame).
- **Background thread untuk build atlas**: kalau atlas ditambahkan, proses packing sebaiknya dijalankan di `Dispatchers.IO`/`AsyncTask` supaya UI tidak freeze saat scan folder besar.
- **Frame skipping/refresh rate**: sudah ada implementasi `setFrameRate` (API 30+) pada `Surface` di `SpriteGLSurfaceView`; untuk device lama, gunakan `Choreographer` custom kalau perlu kontrol FPS lebih presisi.
