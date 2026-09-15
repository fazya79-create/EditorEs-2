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
import com.itsaky.androidide.ai.agent.DocumentSymbolEntry
import com.itsaky.androidide.ai.agent.SymbolIndex
import com.itsaky.androidide.ai.agent.SymbolIndexRegistry
import com.itsaky.androidide.ai.agent.SymbolLocation
import java.io.File
import org.junit.After
import org.junit.Test

class SymbolToolTest {

  private class FakeIndex(
    private val definitions: List<SymbolLocation> = emptyList(),
    private val references: List<SymbolLocation> = emptyList(),
    private val symbols: List<DocumentSymbolEntry> = emptyList()
  ) : SymbolIndex {

    var lastLine: Int = -1
    var lastColumn: Int = -1

    override suspend fun definition(file: File, line: Int, column: Int): List<SymbolLocation> {
      lastLine = line
      lastColumn = column
      return definitions
    }

    override suspend fun references(file: File, line: Int, column: Int): List<SymbolLocation> {
      lastLine = line
      lastColumn = column
      return references
    }

    override suspend fun documentSymbols(file: File): List<DocumentSymbolEntry> = symbols
  }

  @After
  fun tearDown() {
    SymbolIndexRegistry.install(null)
  }

  @Test
  fun `the symbol tools are offered even in plan mode`() {
    val offered = AgentMode.PLAN.access.filter(ToolRegistry.specs()).map { it.name }

    assertThat(offered).containsAtLeast(
      FindSymbolTool.DEFINITION_NAME,
      FindSymbolTool.REFERENCES_NAME,
      DocumentSymbolsTool.NAME
    )
  }

  @Test
  fun `building is withheld in plan mode because it runs the compiler`() {
    val offered = AgentMode.PLAN.access.filter(ToolRegistry.specs()).map { it.name }

    assertThat(offered).doesNotContain(BuildProjectTool.NAME)
  }

  @Test
  fun `the registry resolves every new tool by name`() {
    listOf(
      FindSymbolTool.DEFINITION_NAME,
      FindSymbolTool.REFERENCES_NAME,
      DocumentSymbolsTool.NAME,
      BuildProjectTool.NAME
    ).forEach { name ->
      assertThat(ToolRegistry.find(name)).isNotNull()
    }
  }

  @Test
  fun `no index installed is reported instead of pretending there are no results`() {
    SymbolIndexRegistry.install(null)

    assertThat(SymbolIndexRegistry.current()).isNull()
  }

  @Test
  fun `an installed index is visible to the tools`() {
    val index = FakeIndex()
    SymbolIndexRegistry.install(index)

    assertThat(SymbolIndexRegistry.current()).isSameInstanceAs(index)
  }

  @Test
  fun `the tools describe themselves with the position they were asked about`() {
    val definition = FindSymbolTool(SymbolQuery.DEFINITION)
    val arguments = parseArguments("""{"path":"src/main.cpp","line":42,"column":7}""")

    assertThat(definition.describe(arguments)).isEqualTo("Find definition at src/main.cpp:42")
  }

  @Test
  fun `numeric arguments sent as strings are still understood`() {
    val arguments = parseArguments("""{"line":"42","column":7}""")

    assertThat(arguments.get("line").asIntOrNull()).isEqualTo(42)
    assertThat(arguments.get("column").asIntOrNull()).isEqualTo(7)
  }

  @Test
  fun `a non numeric argument is rejected rather than silently treated as zero`() {
    val arguments = parseArguments("""{"line":"not a number"}""")

    assertThat(arguments.get("line").asIntOrNull()).isNull()
  }
}
