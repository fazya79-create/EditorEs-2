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

class MarkdownNormalizerTest {

  @Test
  fun `inserts the missing blank line before a table glued to a paragraph`() {
    val normalized = MarkdownNormalizer.normalize(
      "Components (includes/)\n| Dir | Role |\n|------|------|\n| il2cpp | wrapper |"
    )

    assertThat(normalized).isEqualTo(
      "Components (includes/)\n\n| Dir | Role |\n|------|------|\n| il2cpp | wrapper |"
    )
  }

  @Test
  fun `leaves a correctly separated table untouched`() {
    val source = "Intro\n\n| Dir | Role |\n|------|------|\n| il2cpp | wrapper |"

    assertThat(MarkdownNormalizer.normalize(source)).isEqualTo(source)
  }

  @Test
  fun `leaves a table at the very start untouched`() {
    val source = "| Dir | Role |\n|------|------|\n| il2cpp | wrapper |"

    assertThat(MarkdownNormalizer.normalize(source)).isEqualTo(source)
  }

  @Test
  fun `does not touch markdown without pipes`() {
    val source = "# Title\n\n- one\n- two\n\nSome text with a - dash"

    assertThat(MarkdownNormalizer.normalize(source)).isEqualTo(source)
  }

  @Test
  fun `does not mistake a setext heading underline for a table delimiter`() {
    val source = "Heading\n-------\n\nbody"

    assertThat(MarkdownNormalizer.normalize(source)).isEqualTo(source)
  }

  @Test
  fun `does not split a horizontal rule after a paragraph`() {
    val source = "paragraph\n\n---\n\nnext"

    assertThat(MarkdownNormalizer.normalize(source)).isEqualTo(source)
  }

  @Test
  fun `handles alignment markers in the delimiter row`() {
    val normalized = MarkdownNormalizer.normalize(
      "Lead in\n| A | B |\n|:---|---:|\n| 1 | 2 |"
    )

    assertThat(normalized).isEqualTo("Lead in\n\n| A | B |\n|:---|---:|\n| 1 | 2 |")
  }

  @Test
  fun `fixes several glued tables in one response`() {
    val normalized = MarkdownNormalizer.normalize(
      "First\n| A | B |\n|---|---|\n| 1 | 2 |\n\nSecond\n| C | D |\n|---|---|\n| 3 | 4 |"
    )

    assertThat(normalized).contains("First\n\n| A | B |")
    assertThat(normalized).contains("Second\n\n| C | D |")
  }
}
