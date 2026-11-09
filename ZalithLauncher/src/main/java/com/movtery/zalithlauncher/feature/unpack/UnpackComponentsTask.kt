package com.movtery.zalithlauncher.feature.unpack

import android.content.Context
import android.content.res.AssetManager
import com.movtery.zalithlauncher.feature.log.Logging.i
import com.movtery.zalithlauncher.launch.LwjglComponent
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class UnpackComponentsTask(val context: Context, val component: Components) : AbstractUnpackTask() {
    private lateinit var am: AssetManager
    private lateinit var rootDir: String
    private lateinit var versionFile: File
    private lateinit var input: InputStream
    private var isCheckFailed: Boolean = false

    init {
        runCatching {
            am = context.assets
            rootDir = if (component.privateDirectory) PathManager.DIR_DATA else PathManager.DIR_GAME_HOME
            versionFile = File("$rootDir/${component.component}/version")
            input = am.open("components/${component.component}/version")
        }.getOrElse {
            isCheckFailed = true
        }
    }

    fun isCheckFailed() = isCheckFailed

    override fun isNeedUnpack(): Boolean {
        if (isCheckFailed) return false

        if (!versionFile.exists()) {
            requestEmptyParentDir(versionFile)
            i("Unpack Components", "${component.component}: Pack was installed manually, or does not exist...")
            return true
        } else {
            val fis = FileInputStream(versionFile)
            val release1 = Tools.read(input)
            val release2 = Tools.read(fis)
            if (release1 != release2) {
                requestEmptyParentDir(versionFile)
                return true
            } else {
                i("UnpackPrep", "${component.component}: Pack is up-to-date with the launcher, continuing...")
                return false
            }
        }
    }

    override fun run() {
        listener?.onTaskStart()
        copyAssetDir("components/${component.component}", "$rootDir/${component.component}")
        listener?.onTaskEnd()
    }

    /**
     * Copy an asset directory recursively. Sub-folders of a "natives" folder are ABI folders
     * (arm64-v8a, armeabi-v7a, ...): only the one matching this device is unpacked to save space/time.
     */
    private fun copyAssetDir(assetPath: String, outputDir: String) {
        val children = am.list(assetPath) ?: return
        for (name in children) {
            val childAsset = "$assetPath/$name"
            val grandChildren = am.list(childAsset)
            if (!grandChildren.isNullOrEmpty()) {
                if (assetPath.endsWith("/natives") && name != LwjglComponent.abiDirName()) continue
                copyAssetDir(childAsset, "$outputDir/$name")
            } else {
                Tools.copyAssetFile(context, childAsset, outputDir, true)
            }
        }
    }

    private fun requestEmptyParentDir(file: File) {
        file.parentFile!!.apply {
            if (exists() and isDirectory) {
                FileUtils.deleteDirectory(this)
            }
            mkdirs()
        }
    }
}