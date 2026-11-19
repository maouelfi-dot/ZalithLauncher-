package com.movtery.zalithlauncher.feature.accounts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.value.MinecraftAccount
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Port server skin yang sama di semua HP: URL tekstur memakai "http://localhost:<port>/textures/<hash>". */
private const val SHARED_PORT = 24447

/**
 * Kunci penanda tangan TETAP (sama di semua HP). Tiap HP hanya mempercayai kunci server skin miliknya sendiri,
 * jadi profil pemain lain yang dijawab server skin HOST harus memakai kunci yang sama. Bukan rahasia (hanya untuk skin offline lokal).
 * Kunci privat TIDAK di-cast ke RSAPrivateCrtKey (gagal di Android 8.1).
 */
private const val SHARED_KEY_PKCS8 =
        "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCkPazpRSR0P3fFfMvOZQQgHpzN+Df6k1PrcxR0eZF4Y2glqI0C" +
        "R3XyEvzjUb7+qdNE+7By/auhs+LJaArgFTS/8JAR3b2IwMlezAfH7KCA62GUZIGrcXHua0cF2uwLAR9OisXYQUo3EvUE1NuXEopD" +
        "FHFq/8PEmHcJZNUj785f0AJd9oMDR1uNNSWSKBFYh+ahnTxLdIbBRCN2FjhZWetoAFefr8jMHgRaE696DyrF0v5U9sFwWIdEPrBR" +
        "xXbXiH36IAtfk3uIVb8nANNSK2Nh65IkzhICtOKx3NrBcyFLniYJxN0u9mucatPEV6ajh9YJR9BFGnfs+uNF4KdRhWSTAgMBAAEC" +
        "ggEAAkjXvOIfVBafhEaGHZ4K5QxH9hcVZqdIXyZzCsoeG34wFqxpIBz9jc51Ag+pbnVorbVdvcIiWKKquhtEyEH2dK0HjZgRNab1" +
        "phuEbpcWBg6mhTl/EqY5uK1GIaJRiJ2m+weAu2jwSIlukXcJTukNHBdbgVEcpTSeg0NWmHD5F+mqrUVm0fanZf81ehlU1CB8Kata" +
        "qkUqBFB6Q7gaUcqUEA/IaykiaywfOt2cGes4FWnsZhDNTvcUVEVQuZ+h9gtgUXHR7i7DH/AsLPyMIFAMaywc4I5p+CU5yWke6wgN" +
        "TEBqhe/H07gakE0DLyWYbKFHllkIR1hVM9DQFoe/Q6KusQKBgQDb2Zks/HPdvpq/1pNKn+cbuh5mV3GI4EF58FmBliOWi0NV4C7P" +
        "0kOa/UWDFXsN2KhnYsbCNW7LkqdonyD/O5hY68HdpL0z49LLMkxHQeNiMWunLehFiRTU7QweVZPJYUP4jLGNG6JhQUVXUlNhWBub" +
        "d8isglVF/Uke75xyhUbXrwKBgQC/P0J2qxaBx84ax0LaFO5xjwC1JV9b0igEkRd3Nqjy9Jh6IB7qD/FThI8cA0Rbp0uOEzZZ1V7B" +
        "J9ylHgqxGbWE3iO3yFjG8MGZ4PTvy7X+tlwu2TeKJ9mytxS3NiGGjzh8W4QLzpoOD8ecTWchD6JPkPxS8h04aOTndMEQA8AWXQKB" +
        "gGJBjA3o/je6ehsG4WlWsclYk0GWDy3nXBwNaVNbMKiVsRBuUz2acZCRZUHCle6ISCt8n+woSU4+SwedJvgrQFltdIWEnPLV/V3H" +
        "KwQl1GQMRlq/T7HUJDVNhD8CbDxydhhdePThxt7EdyMhrhYxdJU1IcDHtyjCnK1w1XDxU8oNAoGAOnuUzocpHczE+SlgByKJfA53" +
        "7+0ayRBuDl90Jo9/gLRNgGMcMQVpDdomjTFukPyKvUqgvz3heP3AmnmiWV/8Q0JO9997TnLHMVA6dq373IVVkTn5ovTmvKEB47jY" +
        "7bYUVusBaT7nm082AlUlKVYHk39Jue+yAdMJ1MG+XDBZaD0CgYAXrBIyolGbpg71syNiSUIIwL4NBay4T5ZwdUcmKglB0+wawYwa" +
        "hmdWtYycoamqmKxfU85IecFT+KUxc7/xm3PbAEAO/D5wWeDnWBHyK62wQyhD84Ris+ho/9Q1SFprLrIfgoUaNo3tjQzPAjAWFKQY" +
        "nt3CRBSY5FPxloIJ3pujsQ=="

