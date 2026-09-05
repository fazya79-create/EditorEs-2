package com.itsaky.androidide.backend.build

import android.content.Context
import com.itsaky.androidide.backend.proot.ProotConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

sealed interface BuildEvent {
    data class Line(val text: String) : BuildEvent
    data class Finished(val exitCode: Int) : BuildEvent
    data class Failed(val message: String) : BuildEvent
}

sealed interface BuildRequest {
    data class Build(val preset: String) : BuildRequest
    data class CleanBuild(val preset: String) : BuildRequest
}

class BuildRunner(
    private val context: Context,
    val abis: List<String> = listOf("arm64-v8a"),
    val apiLevel: Int = 24,
    val buildType: String = "Release"
) {

    @Volatile
    private var process: Process? = null

    val isRunning: Boolean get() = process?.isAlive == true

    fun stop() {
        process?.destroy()
        process = null
    }

    suspend fun listPresets(projectDir: File, type: String): List<String> =
        withContext(Dispatchers.IO) {
            if (!ready()) return@withContext emptyList()
            val cmake = ToolchainPaths.guestCMake()
            val output = capture(projectDir, "$cmake --list-presets=$type 2>&1 || true")
            CmakePresets.parseListOutput(output)
        }

    suspend fun run(
        projectDir: File,
        request: BuildRequest,
        onEvent: (BuildEvent) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        try {
            val failure = preflight(projectDir)
            if (failure != null) {
                onEvent(BuildEvent.Failed(failure))
                return@withContext
            }

            val script = when (request) {
                is BuildRequest.Build ->
                    buildScript(projectDir, request.preset, false, onEvent)
                is BuildRequest.CleanBuild -> {
                    val binaryDir = CmakePresets.binaryDirOf(projectDir, request.preset)
                    if (binaryDir.isDirectory) {
                        onEvent(BuildEvent.Line("> removing build/${binaryDir.name}"))
                        binaryDir.deleteRecursively()
                    }
                    buildScript(projectDir, request.preset, true, onEvent)
                }
            }

            execute(projectDir, script, onEvent)
        } catch (e: Exception) {
            process = null
            onEvent(BuildEvent.Failed(e.message ?: "Build failed"))
        }
    }

    private fun buildScript(
        projectDir: File,
        preset: String,
        cleanFirst: Boolean,
        onEvent: (BuildEvent) -> Unit
    ): String {
        val cmake = ToolchainPaths.guestCMake()
        val binaryDir = CmakePresets.binaryDirOf(projectDir, preset)
        val staleReason = staleCacheReason(binaryDir, projectDir)
        if (staleReason != null) {
            onEvent(BuildEvent.Line("> stale cache: $staleReason"))
            binaryDir.deleteRecursively()
        }
        val steps = mutableListOf("set -e")
        if (!File(binaryDir, "CMakeCache.txt").isFile) {
            onEvent(BuildEvent.Line("> configure $preset"))
            steps += configureCommand(preset)
        }
        onEvent(BuildEvent.Line("> build $preset"))
        val clean = if (cleanFirst) " --clean-first" else ""
        steps += "$cmake --build --preset $preset$clean"
        return steps.joinToString(" && ")
    }

    private fun configureCommand(configurePreset: String): String =
        "${ToolchainPaths.guestCMake()} --preset $configurePreset" +
            " -DCMAKE_MAKE_PROGRAM=${ToolchainPaths.guestNinja()}"

    private fun staleCacheReason(binaryDir: File, projectDir: File): String? {
        val cache = File(binaryDir, "CMakeCache.txt")
        if (!cache.isFile) return null
        val home = runCatching {
            cache.useLines { lines ->
                lines.firstOrNull { it.startsWith("CMAKE_HOME_DIRECTORY:") }
                    ?.substringAfter('=')
                    ?.trim()
            }
        }.getOrNull() ?: return null
        if (home.isEmpty()) return null
        val expected = projectDir.absolutePath.trimEnd('/')
        if (home.trimEnd('/') == expected) return null
        return "cache points at $home"
    }

    private fun preflight(projectDir: File): String? {
        if (!ProotConfig.isInstalled(context)) return "Ubuntu environment is not installed"
        if (!ToolchainPaths.isInstalled(context, ToolchainKind.Ndk)) {
            return "Android NDK is not installed"
        }
        if (!ToolchainPaths.isInstalled(context, ToolchainKind.CMake)) {
            return "CMake is not installed"
        }
        if (!File(projectDir, "CMakeLists.txt").isFile) {
            return "CMakeLists.txt not found in ${projectDir.name}"
        }
        val cmakeHost = ToolchainPaths.cmakeBinary(context)
        val ninjaHost = File(cmakeHost.parentFile, "ninja")
        cmakeHost.setExecutable(true, false)
        ninjaHost.setExecutable(true, false)
        if (!cmakeHost.canExecute()) {
            return "cmake is not executable at ${cmakeHost.absolutePath}"
        }
        if (!ninjaHost.isFile) {
            return "ninja is missing at ${ninjaHost.absolutePath}, reinstall CMake"
        }
        if (!ToolchainPaths.ndkToolchainFile(context).isFile) {
            return "NDK toolchain file is missing, reinstall the NDK"
        }
        return null
    }

    private fun ready(): Boolean =
        ProotConfig.isInstalled(context) &&
            ToolchainPaths.isInstalled(context, ToolchainKind.Ndk) &&
            ToolchainPaths.isInstalled(context, ToolchainKind.CMake)

    private fun prootArgsFor(projectDir: File, script: String): List<String> {
        val guestProject = projectDir.absolutePath
        ProotConfig.prepareStorageMounts(context)
        runCatching {
            File(ProotConfig.rootfsDir(context), guestProject.trimStart('/')).mkdirs()
        }
        return ProotConfig.commandArgs(
            context = context,
            script = script,
            guestCwd = guestProject,
            binds = listOf("$guestProject:$guestProject"),
            extraPath = listOf(ToolchainPaths.guestCMakeBin(), ToolchainPaths.guestNdkBin())
        )
    }

    private fun capture(projectDir: File, script: String): String {
        val builder = ProcessBuilder(prootArgsFor(projectDir, script))
        builder.redirectErrorStream(true)
        builder.environment().putAll(ProotConfig.prootEnvMap(context))
        val started = builder.start()
        val output = started.inputStream.bufferedReader().use { it.readText() }
        started.waitFor()
        return output
    }

    private fun execute(projectDir: File, script: String, onEvent: (BuildEvent) -> Unit) {
        val builder = ProcessBuilder(prootArgsFor(projectDir, script))
        builder.redirectErrorStream(true)
        builder.environment().putAll(ProotConfig.prootEnvMap(context))
        val started = builder.start()
        process = started
        BufferedReader(InputStreamReader(started.inputStream)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                onEvent(BuildEvent.Line(line))
            }
        }
        val exit = started.waitFor()
        process = null
        onEvent(BuildEvent.Finished(exit))
    }
}
