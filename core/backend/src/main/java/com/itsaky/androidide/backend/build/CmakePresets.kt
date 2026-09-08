package com.itsaky.androidide.backend.build

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CmakePresets {

    const val ProjectFileName = "CMakePresets.json"
    const val UserFileName = "CMakeUserPresets.json"

    private const val SchemaVersion = 6

    fun projectFile(projectDir: File): File = File(projectDir, ProjectFileName)

    fun userFile(projectDir: File): File = File(projectDir, UserFileName)

    fun hasAny(projectDir: File): Boolean =
        projectFile(projectDir).isFile || userFile(projectDir).isFile

    fun bootstrap(
        projectDir: File,
        abis: List<String>,
        apiLevel: Int,
        buildType: String,
        ninjaPath: String
    ) {
        ensurePresets(projectDir, abis, apiLevel, buildType, ninjaPath)
    }

    fun ensurePresets(
        projectDir: File,
        abis: List<String>,
        apiLevel: Int,
        buildType: String,
        ninjaPath: String
    ) {
        val file = projectFile(projectDir)
        val root = if (file.isFile) JSONObject(file.readText()) else JSONObject().put("version", SchemaVersion)
        val configurePresets = root.optJSONArray("configurePresets") ?: JSONArray()
        val buildPresets = root.optJSONArray("buildPresets") ?: JSONArray()
        val configured = (0 until configurePresets.length()).mapNotNull { index ->
            configurePresets.optJSONObject(index)?.optString("name")
        }.toSet()
        val builds = (0 until buildPresets.length()).mapNotNull { index ->
            buildPresets.optJSONObject(index)?.optString("name")
        }.toSet()
        for (abi in abis) {
            val name = presetName(abi, buildType)
            if (name !in configured) {
                configurePresets.put(
                    JSONObject()
                        .put("name", name)
                        .put("generator", "Ninja")
                        .put("binaryDir", "\${sourceDir}/build/$name")
                        .put("toolchainFile", "\$env{ANDROID_NDK_ROOT}/build/cmake/android.toolchain.cmake")
                        .put("cacheVariables", JSONObject()
                            .put("ANDROID_ABI", abi)
                            .put("ANDROID_PLATFORM", "android-$apiLevel")
                            .put("CMAKE_BUILD_TYPE", buildType)
                            .put("CMAKE_EXPORT_COMPILE_COMMANDS", "ON")
                            .put("CMAKE_MAKE_PROGRAM", ninjaPath))
                )
            }
            if (name !in builds) {
                buildPresets.put(JSONObject().put("name", name).put("configurePreset", name))
            }
        }
        root.put("configurePresets", configurePresets)
        root.put("buildPresets", buildPresets)
        file.writeText(root.toString(2) + "\n")
        ensureGitIgnore(projectDir)
    }

    fun presetName(abi: String, buildType: String): String =
        "android-${abi}-${buildType.lowercase()}"

    fun defaultPresetName(buildType: String): String = "android-${buildType.lowercase()}"

    private fun ensureGitIgnore(projectDir: File) {
        val gitignore = File(projectDir, ".gitignore")
        val existing = if (gitignore.isFile) gitignore.readText() else ""
        if (existing.lineSequence().any { it.trim() == UserFileName }) return
        val prefix = if (existing.isEmpty() || existing.endsWith("\n")) "" else "\n"
        runCatching { gitignore.appendText("$prefix$UserFileName\n") }
    }

    fun parseListOutput(output: String): List<String> = output.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("\"") }
        .mapNotNull { line ->
            val start = line.indexOf('"')
            val end = line.indexOf('"', start + 1)
            if (start < 0 || end <= start) null else line.substring(start + 1, end)
        }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    fun binaryDirOf(projectDir: File, presetName: String): File =
        File(projectDir, "build/$presetName")
}
