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

package com.itsaky.androidide.ai.tools

import com.itsaky.androidide.ai.model.ToolSpec

enum class ToolScope {
  FULL,
  READ_ONLY;

  val wireValue: String
    get() = name.lowercase()

  fun allows(spec: ToolSpec): Boolean = when (this) {
    FULL -> true
    READ_ONLY -> !spec.mutating
  }

  companion object {

    fun fromWire(value: String?): ToolScope =
      entries.firstOrNull { it.wireValue == value?.trim()?.lowercase() } ?: READ_ONLY
  }
}

data class ToolAccess(
  val scope: ToolScope = ToolScope.FULL,
  val denied: Set<String> = emptySet()
) {

  fun permits(spec: ToolSpec): Boolean = scope.allows(spec) && spec.name !in denied

  fun filter(specs: List<ToolSpec>): List<ToolSpec> = specs.filter(::permits)

  companion object {

    val FULL = ToolAccess()

    val SUBAGENT_DENIED = setOf(TodoWriteTool.NAME, DispatchSubagentTool.NAME)

    fun forSubagent(scope: ToolScope) = ToolAccess(scope = scope, denied = SUBAGENT_DENIED)
  }
}
