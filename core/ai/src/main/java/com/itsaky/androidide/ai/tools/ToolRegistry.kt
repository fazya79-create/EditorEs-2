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

import android.content.Context
import com.itsaky.androidide.ai.agent.SubagentRunner
import com.itsaky.androidide.ai.model.ToolSpec
import com.itsaky.androidide.ai.prefs.AiPreferences
import com.itsaky.androidide.ai.skills.SkillRegistry

class ToolRegistry(
  private val todoStore: TodoStore = TodoStore(),
  skills: (() -> SkillRegistry)? = null,
  subagentRunner: SubagentRunner? = null
) {

  private val tools: Map<String, AiTool> = buildList {
    add(ReadFileTool())
    add(GlobTool())
    add(GrepTool())
    add(WriteFileTool())
    add(EditFileTool())
    add(RunShellTool())
    add(WebSearchTool())
    add(WebFetchTool())
    add(TodoWriteTool(todoStore))
    if (skills != null) {
      add(SkillTool(skills))
    }
    if (subagentRunner != null) {
      add(DispatchSubagentTool(subagentRunner))
    }
  }.associateBy { it.spec.name }

  fun todos(): TodoStore = todoStore

  fun specs(): List<ToolSpec> = tools.values.map { it.spec }

  fun specs(context: Context, access: ToolAccess = ToolAccess.FULL): List<ToolSpec> {
    val available = if (searchToolsAvailable(context)) {
      specs()
    } else {
      tools.values.filterNot { it.spec.name in SEARCH_TOOL_NAMES }.map { it.spec }
    }
    return access.filter(available)
  }

  fun find(name: String): AiTool? = tools[name]

  companion object {

    private val SEARCH_TOOL_NAMES = setOf(WebSearchTool.NAME, WebFetchTool.NAME)

    private val default = ToolRegistry()

    fun specs(): List<ToolSpec> = default.specs()

    fun specs(context: Context): List<ToolSpec> = default.specs(context)

    fun find(name: String): AiTool? = default.find(name)

    fun searchToolsAvailable(context: Context): Boolean =
      AiPreferences.hasSearchApiKey(context)
  }
}
