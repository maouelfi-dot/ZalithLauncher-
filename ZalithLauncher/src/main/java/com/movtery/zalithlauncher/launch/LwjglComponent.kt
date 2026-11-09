package com.movtery.zalithlauncher.launch

import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.JMinecraftVersionList
import java.io.File

/**
 * Chooses which bundled LWJGL 3 component a game version runs on.
 *
 *  - "lwjgl3"      : LWJGL 3.3.x component (default, natives come from the launcher's jniLibs)
 *  - "lwjgl3_341"  : LWJGL 3.4.1 component (jars + per-ABI natives unpacked to app-private storage),
 *                    used for game versions whose manifest asks for LWJGL >= 3.4.1
 */
object LwjglComponent {
    const val DIR_DEFAULT = "lwjgl3"
    const val DIR_341 = "lwjgl3_341"
    private const val VERSION_341 = 341

    /** ABI folder name inside `natives/` of the 3.4.1 component for this device. */
    @JvmStatic
    fun abiDirName(): String = when (Architecture.getDeviceArchitecture()) {
        Architecture.ARCH_ARM64 -> "arm64-v8a"
        Architecture.ARCH_ARM -> "armeabi-v7a"
        Architecture.ARCH_X86_64 -> "x86_64"
        Architecture.ARCH_X86 -> "x86"
        else -> "arm64-v8a"
    }

    /**
     * Reads `org.lwjgl:lwjgl:X.Y.Z` from the version manifest and returns it as an int (3.4.1 -> 341).
     * @return 0 if it can't be determined
     */
    @JvmStatic
    fun detectLwjglVersion(info: JMinecraftVersionList.Version?): Int {
        val libs = info?.libraries ?: return 0
        for (lib in libs) {
            val name = lib?.name ?: continue
            if (!name.startsWith("org.lwjgl:lwjgl:")) continue
            val v = name.removePrefix("org.lwjgl:lwjgl:")
                .takeWhile { it.isDigit() || it == '.' }
                .filter { it != '.' }
                .toIntOrNull()
            if (v != null && v in 300..999) return v
        }
        return 0
    }

    private fun componentDir(dirName: String) =
        // 3.4.1 holds native .so files -> app-private dir (see Components.LWJGL3_341); 3.3.x jars stay in the game home
        if (dirName == DIR_341) File(PathManager.DIR_DATA, dirName) else File(PathManager.DIR_GAME_HOME, dirName)

    private fun nativesDir() = File(componentDir(DIR_341), "natives/${abiDirName()}")

    /** True when the game wants LWJGL >= 3.4.1 *and* the 3.4.1 component is really unpacked on this device. */
    @JvmStatic
    fun shouldUse341(info: JMinecraftVersionList.Version?): Boolean {
        val forced = runCatching { com.movtery.zalithlauncher.setting.AllSettings.forceLwjgl341.getValue() }.getOrDefault(false)
        if (!forced && detectLwjglVersion(info) < VERSION_341) return false
        val dir = componentDir(DIR_341)
        val hasJars = dir.listFiles { f -> f.name.endsWith(".jar") }?.isNotEmpty() == true
        return hasJars && File(nativesDir(), "liblwjgl.so").exists()
    }

    /** `-cp` part for the chosen component. Core `lwjgl.jar` first, then merged modules, then the rest. */
    @JvmStatic
    fun classPath(use341: Boolean): String {
        val dir = componentDir(if (use341) DIR_341 else DIR_DEFAULT)
        val jars = dir.listFiles { f -> f.name.endsWith(".jar") }?.toList().orEmpty()
        return jars.sortedBy {
            when {
                it.name == "lwjgl.jar" -> 0
                it.name.contains("merged-modules") -> 1
                else -> 2
            }
        }.joinToString(":") { it.absolutePath }
    }

    /** JVM args that make LWJGL load the 3.4.1 natives instead of the 3.3.x ones in nativeLibraryDir. */
    @JvmStatic
    fun nativeArgs(): List<String> {
        val dir = nativesDir().absolutePath
        return listOf(
            "-Dorg.lwjgl.librarypath=$dir",
            "-Dorg.lwjgl.freetype.libname=$dir/libfreetype.so"
        )
    }
}