private const val SHARED_KEY_X509 =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApD2s6UUkdD93xXzLzmUEIB6czfg3+pNT63MUdHmReGNoJaiNAkd18hL8" +
        "41G+/qnTRPuwcv2robPiyWgK4BU0v/CQEd29iMDJXswHx+yggOthlGSBq3Fx7mtHBdrsCwEfTorF2EFKNxL1BNTblxKKQxRxav/D" +
        "xJh3CWTVI+/OX9ACXfaDA0dbjTUlkigRWIfmoZ08S3SGwUQjdhY4WVnraABXn6/IzB4EWhOveg8qxdL+VPbBcFiHRD6wUcV214h9" +
        "+iALX5N7iFW/JwDTUitjYeuSJM4SArTisdzawXMhS54mCcTdLvZrnGrTxFemo4fWCUfQRRp37PrjReCnUYVkkwIDAQAB"

/**
 * Server skin lokal yang kompatibel dengan Yggdrasil, supaya akun offline bisa menampilkan skin dan cape di game.
 * Dipakai bersama authlib-injector (`-javaagent:authlib-injector.jar=http://localhost:PORT`).
 *
 * Tanpa dependensi tambahan: ServerSocket biasa + org.json.
 * Mendengarkan di semua antarmuka agar HP teman (hotspot/WiFi) bisa mengunduh tekstur lewat Mabar.
 * Desain endpoint dan properti "textures" bertanda tangan mengikuti protokol authlib-injector yang sama
 * dengan HMCL dan Zalith Launcher 2.
 */
object OfflineSkinServer {
    private const val TAG = "OfflineSkinServer"
    private const val MAX_BODY = 1 shl 20

    private class Character(
        val uuid: String,
        val name: String,
        val skin: ByteArray?,
        val skinHash: String?,
        val cape: ByteArray?,
        val capeHash: String?,
        val slim: Boolean
    )

    private val byUuid = ConcurrentHashMap<String, Character>()
    private val byName = ConcurrentHashMap<String, Character>()
    private var serverSocket: ServerSocket? = null

    /** Dipanggil (di thread koneksi) saat game menanyakan UUID yang belum dikenal; boleh menunggu beberapa detik sambil mengunduh skin teman. */
    @Volatile
    @JvmField
    var unknownProfileResolver: ((String) -> Unit)? = null

    /** Lapisan cadangan: dipanggil bila game meminta tekstur (hash) yang belum ada. */
    @Volatile
    @JvmField
    var unknownTextureResolver: ((String) -> Unit)? = null

