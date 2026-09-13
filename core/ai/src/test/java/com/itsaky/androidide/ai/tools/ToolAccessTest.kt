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

import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.ai.agent.AgentMode
import org.junit.Test

class ToolAccessTest {

  @Test
  fun `plan mode withholds every mutating tool from the model`() {
    val offered = AgentMode.PLAN.access.filter(ToolRegistry.specs()).map { it.name }

    assertThat(offered).containsNoneOf(
      WriteFileTool.NAME,
      EditFileTool.NAME,
      RunShellTool.NAME
    )
    assertThat(offered).containsAtLeast(
      ReadFileTool.NAME,
      WebSearchTool.NAME,
      WebFetchTool.NAME
    )
  }

  @Test
  fun `build mode offers the full tool set`() {
    val offered = AgentMode.BUILD.access.filter(ToolRegistry.specs()).map { it.name }

    assertThat(offered).containsExactlyElementsIn(ToolRegistry.specs().map { it.name })
  }

  @Test
  fun `a denied tool is refused even when the model calls it anyway`() {
    val access = ToolAccess(denied = setOf(RunShellTool.NAME))
    val shell = ToolRegistry.find(RunShellTool.NAME)!!

    assertThat(access.permits(shell.spec)).isFalse()
  }
}
