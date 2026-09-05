package com.itsaky.androidide.backend.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RunConfigurations(
    private val projectDir: File,
    private val runner: BuildRunner
) {

    fun hasPresets(): Boolean = CmakePresets.hasAny(projectDir)

    fun bootstrap() {
        val abis = runner.abis
        CmakePresets.bootstrap(
            projectDir = projectDir,
            abis = abis,
            apiLevel = runner.apiLevel,
            buildType = runner.buildType,
            ninjaPath = ToolchainPaths.guestNinja()
        )
        val primary = abis.firstOrNull()?.let { CmakePresets.presetName(it, runner.buildType) }
            ?: CmakePresets.defaultPresetName(runner.buildType)
        writeClangdDatabase(primary)
    }

    suspend fun activePresets(): List<String> = withContext(Dispatchers.IO) {
        val fromCmake = runner.listPresets(projectDir, "build")
        val presets = if (fromCmake.isNotEmpty()) {
            fromCmake
        } else {
            runner.abis.map { CmakePresets.presetName(it, runner.buildType) }
        }
        presets.firstOrNull()?.let { writeClangdDatabase(it) }
        presets
    }

    suspend fun activePreset(): String? = withContext(Dispatchers.IO) {
        activePresets().firstOrNull()
    }

    private fun writeClangdDatabase(presetName: String) {
        val binaryDir = "build/$presetName"
        val file = File(projectDir, ".clangd")
        val body = "CompileFlags:\n  CompilationDatabase: $binaryDir\n"
        runCatching {
            if (!file.isFile) {
                file.writeText(body)
                return@runCatching
            }
            val existing = file.readText()
            if (!existing.contains("CompilationDatabase:")) {
                file.writeText(body + existing)
            } else {
                file.writeText(
                    existing.replace(
                        Regex("CompilationDatabase:.*"),
                        "CompilationDatabase: $binaryDir"
                    )
                )
            }
        }
    }
}
