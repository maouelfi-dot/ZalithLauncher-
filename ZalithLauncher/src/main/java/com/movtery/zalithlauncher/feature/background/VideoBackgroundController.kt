package com.movtery.zalithlauncher.feature.background

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import com.movtery.zalithlauncher.feature.log.Logging
import java.io.File

/**
 * Plays a looping video (with its own audio) behind the launcher UI (center-crop).
 * Kept deliberately light for low-RAM devices: hardware-decoded MediaPlayer, audio on,
 * released as soon as the launcher is not visible.
 */
class VideoBackgroundController(private val textureView: TextureView) : TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var file: File? = null
    private var paused = false

    init {
        textureView.surfaceTextureListener = this
    }

    /** Set (or clear with null) the video file. */
    fun setVideo(file: File?) {
        // Same file already set/playing: do nothing (restarting the player makes the picture stutter).
        if (file != null && file == this.file && (player != null || textureView.visibility == View.VISIBLE)) {
            if (player == null && !paused && textureView.isAvailable) start()
            return
        }
        this.file = file
        release()
        if (file == null) {
            textureView.visibility = View.GONE
            return
        }
        textureView.visibility = View.VISIBLE
        if (textureView.isAvailable && !paused) start()
    }

    fun onPause() {
        paused = true
        release()
    }

    fun onResume() {
        paused = false
        if (file != null && textureView.isAvailable) start()
    }

    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val resumeRunnable = Runnable {
        runCatching { if (!paused) player?.let { if (!it.isPlaying) it.start() } }
    }

    /**
     * Jeda video sebentar saat pengguna menyentuh UI, supaya animasi/transisi tombol tidak
     * berebut CPU/GPU dengan decoder video (penyebab UI kejang di HP RAM kecil).
     */
    @Suppress("UNUSED_PARAMETER")
    fun holdForUi(ms: Long = 300L) {
        // Intentionally a no-op: pausing the video on every touch is what made it freeze/stutter.
        // The video is hardware decoded on its own thread and does not compete with the UI.
    }

    fun onDestroy() {
        uiHandler.removeCallbacks(resumeRunnable)
        file = null
        release()
    }

    private fun start() {
        val videoFile = file ?: return
        if (!videoFile.exists() || player != null) return
        val st = textureView.surfaceTexture ?: return
        runCatching {
            val s = Surface(st)
            surface = s
            player = MediaPlayer().apply {
                setDataSource(videoFile.absolutePath)
                setSurface(s)
                setVolume(1f, 1f)
                isLooping = true
                setOnVideoSizeChangedListener { _, w, h -> applyCenterCrop(w, h) }
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Logging.e("VideoBackground", "MediaPlayer error $what/$extra")
                    release()
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            Logging.e("VideoBackground", "Failed to start: ${it.message}")
            release()
        }
    }

    private fun applyCenterCrop(videoW: Int, videoH: Int) {
        val vw = textureView.width.toFloat()
        val vh = textureView.height.toFloat()
        if (videoW <= 0 || videoH <= 0 || vw <= 0f || vh <= 0f) return
        val scale = maxOf(vw / videoW, vh / videoH)
        val sx = videoW * scale / vw
        val sy = videoH * scale / vh
        textureView.setTransform(Matrix().apply { setScale(sx, sy, vw / 2f, vh / 2f) })
    }

    private fun release() {
        uiHandler.removeCallbacks(resumeRunnable)
        runCatching { player?.release() }
        player = null
        runCatching { surface?.release() }
        surface = null
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        if (!paused) start()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
        player?.let { applyCenterCrop(it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
}
