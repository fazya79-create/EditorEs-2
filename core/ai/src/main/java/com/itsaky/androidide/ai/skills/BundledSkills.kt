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

import android.content.Context
import java.io.File
import org.slf4j.LoggerFactory

object BundledSkills {

  const val ASSET_DIR = "ai/skills"

  private const val CACHE_DIR = "ai-skills-bundled"
  private const val STAMP = ".version"

  private val log = LoggerFactory.getLogger(BundledSkills::class.java)

  fun names(context: Context): List<String> = runCatching {
    context.assets.list(ASSET_DIR)
      .orEmpty()
      .filter { it.isNotBlank() && !it.endsWith(".md") }
      .sorted()
  }.getOrElse {
    log.debug("Unable to list bundled skills", it)
    emptyList()
  }

  fun rootFor(context: Context, version: String): File? {
    val root = File(context.applicationContext.cacheDir, CACHE_DIR)
    val stamp = File(root, STAMP)

    if (stamp.isFile && runCatching { stamp.readText() }.getOrNull() == version) {
      return root.takeIf { it.isDirectory }
    }

    return runCatching {
      root.deleteRecursively()
      root.mkdirs()
      names(context).forEach { name -> copyAsset(context, "$ASSET_DIR/$name", File(root, name)) }
      stamp.writeText(version)
      root
    }.getOrElse {
      log.debug("Unable to unpack bundled skills", it)
      null
    }
  }

  fun load(context: Context, version: String): List<Skill> {
    val root = rootFor(context, version) ?: return emptyList()

    return SkillRegistry.manifestsIn(root).mapNotNull { manifest ->
      when (val parsed = SkillRegistry.parse(manifest)) {
        is SkillLoadResult.Loaded ->
          parsed.skill.copy(source = SkillSource.BUILT_IN, origin = "bundled")

        is SkillLoadResult.Invalid -> {
          log.debug("Ignoring bundled skill {}: {}", parsed.path, parsed.reason)
          null
        }
      }
    }
  }

  private fun copyAsset(context: Context, path: String, target: File) {
    val children = runCatching { context.assets.list(path) }.getOrNull().orEmpty()

    if (children.isEmpty()) {
      target.parentFile?.mkdirs()
      context.assets.open(path).use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
      }
      return
    }

    target.mkdirs()
    children.forEach { child -> copyAsset(context, "$path/$child", File(target, child)) }
  }
}
