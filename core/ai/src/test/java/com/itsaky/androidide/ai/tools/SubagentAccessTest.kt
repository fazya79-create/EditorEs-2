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
import com.itsaky.androidide.ai.agent.SubagentOutcome
import com.itsaky.androidide.ai.agent.SubagentRunner
import org.junit.Test

class SubagentAccessTest {

  private val runner = SubagentRunner { SubagentOutcome(text = "done", toolCalls = 0) }

  @Test
  fun `the dispatch tool is only registered when a runner is supplied`() {
    assertThat(ToolRegistry().find(DispatchSubagentTool.NAME)).isNull()
    assertThat(ToolRegistry(subagentRunner = runner).find(DispatchSubagentTool.NAME)).isNotNull()
  }

  @Test
  fun `a sub-agent can never delegate again`() {
    val specs = ToolRegistry(subagentRunner = runner).specs()
    val offered = ToolAccess.forSubagent(ToolScope.FULL).filter(specs).map { it.name }

    assertThat(offered).doesNotContain(DispatchSubagentTool.NAME)
  }

  @Test
  fun `a sub-agent does not get the primary agent's task list`() {
    val offered = ToolAccess.forSubagent(ToolScope.FULL)
      .filter(ToolRegistry().specs())
      .map { it.name }

    assertThat(offered).doesNotContain(TodoWriteTool.NAME)
  }

  @Test
  fun `a read-only sub-agent is offered no mutating tool`() {
    val offered = ToolAccess.forSubagent(ToolScope.READ_ONLY)
      .filter(ToolRegistry().specs())
      .map { it.name }

    assertThat(offered).containsNoneOf(
      WriteFileTool.NAME,
      EditFileTool.NAME,
      RunShellTool.NAME
    )
    assertThat(offered).contains(ReadFileTool.NAME)
  }

  @Test
  fun `a full-scope sub-agent keeps the mutating tools it needs`() {
    val offered = ToolAccess.forSubagent(ToolScope.FULL)
      .filter(ToolRegistry().specs())
      .map { it.name }

    assertThat(offered).containsAtLeast(
      WriteFileTool.NAME,
      EditFileTool.NAME,
      RunShellTool.NAME
    )
  }

  @Test
  fun `an unknown scope value falls back to read-only`() {
    assertThat(ToolScope.fromWire("nonsense")).isEqualTo(ToolScope.READ_ONLY)
    assertThat(ToolScope.fromWire(null)).isEqualTo(ToolScope.READ_ONLY)
    assertThat(ToolScope.fromWire("full")).isEqualTo(ToolScope.FULL)
  }
}
