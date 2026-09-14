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

data class Skill(
  val name: String,
  val description: String,
  val body: String,
  val directory: File,
  val license: String = "",
  val compatibility: String = "",
  val version: String = "",
  val allowedTools: List<String> = emptyList(),
  val metadata: Map<String, String> = emptyMap(),
  val resources: List<String> = emptyList(),
  val source: SkillSource = SkillSource.PROJECT,
  val origin: String = ""
) {

  val summary: String
    get() = description.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
}

sealed interface SkillLoadResult {

  data class Loaded(val skill: Skill) : SkillLoadResult

  data class Invalid(val path: String, val reason: String) : SkillLoadResult
}
