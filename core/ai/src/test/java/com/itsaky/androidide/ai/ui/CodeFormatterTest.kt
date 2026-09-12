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

package com.itsaky.androidide.ai.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CodeFormatterTest {

  @Test
  fun `pretty prints compact json`() {
    val formatted = CodeFormatter.prettyJson("""{"path":"a.txt","content":"x"}""")

    assertThat(formatted).contains("\n")
    assertThat(formatted).contains("\"path\"")
  }

  @Test
  fun `leaves non json text untouched`() {
    val raw = "exit code: 0\nBUILD SUCCESSFUL"

    assertThat(CodeFormatter.prettyJson(raw)).isEqualTo(raw)
  }

  @Test
  fun `does not lose characters while highlighting`() {
    val source = """
      fun main() {
        // greet
        val name = "world"
        println(name)
      }
    """.trimIndent()

    val highlighted = CodeFormatter.highlight(source, COLORS)

    assertThat(highlighted.toString()).isEqualTo(source)
  }

  @Test
  fun `handles unterminated strings without hanging`() {
    val source = "val broken = \"unclosed"

    assertThat(CodeFormatter.highlight(source, COLORS).toString()).isEqualTo(source)
  }

  companion object {
    private val COLORS = SyntaxColors(keyword = 1, string = 2, number = 3, comment = 4)
  }
}
