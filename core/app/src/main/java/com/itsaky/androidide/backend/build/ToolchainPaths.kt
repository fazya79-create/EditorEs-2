package com.itsaky.androidide.backend.build

import android.content.Context
import com.itsaky.androidide.backend.proot.ProotConfig
import java.io.File

enum class ToolchainKind {
    Ndk,
    CMake
}

data class ToolchainRelease(
    val tag: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long
) {
    val sizeMb: Double get() = sizeBytes / (1024.0 * 1024.0)
}

object ToolchainPaths {

    const val NdkReleasesApi =
        "https://api.github.com/repos/HomuHomu833/android-ndk-custom/releases?per_page=20"
    const val CMakeReleasesApi =
        "https://api.github.com/repos/HomuHomu833/cmake-custom/releases?per_page=20"

    const val AssetSuffix = "-aarch64-linux-gnu.tar.xz"

    private const val OptDirName = "opt"
    private const val NdkDirName = "ndk"
    private const val CMakeDirName = "cmake"
    private const val MarkerName = ".version"

    fun toolchainRoot(context: Context): File =
        File(context.filesDir, "toolchains").apply { mkdirs() }

    private fun legacyOptDir(context: Context): File =
        File(context.filesDir, "${ProotConfig.RootfsName}/$OptDirName")

    fun migrateLegacyLayout(context: Context) {
        val legacy = legacyOptDir(context)
        if (!legacy.isDirectory) return
        val root = toolchainRoot(context)
        for (name in listOf(NdkDirName, CMakeDirName)) {
            val from = File(legacy, name)
            val to = File(root, name)
            if (!from.isDirectory || to.isDirectory) continue
            if (!from.renameTo(to)) {
                runCatching { from.copyRecursively(to, overwrite = true) }
                    .onSuccess { from.deleteRecursively() }
            }
        }
    }

    fun hostDir(context: Context, kind: ToolchainKind): File = when (kind) {
        ToolchainKind.Ndk -> File(toolchainRoot(context), NdkDirName)
        ToolchainKind.CMake -> File(toolchainRoot(context), CMakeDirName)
    }

    fun guestDir(kind: ToolchainKind): String = when (kind) {
        ToolchainKind.Ndk -> "/$OptDirName/$NdkDirName"
        ToolchainKind.CMake -> "/$OptDirName/$CMakeDirName"
    }

    fun guestOptDir(): String = "/$OptDirName"

    fun markerFile(context: Context, kind: ToolchainKind): File =
        File(hostDir(context, kind), MarkerName)

    fun installedVersion(context: Context, kind: ToolchainKind): String? {
        val marker = markerFile(context, kind)
        if (!marker.isFile) return null
        return runCatching { marker.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun isInstalled(context: Context, kind: ToolchainKind): Boolean {
        if (installedVersion(context, kind) == null) return false
        return when (kind) {
            ToolchainKind.Ndk -> ndkToolchainFile(context).isFile
            ToolchainKind.CMake -> cmakeBinary(context).isFile
        }
    }

    fun ndkToolchainFile(context: Context): File =
        File(hostDir(context, ToolchainKind.Ndk), "build/cmake/android.toolchain.cmake")

    fun cmakeBinary(context: Context): File =
        File(hostDir(context, ToolchainKind.CMake), "bin/cmake")

    fun clangdBinary(context: Context): File =
        File(
            hostDir(context, ToolchainKind.Ndk),
            "toolchains/llvm/prebuilt/linux-arm64/bin/clangd"
        )

    fun guestClangd(): String =
        "${guestDir(ToolchainKind.Ndk)}/toolchains/llvm/prebuilt/linux-arm64/bin/clangd"

    fun guestNdkBin(): String =
        "${guestDir(ToolchainKind.Ndk)}/toolchains/llvm/prebuilt/linux-arm64/bin"

    fun guestNdkToolchainFile(): String =
        "${guestDir(ToolchainKind.Ndk)}/build/cmake/android.toolchain.cmake"

    fun guestCMakeBin(): String = "${guestDir(ToolchainKind.CMake)}/bin"

    fun guestCMake(): String = "${guestDir(ToolchainKind.CMake)}/bin/cmake"

    fun guestNinja(): String = "${guestDir(ToolchainKind.CMake)}/bin/ninja"

    fun downloadCache(context: Context, kind: ToolchainKind): File =
        File(context.cacheDir, if (kind == ToolchainKind.Ndk) "ndk.tar.xz" else "cmake.tar.xz")
}
