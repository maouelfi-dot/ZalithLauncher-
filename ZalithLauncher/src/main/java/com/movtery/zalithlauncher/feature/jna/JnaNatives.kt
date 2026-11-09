package com.movtery.zalithlauncher.feature.jna

import android.content.Context
import android.os.Build
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.version.Version
import com.movtery.zalithlauncher.utils.path.PathManager
import org.json.JSONObject
import java.io.File

/**
 * Menyediakan libjnidispatch.so yang versinya SAMA dengan JNA milik game.
 * Beberapa mod (contoh: Yes Steve Model) memuat lib native lewat JNA; bila lib native JNA bawaan
 * launcher berbeda versi dari jar JNA game, pemuatan bisa gagal atau macet.
 * Binari tersedia untuk JNA 5.13.0 - 5.19.1 (assets/jna/<versi>/<abi>/libjnidispatch.so).
 */
object JnaNatives {
    private const val TAG = "JnaNatives"
    private val LIB_REGEX = Regex("""^net\.java\.dev\.jna:jna:(\d+(?:\.\d+)+)""")

    /** Folder hasil ekstrak untuk peluncuran saat ini, atau null bila memakai bawaan launcher. */
    @Volatile
    @JvmField
    var currentDir: String? = null

    @JvmStatic
    fun prepare(context: Context, gameVersion: Version?) {
        currentDir = null
        if (gameVersion == null) return
        runCatching {
            val version = detectJnaVersion(gameVersion.getVersionPath()) ?: return
            val assets = context.assets
            val available = assets.list("jna")?.toSet() ?: return
            if (version !in available) {
                Logging.i(TAG, "JNA $version tidak ada di paket launcher, memakai bawaan")
                return
            }
            val abi = Build.SUPPORTED_ABIS.firstOrNull { !assets.list("jna/$version/$it").isNullOrEmpty() } ?: return
            val outDir = File(PathManager.DIR_FILE, "jna_natives/$version").apply { mkdirs() }
            val out = File(outDir, "libjnidispatch.so")
            assets.open("jna/$version/$abi/libjnidispatch.so").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out.setReadable(true, false)
            out.setExecutable(true, false)
            currentDir = outDir.absolutePath
            Logging.i(TAG, "JNA native $version ($abi) dipakai dari ${outDir.absolutePath}")
        }.onFailure { Logging.e(TAG, "Gagal menyiapkan JNA native: ${it.message}") }
    }

    private fun readLibraries(jsonFile: File, found: MutableList<String>): String? {
        if (!jsonFile.isFile) return null
        val json = JSONObject(jsonFile.readText().removePrefix("\uFEFF"))
        val libs = json.optJSONArray("libraries")
        if (libs != null) {
            for (i in 0 until libs.length()) {
                val name = libs.optJSONObject(i)?.optString("name") ?: continue
                LIB_REGEX.find(name)?.groupValues?.get(1)?.let(found::add)
            }
        }
        return json.optString("inheritsFrom").takeIf { it.isNotEmpty() }
    }

    private fun detectJnaVersion(versionDir: File): String? {
        val found = ArrayList<String>()
        var parent = readLibraries(File(versionDir, "${versionDir.name}.json"), found)
        var guard = 0
        while (parent != null && guard++ < 4) {
            val pDir = File(versionDir.parentFile, parent)
            parent = readLibraries(File(pDir, "$parent.json"), found)
        }
        return found.maxWithOrNull(compareBy { v -> v.split('.').map { it.toIntOrNull() ?: 0 }.fold(0L) { acc, n -> acc * 1000 + n } })
    }
}
