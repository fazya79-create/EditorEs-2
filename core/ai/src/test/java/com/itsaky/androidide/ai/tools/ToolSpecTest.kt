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
import com.google.gson.JsonParser
import org.junit.Test

class ToolSpecTest {

  @Test
  fun `only read-only tools are auto approved`() {
    val mutating = ToolRegistry.specs().filter { it.mutating }.map { it.name }.toSet()
    val readOnly = ToolRegistry.specs().filterNot { it.mutating }.map { it.name }.toSet()

    assertThat(readOnly).containsExactly(
      ReadFileTool.NAME,
      WebSearchTool.NAME,
      WebFetchTool.NAME
    )
    assertThat(mutating).containsExactly(
      WriteFileTool.NAME,
      EditFileTool.NAME,
      RunShellTool.NAME
    )
  }

  @Test
  fun `every tool schema is a valid json object schema`() {
    ToolRegistry.specs().forEach { spec ->
      val schema = JsonParser.parseString(spec.parametersSchemaJson).asJsonObject
      assertThat(schema.get("type").asString).isEqualTo("object")
      assertThat(schema.has("properties")).isTrue()
      assertThat(spec.description).isNotEmpty()
    }
  }

  @Test
  fun `registry resolves every declared tool by name`() {
    ToolRegistry.specs().forEach { spec ->
      assertThat(ToolRegistry.find(spec.name)).isNotNull()
    }
    assertThat(ToolRegistry.find("no_such_tool")).isNull()
  }

  @Test
  fun `malformed arguments fall back to an empty object`() {
    assertThat(parseArguments("not json").entrySet()).isEmpty()
    assertThat(parseArguments("[1,2]").entrySet()).isEmpty()
    assertThat(parseArguments("""{"path":"a.txt"}""").get("path").asString).isEqualTo("a.txt")
  }
}
