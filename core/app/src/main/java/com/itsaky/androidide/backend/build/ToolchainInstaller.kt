package com.itsaky.androidide.backend.build

import android.content.Context
import com.itsaky.androidide.backend.net.DownloadCancelledException
import com.itsaky.androidide.backend.net.ResumableDownload
import com.itsaky.androidide.backend.proot.deleteRecursivelySafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

sealed class ToolchainPhase {
    data object Idle : ToolchainPhase()
    data class Downloading(val percent: Int, val receivedMb: Double, val totalMb: Double) : ToolchainPhase()
    data class Retrying(val attempt: Int, val reason: String, val receivedMb: Double) : ToolchainPhase()
    data class Extracting(val entry: String, val count: Int) : ToolchainPhase()
    data object Done : ToolchainPhase()
    data class Failed(val message: String) : ToolchainPhase()
    data object Cancelled : ToolchainPhase()
}

class ToolchainInstaller(private val context: Context, private val kind: ToolchainKind) {

    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    suspend fun install(
        release: ToolchainRelease,
        onProgress: (ToolchainPhase) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val archive = ToolchainPaths.downloadCache(context, kind)
        try {
            download(release, archive, onProgress)
            extract(archive, release, onProgress)
            archive.delete()
            onProgress(ToolchainPhase.Done)
        } catch (e: DownloadCancelledException) {
            onProgress(ToolchainPhase.Cancelled)
        } catch (e: OutOfMemoryError) {
            archive.delete()
            ToolchainPaths.hostDir(context, kind).deleteRecursivelySafe()
            onProgress(ToolchainPhase.Failed("Not enough memory to extract on this device. Free up RAM and retry."))
        } catch (e: Exception) {
            archive.delete()
            ToolchainPaths.hostDir(context, kind).deleteRecursivelySafe()
            onProgress(ToolchainPhase.Failed(e.message ?: "Installation failed"))
        }
    }

    private fun download(
        release: ToolchainRelease,
        target: File,
        onProgress: (ToolchainPhase) -> Unit
    ) {
        var lastPercent = -1
        ResumableDownload.fetch(
            url = release.downloadUrl,
            target = target,
            fallbackTotal = release.sizeBytes,
            cancelled = cancelled,
            onProgress = { received, total ->
                if (total > 0) {
                    val percent = ((received * 100) / total).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(
                            ToolchainPhase.Downloading(
                                percent = percent,
                                receivedMb = received / (1024.0 * 1024.0),
                                totalMb = total / (1024.0 * 1024.0)
                            )
                        )
                    }
                }
            },
            onRetry = { attempt, reason ->
                val received = ResumableDownload.partFile(target).length() / (1024.0 * 1024.0)
                onProgress(ToolchainPhase.Retrying(attempt, reason, received))
            }
        )
    }

    private fun extract(
        archive: File,
        release: ToolchainRelease,
        onProgress: (ToolchainPhase) -> Unit
    ) {
        val freeMemory = Runtime.getRuntime().freeMemory()
        val totalMemory = Runtime.getRuntime().totalMemory()
        val maxMemory = Runtime.getRuntime().maxMemory()
        val usedMemory = totalMemory - freeMemory
        val availableForAllocation = maxMemory - usedMemory

        val minMemoryRequired = 150L * 1024L * 1024L
        if (availableForAllocation < minMemoryRequired) {
            throw OutOfMemoryError("Not enough memory to extract. Close other apps and retry.")
        }

        val root = ToolchainPaths.hostDir(context, kind)
        root.deleteRecursivelySafe()
        root.mkdirs()
        val pendingLinks = mutableListOf<Pair<File, String>>()
        var count = 0

        val xzStream = try {
            XZInputStream(BufferedInputStream(archive.inputStream(), 1024 * 1024))
        } catch (e: Exception) {
            throw IllegalStateException("Failed to open XZ archive: ${e.message}", e)
        }

        TarArchiveInputStream(xzStream).use { tar ->
            while (true) {
                if (cancelled.get()) throw DownloadCancelledException()
                val entry = tar.nextEntry ?: break
                val relative = stripTopLevel(entry.name) ?: continue
                if (!ToolchainPruner.keep(kind, relative)) continue
                val target = File(root, relative)
                if (!target.canonicalPath.startsWith(root.canonicalPath)) continue
                when {
                    entry.isDirectory -> target.mkdirs()
                    entry.isSymbolicLink -> {
                        target.parentFile?.mkdirs()
                        target.delete()
                        runCatching {
                            java.nio.file.Files.createSymbolicLink(
                                target.toPath(),
                                java.nio.file.Paths.get(entry.linkName)
                            )
                        }
                    }
                    entry.isLink -> {
                        target.parentFile?.mkdirs()
                        pendingLinks += target to (stripTopLevel(entry.linkName) ?: continue)
                    }
                    else -> {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val read = tar.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                            }
                        }
                        applyMode(target, entry)
                    }
                }
                count++
                onProgress(ToolchainPhase.Extracting(relative, count))
            }
        }
        for ((target, linkName) in pendingLinks) {
            val source = File(root, linkName)
            if (!source.exists()) continue
            target.delete()
            if (!runCatching {
                    java.nio.file.Files.createLink(target.toPath(), source.toPath())
                }.isSuccess
            ) {
                runCatching { source.copyTo(target, overwrite = true) }
            }
        }
        verify(root)
        ToolchainPaths.markerFile(context, kind).writeText(release.tag)
    }

    private fun verify(root: File) {
        when (kind) {
            ToolchainKind.Ndk -> {
                val toolchain = File(root, "build/cmake/android.toolchain.cmake")
                if (!toolchain.isFile) throw IllegalStateException("NDK is missing android.toolchain.cmake")
                val clang = File(root, "toolchains/llvm/prebuilt/linux-arm64/bin/clang")
                if (!clang.exists()) throw IllegalStateException("NDK is missing clang")
            }
            ToolchainKind.CMake -> {
                val cmake = File(root, "bin/cmake")
                if (!cmake.isFile) throw IllegalStateException("CMake binary is missing")
                val ninja = File(root, "bin/ninja")
                if (!ninja.isFile) throw IllegalStateException("Ninja binary is missing")
            }
        }
        markExecutables(root)
    }

    private fun markExecutables(root: File) {
        val binDirs = when (kind) {
            ToolchainKind.Ndk -> listOf(
                File(root, "toolchains/llvm/prebuilt/linux-arm64/bin"),
                File(root, "prebuilt/linux-arm64/bin"),
                File(root, "toolchains/llvm/prebuilt/linux-arm64/lib/clang/21/bin")
            )
            ToolchainKind.CMake -> listOf(File(root, "bin"))
        }
        for (dir in binDirs) {
            val children = dir.listFiles() ?: continue
            for (file in children) {
                if (file.isFile) file.setExecutable(true, false)
            }
        }
    }

    private fun stripTopLevel(name: String): String? {
        var cleaned = name.replace('\\', '/')
        while (cleaned.startsWith("./")) cleaned = cleaned.substring(2)
        cleaned = cleaned.trimStart('/').trimEnd('/')
        if (cleaned.isBlank()) return null
        if (cleaned.split('/').any { it == ".." }) return null
        val slash = cleaned.indexOf('/')
        if (slash < 0) return null
        return cleaned.substring(slash + 1).takeIf { it.isNotBlank() }
    }

    private fun applyMode(target: File, entry: TarArchiveEntry) {
        val mode = entry.mode
        target.setReadable(true, false)
        if (mode and 128 != 0) target.setWritable(true, false)
        if (mode and 64 != 0) target.setExecutable(true, false)
    }
}
