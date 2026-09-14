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

class SkillStore(context: Context) {

  private val root = File(context.applicationContext.filesDir, DIR_NAME)
  private val staging = File(context.applicationContext.cacheDir, STAGING_NAME)

  fun installedRoot(): File = root

  fun dirFor(name: String): File = File(root, name)

  fun newStagingDir(): File {
    staging.mkdirs()
    val dir = File(staging, System.nanoTime().toString())
    dir.mkdirs()
    return dir
  }

  fun writeOrigin(name: String, origin: String) {
    if (origin.isBlank()) {
      return
    }
    runCatching { File(dirFor(name), ORIGIN_FILE).writeText(origin.trim()) }
  }

  fun readOrigin(name: String): String =
    runCatching { File(dirFor(name), ORIGIN_FILE).takeIf { it.isFile }?.readText()?.trim() }
      .getOrNull()
      .orEmpty()

  fun delete(name: String): Boolean {
    val dir = dirFor(name)
    if (!dir.isDirectory) {
      return false
    }
    return dir.deleteRecursively()
  }

  fun installed(): List<Skill> {
    if (!root.isDirectory) {
      return emptyList()
    }

    return SkillRegistry.manifestsIn(root).mapNotNull { manifest ->
      when (val parsed = SkillRegistry.parse(manifest)) {
        is SkillLoadResult.Loaded -> parsed.skill.copy(
          source = SkillSource.INSTALLED,
          origin = readOrigin(parsed.skill.name)
        )

        is SkillLoadResult.Invalid -> null
      }
    }
  }

  fun clearStaging() {
    runCatching { staging.deleteRecursively() }
  }

  companion object {

    private const val DIR_NAME = "ai-skills"
    private const val STAGING_NAME = "ai-skills-staging"
    const val ORIGIN_FILE = ".origin"
  }
}
