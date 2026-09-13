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
import com.itsaky.androidide.ai.model.ToolSpec
import com.itsaky.androidide.ai.prefs.AiPreferences

object ToolRegistry {

  private val tools: Map<String, AiTool> = listOf(
    ReadFileTool(),
    WriteFileTool(),
    EditFileTool(),
    RunShellTool(),
    WebSearchTool(),
    WebFetchTool()
  ).associateBy { it.spec.name }

  private val searchToolNames = setOf(WebSearchTool.NAME, WebFetchTool.NAME)

  fun specs(): List<ToolSpec> = tools.values.map { it.spec }

  fun specs(context: Context): List<ToolSpec> {
    if (searchToolsAvailable(context)) {
      return specs()
    }
    return tools.values.filterNot { it.spec.name in searchToolNames }.map { it.spec }
  }

  fun searchToolsAvailable(context: Context): Boolean =
    AiPreferences.hasSearchApiKey(context)

  fun find(name: String): AiTool? = tools[name]
}
