/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.ai.skills

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import org.slf4j.LoggerFactory

sealed interface SkillInstallResult {

  data class Installed(val names: List<String>) : SkillInstallResult

  data class Failed(val reason: String) : SkillInstallResult
}

class SkillInstaller(private val store: SkillStore) {

  fun installFromDirectory(dir: File): SkillInstallResult {
    if (!dir.isDirectory) {
      return SkillInstallResult.Failed("Not a directory: ${dir.name}")
    }

    val manifests = SkillRegistry.manifestsIn(dir)
    if (manifests.isEmpty()) {
      return SkillInstallResult.Failed("No SKILL.md found in ${dir.name}")
    }

    return adoptAll(manifests.map { it.parentFile }, dir.absolutePath)
  }

  fun installFromZip(stream: InputStream, origin: String): SkillInstallResult {
    val staging = store.newStagingDir()
    return try {
      val entries = unzip(stream, staging)
      if (entries == 0) {
        return SkillInstallResult.Failed("The archive was empty.")
      }
      val manifests = SkillRegistry.manifestsIn(staging)
      if (manifests.isEmpty()) {
        return SkillInstallResult.Failed("No SKILL.md found in the archive.")
      }
      adoptAll(manifests.map { it.parentFile }, origin)
    } catch (err: Throwable) {
      log.debug("Failed to install skill archive", err)
      SkillInstallResult.Failed(err.message ?: "The archive could not be read.")
    } finally {
      staging.deleteRecursively()
    }
  }

  fun installFromUrl(rawUrl: String): SkillInstallResult {
    val url = rawUrl.trim()
    if (url.isEmpty()) {
      return SkillInstallResult.Failed("Enter a URL.")
    }
    if (!url.startsWith("https://") && !url.startsWith("http://")) {
      return SkillInstallResult.Failed("Only http and https URLs are supported.")
    }

    val resolved = GitHubSkillUrl.resolve(url)
      ?: return SkillInstallResult.Failed("That URL is not a skill, a repository or an archive.")

    return try {
      when (resolved) {
        is GitHubSkillUrl.Resolved.Archive ->
          open(resolved.url).use { installFromZip(it, url) }

        is GitHubSkillUrl.Resolved.Manifest -> {
          val text = open(resolved.url).use { it.readBytes().decodeToString() }
          installFromManifestText(text, resolved.name, url)
        }
      }
    } catch (err: Throwable) {
      log.debug("Failed to download skill from {}", url, err)
      SkillInstallResult.Failed(err.message ?: "The download failed.")
    }
  }

  fun installFromManifestText(
    text: String,
    fallbackName: String,
    origin: String
  ): SkillInstallResult {
    val staging = store.newStagingDir()
    return try {
      val dir = staging.resolve(fallbackName.ifBlank { "skill" })
      dir.mkdirs()
      dir.resolve(SkillRegistry.MANIFEST).writeText(text)
      adoptAll(listOf(dir), origin)
    } finally {
      staging.deleteRecursively()
    }
  }

  private fun adoptAll(dirs: List<File>, origin: String): SkillInstallResult {
    val installed = mutableListOf<String>()
    var failure: String? = null

    dirs.take(MAX_PER_INSTALL).forEach { dir ->
      val manifest = dir.resolve(SkillRegistry.MANIFEST)
      when (val parsed = SkillRegistry.parse(manifest)) {
        is SkillLoadResult.Loaded -> {
          val target = store.dirFor(parsed.skill.name)
          target.deleteRecursively()
          target.parentFile?.mkdirs()
          if (copyTree(dir, target)) {
            store.writeOrigin(parsed.skill.name, origin)
            installed += parsed.skill.name
          } else {
            failure = "Could not copy ${parsed.skill.name}."
          }
        }

        is SkillLoadResult.Invalid -> failure = parsed.reason
      }
    }

    return if (installed.isEmpty()) {
      SkillInstallResult.Failed(failure ?: "Nothing to install.")
    } else {
      SkillInstallResult.Installed(installed)
    }
  }

  private fun copyTree(from: File, to: File): Boolean = runCatching {
    var bytes = 0L
    from.walkTopDown().forEach { entry ->
      val relative = entry.relativeTo(from).path
      val target = File(to, relative)
      when {
        entry.isDirectory -> target.mkdirs()

        entry.isFile -> {
          bytes += entry.length()
          if (bytes > MAX_SKILL_BYTES) {
            throw IllegalStateException("The skill is larger than ${MAX_SKILL_BYTES / 1024} KB.")
          }
          target.parentFile?.mkdirs()
          entry.copyTo(target, overwrite = true)
        }
      }
    }
    true
  }.getOrElse {
    log.debug("Failed to copy skill tree", it)
    to.deleteRecursively()
    false
  }

  private fun unzip(stream: InputStream, into: File): Int {
    var count = 0
    var total = 0L
    val root = into.canonicalFile

    ZipInputStream(stream).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        val target = File(root, entry.name).canonicalFile

        if (!target.path.startsWith(root.path + File.separator) && target.path != root.path) {
          zip.closeEntry()
          continue
        }

        if (entry.isDirectory) {
          target.mkdirs()
        } else {
          if (count >= MAX_ENTRIES) {
            break
          }
          target.parentFile?.mkdirs()
          target.outputStream().use { output ->
            var written = 0L
            val buffer = ByteArray(BUFFER)
            while (true) {
              val read = zip.read(buffer)
              if (read <= 0) {
                break
              }
              written += read
              total += read
              if (total > MAX_ARCHIVE_BYTES) {
                throw IllegalStateException(
                  "The archive expands to more than ${MAX_ARCHIVE_BYTES / (1024 * 1024)} MB."
                )
              }
              output.write(buffer, 0, read)
            }
          }
          count++
        }
        zip.closeEntry()
      }
    }

    return count
  }

  private fun open(url: String): InputStream {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = CONNECT_TIMEOUT
      readTimeout = READ_TIMEOUT
      instanceFollowRedirects = true
      setRequestProperty("Accept", "*/*")
    }

    val status = connection.responseCode
    if (status !in 200..299) {
      connection.disconnect()
      throw IllegalStateException("The server returned HTTP $status.")
    }
    return connection.inputStream
  }

  companion object {

    private val log = LoggerFactory.getLogger(SkillInstaller::class.java)

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 60_000
    private const val BUFFER = 8 * 1024
    private const val MAX_ENTRIES = 400
    private const val MAX_ARCHIVE_BYTES = 32L * 1024 * 1024
    private const val MAX_SKILL_BYTES = 8L * 1024 * 1024
    private const val MAX_PER_INSTALL = 50
  }
}
