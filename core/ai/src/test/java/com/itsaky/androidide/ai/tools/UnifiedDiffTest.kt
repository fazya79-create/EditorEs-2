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
import org.junit.Test

class UnifiedDiffTest {

  @Test
  fun `identical text produces no diff at all`() {
    assertThat(UnifiedDiff.render("same\ntext", "same\ntext")).isEmpty()
  }

  @Test
  fun `a changed line is shown as a removal followed by an addition`() {
    val diff = UnifiedDiff.render("a\nb\nc", "a\nB\nc")

    assertThat(diff).contains("- b")
    assertThat(diff).contains("+ B")
    assertThat(diff).contains("(-1 +1 lines)")
  }

  @Test
  fun `unchanged lines around the change are kept as context`() {
    val before = (1..12).joinToString("\n") { "line$it" }
    val after = before.replace("line7", "changed7")

    val diff = UnifiedDiff.render(before, after)

    assertThat(diff).contains("- line7")
    assertThat(diff).contains("+ changed7")
    assertThat(diff).contains("  line6")
    assertThat(diff).contains("  line8")
    assertThat(diff).doesNotContain("line1\n")
  }

  @Test
  fun `a new file shows every line as an addition`() {
    val diff = UnifiedDiff.render("", "first\nsecond")

    assertThat(diff).contains("+ first")
    assertThat(diff).contains("+ second")
  }

  @Test
  fun `the reported line number points at the first change`() {
    val diff = UnifiedDiff.render("a\nb\nc\nd", "a\nb\nX\nd")

    assertThat(diff).startsWith("@@ line 3 @@")
  }

  @Test
  fun `a very long line is clipped instead of flooding the preview`() {
    val diff = UnifiedDiff.render("short", "x".repeat(5000))

    assertThat(diff).contains("…")
    diff.split('\n').forEach { assertThat(it.length).isLessThan(400) }
  }

  @Test
  fun `a huge change is truncated to a bounded number of diff lines`() {
    val before = (1..500).joinToString("\n") { "old$it" }
    val after = (1..500).joinToString("\n") { "new$it" }

    val diff = UnifiedDiff.render(before, after)

    assertThat(diff).contains("more diff lines")
    assertThat(diff.split('\n').size).isLessThan(200)
  }
}
