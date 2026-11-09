package com.movtery.zalithlauncher.feature.ysm

import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.util.zip.ZipFile

/**
 * Dukungan Yes Steve Model (YSM) di Fabric.
 * YSM 2.6.x membawa lib native Android (META-INF/native/libysm-core-android.so, aarch64).
 * Lib itu di-ekstrak oleh mod ke penyimpanan bersama (noexec) sehingga bisa gagal/macet.
 * Di sini lib di-ekstrak lebih dulu ke penyimpanan internal aplikasi (boleh dieksekusi)
 * lalu dipasang lewat variabel lingkungan YSM_CORE_LIB.
 */
object YsmSupport {
    private const val ENTRY = "META-INF/native/libysm-core-android.so"
    private const val TAG = "YsmSupport"

    /** @return path lib native Android YSM, atau null jika tidak ada mod YSM */
    @JvmStatic
    fun prepareCoreLib(modsDir: File?): String? {
        if (modsDir == null || !modsDir.isDirectory) return null
        val jars = modsDir.listFiles { f -> f.isFile && f.name.endsWith(".jar", ignoreCase = true) } ?: return null
        for (jar in jars) {
            val out = runCatching { extractIfYsm(jar) }.onFailure {
                Logging.e(TAG, "Gagal membaca ${jar.name}: ${it.message}")
            }.getOrNull()
            if (out != null) return out
        }
        return null
    }

    private fun extractIfYsm(jar: File): String? {
        ZipFile(jar).use { zip ->
            // Cari lib native Android milik YSM di semua versi YSM (nama bisa berbeda antar versi)
            val entry = zip.getEntry(ENTRY) ?: zip.entries().asSequence().firstOrNull {
                !it.isDirectory && it.name.startsWith("META-INF/native/") && it.name.endsWith(".so") &&
                    it.name.contains("ysm", ignoreCase = true) && it.name.contains("android", ignoreCase = true)
            } ?: return null
            val base = PathManager.DIR_RUNTIME_MOD ?: return null
            val dir = File(base, "ysm").apply { mkdirs() }
            // nama file memuat ukuran + waktu ubah jar agar ikut berganti saat YSM di-update
            val target = File(dir, "libysm-core-${entry.size}-${jar.lastModified()}.so")
            if (!target.exists() || target.length() != entry.size) {
                val tmp = File(dir, target.name + ".tmp")
                zip.getInputStream(entry).use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return null
                }
                dir.listFiles { f -> f.name.endsWith(".so") && f != target }?.forEach { it.delete() }
                Logging.i(TAG, "YSM native diekstrak: ${target.absolutePath}")
            }
            target.setReadable(true, false)
            target.setExecutable(true, false)
            return target.absolutePath
        }
    }
}