    private val keyPair: KeyPair by lazy {
        runCatching {
            val kf = KeyFactory.getInstance("RSA")
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(SHARED_KEY_PKCS8, Base64.DEFAULT)))
            val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(SHARED_KEY_X509, Base64.DEFAULT)))
            KeyPair(pub, priv)
        }.getOrElse { e ->
            Logging.w(TAG, "Kunci tetap gagal dimuat, memakai kunci acak", e)
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        }
    }

    fun skinFileOf(account: MinecraftAccount): File = File(PathManager.DIR_USER_SKIN, account.uniqueUUID + ".png")

    @JvmStatic
    fun capeFileOf(account: MinecraftAccount): File = File(PathManager.DIR_USER_SKIN, account.uniqueUUID + ".cape.png")

    private fun modelFileOf(account: MinecraftAccount): File = File(PathManager.DIR_USER_SKIN, account.uniqueUUID + ".model")

    /** The chosen arm model: "classic" (Steve), "slim" (Alex) or null for automatic detection. */
    @JvmStatic
    fun getModel(account: MinecraftAccount): String? = runCatching {
        modelFileOf(account).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it == "classic" || it == "slim" }
    }.getOrNull()

    /** Saves the arm model for this account ("classic" / "slim"); null goes back to automatic detection. */
    @JvmStatic
    fun setModel(account: MinecraftAccount, model: String?) {
        runCatching {
            val file = modelFileOf(account)
            if (model == null) file.delete() else {
                file.parentFile?.mkdirs()
                file.writeText(model)
            }
        }.onFailure { Logging.e(TAG, "Failed to save skin model: ${it.message}") }
    }

    /** Removes the custom skin and its model choice. */
    @JvmStatic
    fun resetSkin(account: MinecraftAccount) {
        skinFileOf(account).delete()
        modelFileOf(account).delete()
    }

    /** Removes the custom cape. */
    @JvmStatic
    fun resetCape(account: MinecraftAccount) {
        capeFileOf(account).delete()
    }

    /** True jika akun ini memakai arm model slim (pilihan pengguna, atau deteksi otomatis dari file skin). */
    @JvmStatic
    fun isSlim(account: MinecraftAccount): Boolean {
        val file = skinFileOf(account)
        return when (getModel(account)) {
            "slim" -> true
            "classic" -> false
            else -> file.isFile && runCatching { isSlimSkin(file.readBytes()) }.getOrDefault(false)
        }
    }

    private fun mabarEnabled(): Boolean = runCatching { AllSettings.mabarLan.getValue() }.getOrDefault(false)

    /**
     * Starts the server (if needed) and registers the account's local skin/cape.
     * Server juga dijalankan bila Mabar aktif, walau akun tidak punya skin sendiri, supaya skin teman bisa dilihat.
     * @return the port, or null if there is nothing to serve / the server failed to start.
     */
    @JvmStatic
    @Synchronized
    fun prepare(account: MinecraftAccount): Int? {
        val skinFile = skinFileOf(account)
        val capeFile = capeFileOf(account)
        if (!skinFile.isFile && !capeFile.isFile && !mabarEnabled()) return null
        return runCatching {
            val skinBytes = skinFile.takeIf { it.isFile }?.readBytes()
            val capeBytes = capeFile.takeIf { it.isFile }?.readBytes()
            val slim = skinBytes != null && when (getModel(account)) {
                "slim" -> true
                "classic" -> false
                else -> isSlimSkin(skinBytes)
            }
            registerCharacter(account.profileId, account.username, skinBytes, capeBytes, slim)
            ensureStarted()
            val port = getPort()
            Logging.i(TAG, "Serving skin for ${account.username} (slim=$slim) on port $port")
            port
        }.onFailure { Logging.e(TAG, "Failed to start offline skin server: ${it.message}") }.getOrNull()
    }

    /**
     * Tambahkan pemain lain (HP teman di hotspot) agar skin/cape-nya terlihat di game ini.
     * Server LAN offline memberi UUID dari nama ("OfflinePlayer:nama"), sedangkan host memakai UUID akunnya sendiri,
     * jadi karakter didaftarkan di kedua UUID.
     */
    @JvmStatic
    fun addRemoteCharacter(name: String, uuid: String, slim: Boolean, skinBytes: ByteArray?, capeBytes: ByteArray?) {
        registerCharacter(uuid, name, skinBytes, capeBytes, slim)
        Logging.i(TAG, "Added remote character $name ($uuid)")
    }

    private fun offlineUuidOf(name: String): String =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8)).toString().replace("-", "")

    private fun registerCharacter(profileId: String, name: String, skinBytes: ByteArray?, capeBytes: ByteArray?, slim: Boolean) {
        val skinHash = skinBytes?.let { sha256Hex(it) }
        val capeHash = capeBytes?.let { sha256Hex(it) }
        val uuid = profileId.replace("-", "").lowercase()
        val offlineUuid = offlineUuidOf(name)
        //server LAN offline memberi UUID dari NAMA, jadi game di HP teman mencari skin dengan UUID itu
        for (id in setOf(uuid, offlineUuid.lowercase())) {
            byUuid[id] = Character(id, name, skinBytes, skinHash, capeBytes, capeHash, slim)
        }
        byName[name.lowercase()] = Character(offlineUuid, name, skinBytes, skinHash, capeBytes, capeHash, slim)
    }

    @Synchronized
    private fun ensureStarted() {
        if (serverSocket?.isClosed == false) return
        val socket = openSocket()
        serverSocket = socket
        Thread({ acceptLoop(socket) }, "OfflineSkinServer").apply { isDaemon = true }.start()
        // Membuat kunci bisa lama di HP lambat. Lakukan sekarang di latar belakang supaya permintaan pertama game tidak menunggu.
        Thread({ keyPair }, "OfflineSkinServer-key").apply { isDaemon = true }.start()
    }

    private fun openSocket(): ServerSocket {
        return try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(SHARED_PORT), 50)
            }
        } catch (e: Exception) {
            Logging.w(TAG, "Port $SHARED_PORT tidak bisa dipakai, memakai port acak (skin lintas-HP mungkin tidak muncul)", e)
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(0), 50)
            }
        }
    }

    /** Port server skin yang sedang berjalan, atau null jika belum aktif. */
    @JvmStatic
    fun getPort(): Int? = serverSocket?.takeIf { !it.isClosed }?.localPort

    /** Validates a skin image: PNG, width multiple of 64, height equal to width or half of it. */
    @JvmStatic
    fun isValidSkin(bytes: ByteArray): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        return w >= 64 && w % 64 == 0 && (h == w || h * 2 == w)
    }

    /** Validates a cape image: 64x32 (atau kelipatannya), 22x17 klasik, atau 46x22 (OptiFine). */
    @JvmStatic
    fun isValidCape(bytes: ByteArray): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (w <= 0 || h <= 0) return false
        return (w >= 64 && w % 64 == 0 && h * 2 == w) ||
            (w >= 22 && w % 22 == 0 && h * 22 == w * 17) ||
            (w >= 46 && w % 46 == 0 && h * 46 == w * 22)
    }

    /** Slim ("Alex") arms leave the columns x=54..55, y=20..31 (at 64px scale) transparent. */
    private fun isSlimSkin(bytes: ByteArray): Boolean {
        val bmp: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        return try {
            if (bmp.width != bmp.height) return false // legacy 64x32 skins are always classic
            val s = bmp.width / 64
            for (x in 54 * s until 56 * s) for (y in 20 * s until 32 * s) {
                if ((bmp.getPixel(x, y) ushr 24) != 0) return false
            }
            true
        } finally {
            bmp.recycle()
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try { socket.accept() } catch (_: Exception) { return }
            Thread({ handle(client) }, "OfflineSkinServer-conn").apply { isDaemon = true }.start()
        }
    }

    private fun handle(client: Socket) {
        client.use { c ->
            runCatching {
                c.soTimeout = 15_000
                val input = c.getInputStream()
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                var contentLength = 0
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("content-length:", ignoreCase = true)) contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                }
                val body = if (contentLength > 0) ByteArray(minOf(contentLength, MAX_BODY)).also { readFully(input, it) } else ByteArray(0)

                val rawPath = parts[1]
                val path = rawPath.substringBefore('?')
                val query = parseQuery(rawPath.substringAfter('?', ""))
                route(c, method, path, query, body)
            }.onFailure { Logging.e(TAG, "Request failed: ${it.message}") }
        }
    }

    private fun route(c: Socket, method: String, path: String, query: Map<String, String>, body: ByteArray) {
        when {
            method == "GET" && (path == "/" || path.isEmpty()) -> json(c, rootJson())
            method == "GET" && path == "/status" ->
                json(c, JSONObject().put("user.count", byUuid.size).put("token.count", 0).toString())
            method == "POST" && path == "/api/profiles/minecraft" -> {
                val names = JSONArray(String(body, Charsets.UTF_8))
                val result = JSONArray()
                for (i in 0 until names.length()) {
                    byName[names.getString(i).lowercase()]?.let {
                        result.put(JSONObject().put("id", it.uuid).put("name", it.name))
                    }
                }
                json(c, result.toString())
            }
            method == "GET" && path == "/sessionserver/session/minecraft/hasJoined" -> {
                val username = query["username"]
                if (username == null) {
                    empty(c, 204)
                } else {
                    val offlineId = offlineUuidOf(username)
                    if (!byName.containsKey(username.lowercase())) {
                        //lapisan 1: coba unduh skin pemain ini sekarang juga dari siaran temannya
                        runCatching { unknownProfileResolver?.invoke(offlineId) }
                    }
                    //lapisan 2: jika tetap belum ada, JANGAN menolak login; beri profil polos tanpa skin
                    val ch = byName[username.lowercase()] ?: Character(offlineId, username, null, null, null, null, false)
                    json(c, profileJson(ch, c))
                }
            }
            method == "POST" && path == "/sessionserver/session/minecraft/join" -> empty(c, 204)
            method == "GET" && path.startsWith("/sessionserver/session/minecraft/profile/") -> {
                val id = path.substringAfterLast('/').lowercase().replace("-", "")
                if (!byUuid.containsKey(id)) runCatching { unknownProfileResolver?.invoke(id) }
                val ch = byUuid[id]
                if (ch == null) empty(c, 204) else json(c, profileJson(ch, c))
            }
            method == "GET" && path.startsWith("/textures/") -> {
                val hash = path.substringAfterLast('/')
                var data = findTexture(hash)
                if (data == null) {
                    //lapisan 3: tekstur belum disinkronkan -> unduh dari teman lalu coba lagi
                    runCatching { unknownTextureResolver?.invoke(hash) }
                    data = findTexture(hash)
                }
                if (data == null) empty(c, 404)
                else respond(c, 200, "image/png", data, "Cache-Control: max-age=2592000, public\r\nETag: \"$hash\"\r\n")
            }
            else -> empty(c, 404)
        }
    }

    private fun findTexture(hash: String): ByteArray? {
        for (ch in byUuid.values) {
            if (ch.skinHash == hash) return ch.skin
            if (ch.capeHash == hash) return ch.cape
        }
        return null
    }

    private fun rootJson(): String {
        val pem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP) + "\n-----END PUBLIC KEY-----"
        return JSONObject()
            .put("skinDomains", JSONArray().put("127.0.0.1").put("localhost"))
            .put("meta", JSONObject()
                .put("serverName", "ZalithOfflineSkin")
                .put("implementationName", "ZalithOfflineSkin")
                .put("implementationVersion", "1.0")
                .put("feature.non_email_login", true))
            .put("signaturePublickey", pem)
            .toString()
    }

    private fun profileJson(ch: Character, c: Socket): String {
        val root = "http://localhost:${getPort() ?: c.localPort}"
        val textures = JSONObject()
        ch.skinHash?.let { hash ->
            val skin = JSONObject().put("url", "$root/textures/$hash")
            if (ch.slim) skin.put("metadata", JSONObject().put("model", "slim"))
            textures.put("SKIN", skin)
        }
        ch.capeHash?.let { hash ->
            textures.put("CAPE", JSONObject().put("url", "$root/textures/$hash"))
        }
        val payload = JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("profileId", ch.uuid)
            .put("profileName", ch.name)
            .put("textures", textures)
        val encoded = Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(keyPair.private)
            update(encoded.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(sign(), Base64.NO_WRAP)
        }
        return JSONObject()
            .put("id", ch.uuid)
            .put("name", ch.name)
            .put("properties", JSONArray().put(
                JSONObject().put("name", "textures").put("value", encoded).put("signature", signature)))
            .toString()
    }

    private fun json(c: Socket, text: String) = respond(c, 200, "application/json; charset=utf-8", text.toByteArray(Charsets.UTF_8))

    private fun empty(c: Socket, code: Int) = respond(c, code, "text/plain", ByteArray(0))

    private fun respond(c: Socket, code: Int, type: String, body: ByteArray, extra: String = "") {
        val reason = when (code) { 200 -> "OK"; 204 -> "No Content"; else -> "Not Found" }
        val head = "HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n${extra}Connection: close\r\n\r\n"
        c.getOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); write(body); flush() }
    }

    private fun parseQuery(q: String): Map<String, String> =
        if (q.isEmpty()) emptyMap() else q.split("&").mapNotNull {
            val k = it.substringBefore('=')
            if (k.isEmpty()) null else k to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }.toMap()

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (out.size() == 0) null else out.toString("ISO-8859-1")
            if (b == '\n'.code) return out.toString("ISO-8859-1").trimEnd('\r')
            if (out.size() > 8192) return null
            out.write(b)
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
    }

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}
