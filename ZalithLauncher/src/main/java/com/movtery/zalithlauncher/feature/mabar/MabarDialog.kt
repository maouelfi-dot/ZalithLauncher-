package com.movtery.zalithlauncher.feature.mabar

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.movtery.zalithlauncher.R

/** Dialog status Mabar di dalam game (Mabar jalan di proses game, jadi statusnya hanya bisa dilihat dari sini). */
object MabarDialog {
    private const val REFRESH_MS = 1000L

    @JvmStatic
    fun show(activity: Activity) {
        val dp = activity.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val text = TextView(activity).apply { setPadding(px(4), px(4), px(4), px(8)) }
        val copy = Button(activity).apply { setText(R.string.mabar_copy_ip) }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(12), px(24), px(4))
            addView(text)
            addView(copy)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.mabar_title)
            .setView(ScrollView(activity).apply { addView(content) })
            .setPositiveButton(R.string.mabar_close, null)
            .create()

        val handler = Handler(Looper.getMainLooper())
        val refresh = object : Runnable {
            override fun run() {
                val state = MabarLan.state.value
                text.text = render(activity, state)
                copy.isEnabled = state.bestIp != null
                handler.postDelayed(this, REFRESH_MS)
            }
        }
        copy.setOnClickListener {
            val ip = MabarLan.state.value.bestIp ?: return@setOnClickListener
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("ip", ip))
            Toast.makeText(activity, activity.getString(R.string.mabar_copied, ip), Toast.LENGTH_SHORT).show()
        }
        dialog.setOnDismissListener { handler.removeCallbacks(refresh) }
        dialog.show()
        refresh.run()
    }

    private fun render(ctx: Context, s: MabarLan.State): String {
        if (!s.running) return ctx.getString(R.string.mabar_status_off)
        val lines = ArrayList<String>()
        lines += s.bestIp?.let { ctx.getString(R.string.mabar_your_ip, it) } ?: ctx.getString(R.string.mabar_no_network)
        val own = s.ownLanPort
        val host = s.host
        when {
            own != null -> lines += ctx.getString(R.string.mabar_status_hosting, own)
            host != null -> {
                lines += ctx.getString(R.string.mabar_status_found, host.ip, host.port)
                if (s.pingMs >= 0) lines += ctx.getString(R.string.mabar_ping, s.pingMs)
            }
            else -> lines += ctx.getString(R.string.mabar_status_searching)
        }
        if (s.activeLinks > 0) lines += ctx.getString(R.string.mabar_status_links, s.activeLinks)
        if (s.syncedPlayers.isNotEmpty()) lines += ctx.getString(R.string.mabar_status_skins, s.syncedPlayers.joinToString(", "))
        return lines.joinToString("\n\n")
    }
}
