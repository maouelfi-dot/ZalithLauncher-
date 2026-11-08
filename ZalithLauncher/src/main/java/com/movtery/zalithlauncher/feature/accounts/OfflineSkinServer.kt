package com.movtery.zalithlauncher.feature.accounts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.value.MinecraftAccount
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny local Yggdrasil-compatible skin server so offline accounts can show a custom skin in game.
 * Used together with authlib-injector (`-javaagent:authlib-injector.jar=http://localhost:PORT`).
 *
 * No extra dependencies: plain ServerSocket + org.json. Only serves 127.0.0.1.
 * The design (endpoints, signed "textures" property) follows the public authlib-injector
 * server protocol, the same one HMCL / Zalith Launcher 2 use for offline skins.
 */
object OfflineSkinServer {
    private const val TAG = "OfflineSkinServer"

    private class Character(val uuid: String, val name: String, val skin: ByteArray, val hash: String, val slim: Boolean)

    private val byUuid = ConcurrentHashMap<String, Character>()
    private val byName = ConcurrentHashMap<String, Character>()
    private var serverSocket: ServerSocket? = null
    private val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    fun skinFileOf(account: MinecraftAccount): File = File(PathManager.DIR_USER_SKIN, account.uniqueUUID + ".png")

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

    /**
     * Starts the server (if needed) and registers the account's local skin.
     * @return the port, or null if the account has no custom skin / the server failed to start.
     */
    @JvmStatic
    @Synchronized
    fun prepare(account: MinecraftAccount): Int? {
        val file = skinFileOf(account)
        if (!file.isFile) return null
        return runCatching {
            val bytes = file.readBytes()
            val uuid = account.profileId.replace("-", "").lowercase()
            val slim = when (getModel(account)) {
                "slim" -> true
                "classic" -> false
                else -> isSlimSkin(bytes)
            }
            val character = Character(uuid, account.username, bytes, sha256Hex(bytes), slim)
            byUuid[uuid] = character
            byName[account.username.lowercase()] = character

            if (serverSocket == null || serverSocket!!.isClosed) {
                val socket = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
                serverSocket = socket
                Thread({ acceptLoop(socket) }, "OfflineSkinServer").apply { isDaemon = true }.start()
                // Generating a 2048-bit RSA key can take several seconds on a slow phone. Do it now, in the
                // background, so the game's first request to the server is not stuck waiting for it.
                Thread({ keyPair }, "OfflineSkinServer-key").apply { isDaemon = true }.start()
            }
            Logging.i(TAG, "Serving skin for ${account.username} (slim=${character.slim}) on port ${serverSocket!!.localPort}")
            serverSocket!!.localPort
        }.onFailure { Logging.e(TAG, "Failed to start offline skin server: ${it.message}") }.getOrNull()
    }

    /** Validates a skin image: PNG, width multiple of 64, height equal to width or half of it. */
    @JvmStatic
    fun isValidSkin(bytes: ByteArray): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        return w >= 64 && w % 64 == 0 && (h == w || h * 2 == w)
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
                c.soTimeout = 10_000
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
                val body = if (contentLength > 0) ByteArray(minOf(contentLength, 1 shl 20)).also { readFully(input, it) } else ByteArray(0)

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
                val ch = query["username"]?.lowercase()?.let { byName[it] }
                if (ch == null) empty(c, 204) else json(c, profileJson(ch, c))
            }
            method == "POST" && path == "/sessionserver/session/minecraft/join" -> empty(c, 204)
            method == "GET" && path.startsWith("/sessionserver/session/minecraft/profile/") -> {
                val ch = byUuid[path.substringAfterLast('/').lowercase()]
                if (ch == null) empty(c, 204) else json(c, profileJson(ch, c))
            }
            method == "GET" && path.startsWith("/textures/") -> {
                val hash = path.substringAfterLast('/')
                val ch = byUuid.values.firstOrNull { it.hash == hash }
                if (ch == null) empty(c, 404) else respond(c, 200, "image/png", ch.skin, "Cache-Control: max-age=2592000, public\r\n")
            }
            else -> empty(c, 404)
        }
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
        val root = "http://localhost:${c.localPort}"
        val skin = JSONObject().put("url", "$root/textures/${ch.hash}")
        if (ch.slim) skin.put("metadata", JSONObject().put("model", "slim"))
        val textures = JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("profileId", ch.uuid)
            .put("profileName", ch.name)
            .put("textures", JSONObject().put("SKIN", skin))
        val encoded = Base64.encodeToString(textures.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
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
