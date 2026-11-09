package com.movtery.zalithlauncher.feature.mod

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors
import java.util.zip.ZipFile

/**
 * Memuat ikon/logo mod dari dalam file .jar (atau .jar.disabled) untuk daftar Mod Management.
 * Sumber ikon: fabric.mod.json -> "icon", quilt.mod.json -> metadata.icon,
 * META-INF/mods.toml -> logoFile, terakhir pack.png.
 */
object ModIconLoader {
    fun interface Callback {
        fun onLoaded(bitmap: Bitmap?)
    }

    private const val MAX_SIZE = 96
    private val cache = LruCache<String, Bitmap>(96)
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    @JvmStatic
    fun isModFile(file: File?): Boolean {
        if (file == null || !file.isFile) return false
        val n = file.name.lowercase()
        return n.endsWith(ModUtils.JAR_FILE_SUFFIX) || n.endsWith(ModUtils.DISABLE_JAR_FILE_SUFFIX)
    }

    private fun key(file: File) = "${file.absolutePath}|${file.lastModified()}"

    @JvmStatic
    fun getCached(file: File): Bitmap? = cache.get(key(file))

    /** true jika sudah diketahui bahwa mod ini tidak punya ikon */
    @JvmStatic
    fun isKnownMissing(file: File): Boolean = missing.contains(key(file))

    @JvmStatic
    fun load(file: File, callback: Callback) {
        val k = key(file)
        cache.get(k)?.let { callback.onLoaded(it); return }
        if (missing.contains(k)) { callback.onLoaded(null); return }
        executor.execute {
            val bmp = runCatching { readIcon(file) }.getOrNull()
            if (bmp != null) cache.put(k, bmp) else missing.add(k)
            main.post { callback.onLoaded(bmp) }
        }
    }

    private fun readText(zip: ZipFile, name: String): String? {
        val e = zip.getEntry(name) ?: return null
        return zip.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }.removePrefix("\uFEFF")
    }

    private fun pickIcon(any: Any?): String? = when (any) {
        is String -> any
        is JSONObject -> {
            var best: String? = null
            var bestSize = -1
            val keys = any.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val size = k.toIntOrNull() ?: 0
                if (size > bestSize) { bestSize = size; best = any.optString(k) }
            }
            best
        }
        else -> null
    }

    private fun readIcon(file: File): Bitmap? {
        ZipFile(file).use { zip ->
            val candidates = ArrayList<String>()
            readText(zip, "fabric.mod.json")?.let {
                runCatching { pickIcon(JSONObject(it).opt("icon")) }.getOrNull()?.let(candidates::add)
            }
            readText(zip, "quilt.mod.json")?.let {
                runCatching {
                    pickIcon(JSONObject(it).optJSONObject("quilt_loader")?.optJSONObject("metadata")?.opt("icon"))
                }.getOrNull()?.let(candidates::add)
            }
            for (toml in listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml")) {
                readText(zip, toml)?.let {
                    Regex("""logoFile\s*=\s*["']([^"']+)["']""").find(it)?.groupValues?.get(1)?.let(candidates::add)
                }
            }
            candidates.add("pack.png")
            for (c in candidates) {
                val entry = zip.getEntry(c.trimStart('/')) ?: continue
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                decode(bytes)?.let { return it }
            }
        }
        return null
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= MAX_SIZE && opts.outHeight / (sample * 2) >= MAX_SIZE) sample *= 2
        val real = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, real)
    }
}
