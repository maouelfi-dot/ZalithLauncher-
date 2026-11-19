package com.movtery.zalithlauncher.feature.accounts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.File

/** Merender tampak depan skin Minecraft (64x64 / 64x32) menjadi bitmap preview. */
object SkinPreview {
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }

    @JvmStatic
    @JvmOverloads
    fun renderFront(file: File, slim: Boolean, scale: Int = 8, back: Boolean = false, capeFile: File? = null): Bitmap? {
        if (!file.isFile) return null
        val skin = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: return null
        if (skin.width != 64 || (skin.height != 64 && skin.height != 32)) return null
        val legacy = skin.height == 32
        val armW = if (slim) 3 else 4
        val out = Bitmap.createBitmap(16 * scale, 32 * scale, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)

        fun part(sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
            c.drawBitmap(skin, Rect(sx, sy, sx + w, sy + h),
                Rect(dx * scale, dy * scale, (dx + w) * scale, (dy + h) * scale), paint)
        }
        // Tampak belakang: x sisi belakang = asal bagian + 8 + lebar (lengan) / +12 (kepala, badan, kaki, 8 lebar)
        val hx = if (back) 24 else 8        // kepala
        val bx = if (back) 32 else 20       // badan / jaket
        val rax = if (back) 40 + 8 + armW else 44   // lengan kanan
        val lax = if (back) 32 + 8 + armW else 36   // lengan kiri
        val rlx = if (back) 12 else 4       // kaki kanan
        val llx = if (back) 28 else 20      // kaki kiri

        // lapisan dalam
        part(hx, 8, 8, 8, 4, 0)
        part(bx, 20, 8, 12, 4, 8)
        part(rax, 20, armW, 12, if (back) 12 else 4 - armW, 8)
        part(rlx, 20, 4, 12, if (back) 8 else 4, 20)
        if (legacy) {
            part(rax, 20, armW, 12, if (back) 4 - armW else 12, 8)
            part(rlx, 20, 4, 12, if (back) 4 else 8, 20)
        } else {
            part(lax, 52, armW, 12, if (back) 4 - armW else 12, 8)
            part(llx, 52, 4, 12, if (back) 4 else 8, 20)
        }
        // lapisan luar (overlay)
        part(if (back) 56 else 40, 8, 8, 8, 4, 0)
        if (!legacy) {
            part(if (back) 32 else 20, 36, 8, 12, 4, 8)
            part(if (back) 40 + 8 + armW else 44, 36, armW, 12, if (back) 12 else 4 - armW, 8)
            part(if (back) 48 + 8 + armW else 52, 52, armW, 12, if (back) 4 - armW else 12, 8)
            part(if (back) 12 else 4, 36, 4, 12, if (back) 8 else 4, 20)
            part(if (back) 12 else 4, 52, 4, 12, if (back) 4 else 8, 20)
        }
        // Cape terlihat di tampak belakang: sisi luar cape ada di (1,1)-(11,17) pada skala dasarnya
        if (back && capeFile != null && capeFile.isFile) {
            val cape = runCatching { BitmapFactory.decodeFile(capeFile.absolutePath) }.getOrNull()
            if (cape != null) {
                val w = cape.width
                val h = cape.height
                val s = when {
                    h * 2 == w && w % 64 == 0 -> w / 64
                    h * 22 == w * 17 && w % 22 == 0 -> w / 22
                    h * 46 == w * 22 && w % 46 == 0 -> w / 46
                    else -> 0
                }
                if (s > 0) {
                    c.drawBitmap(cape, Rect(s, s, 11 * s, 17 * s),
                        Rect(3 * scale, 8 * scale, 13 * scale, 24 * scale), paint)
                }
                cape.recycle()
            }
        }
        skin.recycle()
        return out
    }
}
