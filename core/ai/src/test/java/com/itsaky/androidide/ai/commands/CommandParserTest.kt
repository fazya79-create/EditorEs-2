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

package com.itsaky.androidide.ai.commands

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommandParserTest {

  @Test
  fun `a leading slash word is parsed as a command`() {
    val parsed = CommandParser.parse("/commit")

    assertThat(parsed?.name).isEqualTo("commit")
    assertThat(parsed?.arguments).isEmpty()
  }

  @Test
  fun `arguments after the command name are kept verbatim`() {
    val parsed = CommandParser.parse("/review only the json parser")

    assertThat(parsed?.name).isEqualTo("review")
    assertThat(parsed?.arguments).isEqualTo("only the json parser")
  }

  @Test
  fun `ordinary prose is not treated as a command`() {
    assertThat(CommandParser.parse("what does 3/4 mean")).isNull()
    assertThat(CommandParser.parse("/")).isNull()
    assertThat(CommandParser.parse("/ spaced")).isNull()
  }

  @Test
  fun `the arguments placeholder receives the whole argument string`() {
    val expanded = CommandParser.expand("Review this: \$ARGUMENTS", "the parser")

    assertThat(expanded).isEqualTo("Review this: the parser")
  }

  @Test
  fun `the last positional placeholder absorbs the remaining tokens`() {
    val expanded = CommandParser.expand("run \$1 against \$2", "test the whole suite")

    assertThat(expanded).isEqualTo("run test against the whole suite")
  }

  @Test
  fun `quoted arguments stay a single token`() {
    val expanded = CommandParser.expand("\$1 | \$2", "\"first thing\" second")

    assertThat(expanded).isEqualTo("first thing | second")
  }

  @Test
  fun `arguments are appended when the template has no placeholder`() {
    val expanded = CommandParser.expand("Write a commit message.", "scope it to core/ai")

    assertThat(expanded).isEqualTo("Write a commit message.\n\nscope it to core/ai")
  }

  @Test
  fun `a template without arguments is left untouched`() {
    val expanded = CommandParser.expand("Write a commit message.", "")

    assertThat(expanded).isEqualTo("Write a commit message.")
  }
}
