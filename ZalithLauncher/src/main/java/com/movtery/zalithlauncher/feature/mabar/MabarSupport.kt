package com.movtery.zalithlauncher.feature.mabar

import com.movtery.zalithlauncher.feature.log.Logging
import java.io.File
import java.security.MessageDigest

/** Data akun yang dibutuhkan Mabar (dipisah dari MinecraftAccount supaya MabarLan tidak bergantung ke kelas Java). */
class MabarAccount(
    val username: String,
    val profileId: String,
    val skinFile: File,
    val capeFile: File,
    val slim: Boolean
)

/** Pembungkus kecil agar kode Mabar memakai sistem log Zalith 1. */
internal object Logger {
    fun info(tag: String, msg: String) = Logging.i(tag, msg)

    fun warning(tag: String, msg: String, e: Throwable? = null) {
        if (e != null) Logging.w(tag, msg, e) else Logging.w(tag, msg)
    }

    fun error(tag: String, msg: String, e: Throwable? = null) {
        if (e != null) Logging.e(tag, msg, e) else Logging.e(tag, msg)
    }
}

internal object DigestUtils {
    fun digestToString(algorithm: String, data: ByteArray): String =
        MessageDigest.getInstance(algorithm).digest(data).joinToString("") { "%02x".format(it) }
}
