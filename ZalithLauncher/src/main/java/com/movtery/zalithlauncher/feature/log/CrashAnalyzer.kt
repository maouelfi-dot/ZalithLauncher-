package com.movtery.zalithlauncher.feature.log

import java.io.File
import java.io.RandomAccessFile

/**
 * Membaca akhir latest.log (dan hs_err_pid*.log bila ada) lalu menebak penyebab game crash,
 * agar pesan crash tidak lagi hanya "exit code N".
 */
object CrashAnalyzer {
    private const val MAX_BYTES = 600 * 1024L

    private fun tail(file: File): String {
        if (!file.isFile) return ""
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val start = (len - MAX_BYTES).coerceAtLeast(0)
                raf.seek(start)
                val buf = ByteArray((len - start).toInt())
                raf.readFully(buf)
                String(buf, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    @JvmStatic
    fun analyze(gameDir: File?): String? {
        if (gameDir == null) return null
        val latest = tail(File(gameDir, "logs/latest.log"))
        val hs = gameDir.listFiles { f -> f.name.startsWith("hs_err_pid") && f.name.endsWith(".log") }
            ?.maxByOrNull { it.lastModified() }
            ?.takeIf { System.currentTimeMillis() - it.lastModified() < 10 * 60 * 1000L }
            ?.let { tail(it) } ?: ""
        val text = latest + "\n" + hs
        if (text.isBlank()) return null

        val hints = ArrayList<String>()

        if (text.contains("java.lang.OutOfMemoryError") || text.contains("Java heap space")) {
            hints += "Memori Java (heap) habis. Kurangi jumlah mod, turunkan render distance, atau naikkan alokasi RAM jika HP masih punya sisa."
        }
        if (text.contains("Direct buffer memory") || text.contains("Cannot allocate memory") ||
            text.contains("native memory", ignoreCase = true) && text.contains("insufficient", ignoreCase = true)) {
            hints += "RAM native habis (biasanya karena shader, tekstur besar, atau RAM alokasi terlalu besar). Turunkan alokasi RAM Java atau matikan shader."
        }
        if (Regex("""(NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|AbstractMethodError|IncompatibleClassChangeError)[^\n]*org[./]lwjgl""")
                .containsMatchIn(text)) {
            hints += "Game atau mod memakai fungsi LWJGL 3.3.x yang tidak cocok dengan LWJGL 3.4.1. Matikan \"Paksa LWJGL 3.4.1\" di pengaturan Video untuk game/mod versi lama (misalnya 1.21.1)."
        }
        if (Regex("""(Failed to locate library|Unable to load library)[^\n]*lwjgl""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            hints += "Library native LWJGL tidak ditemukan. Komponen LWJGL 3.4.1 mungkin belum terpasang lengkap; buka ulang launcher agar komponen diekstrak ulang."
        }
        Regex("""UnsatisfiedLinkError[^\n]*""").find(text)?.let {
            hints += "Library native tidak cocok dengan HP ini (${it.value.take(160)}). Mod tersebut mungkin tidak mendukung Android/arsitektur HP."
        }
        Regex("""(?:MixinApplyError|InvalidInjectionException|Mixin apply failed|MixinTransformerError)[^\n]*""").find(text)?.let { m ->
            val mod = Regex("""from mod ([A-Za-z0-9_\-]+)""").find(text)?.groupValues?.get(1)
            hints += "Mod saling bentrok (mixin gagal)" + (mod?.let { " - mod: $it" } ?: "") + ". Coba matikan mod yang baru ditambahkan."
        }
        if (text.contains("Incompatible mod set") || text.contains("Mod resolution failed") ||
            text.contains("Could not find required mod") || text.contains("requires any version of")) {
            val line = Regex("""(?:Mod '[^\n]+|[^\n]*requires[^\n]+)""").find(text)?.value?.take(200)
            hints += "Ada mod yang kekurangan mod pendukung atau versinya tidak cocok" + (line?.let { ": $it" } ?: ".")
        }
        if (text.contains("Failed to compile shader", ignoreCase = true) ||
            text.contains("Shader compilation failed", ignoreCase = true) ||
            text.contains("GLSL", ignoreCase = true) && text.contains("error", ignoreCase = true)) {
            hints += "Shader gagal dikompilasi oleh renderer. Coba shader lain atau renderer lain."
        }
        if (text.contains("eglCreateContext") || text.contains("EGL_BAD") || text.contains("GLFW error")) {
            hints += "Masalah grafis/EGL pada renderer. Coba ganti renderer di pengaturan."
        }
        if (text.contains("SIGSEGV") || text.contains("SIGABRT") || text.contains("A fatal error has been detected")) {
            val frame = Regex("""# C\s+\[([^\]]+)\]""").find(text)?.groupValues?.get(1)
            hints += "Crash native (SIGSEGV/SIGABRT)" + (frame?.let { " di $it" } ?: "") + "."
        }
        if (text.contains("yes_steve_model", ignoreCase = true) &&
            Regex("""(?i)(ysm|yes_steve_model)[^\n]*(Exception|Error)""").containsMatchIn(text)) {
            hints += "Ada error dari mod Yes Steve Model. Pastikan versi YSM sesuai dan model tidak rusak."
        }

        if (hints.isEmpty()) {
            Regex("""(?m)^(?:Caused by: |Exception in thread )[^\n]+""").findAll(text).lastOrNull()?.let {
                hints += "Error terakhir di log: ${it.value.take(220)}"
            }
        }
        return if (hints.isEmpty()) null else hints.distinct().joinToString("\n\n") { "• $it" }
    }
}
