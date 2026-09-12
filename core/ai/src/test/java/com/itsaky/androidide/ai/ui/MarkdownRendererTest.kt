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

import android.text.Spanned
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarkdownRendererTest {

  private fun render(markdown: String): CharSequence {
    val view = TextView(ApplicationProvider.getApplicationContext())
    MarkdownRenderer.render(view, markdown)
    return view.text
  }

  @Test
  fun `headings are styled and lose their hash markers`() {
    val rendered = render("## Big heading")

    assertThat(rendered.toString()).doesNotContain("#")
    assertThat(rendered.toString()).contains("Big heading")
    assertThat((rendered as Spanned).getSpans(0, rendered.length, Any::class.java)).isNotEmpty()
  }

  @Test
  fun `dash lists become bullets instead of literal dashes`() {
    val rendered = render("- first\n- second").toString()

    assertThat(rendered).contains("first")
    assertThat(rendered).contains("second")
    assertThat(rendered).doesNotContain("- first")
  }

  @Test
  fun `inline emphasis and code markers are consumed`() {
    val rendered = render("Use **bold** and `code` here").toString()

    assertThat(rendered).doesNotContain("**")
    assertThat(rendered).doesNotContain("`")
    assertThat(rendered).contains("bold")
    assertThat(rendered).contains("code")
  }

  @Test
  fun `fenced code blocks keep their contents`() {
    val rendered = render("```kotlin\nval a = 1\n```").toString()

    assertThat(rendered).contains("val a = 1")
    assertThat(rendered).doesNotContain("```")
  }

  @Test
  fun `plain text is rendered unchanged`() {
    assertThat(render("just a sentence").toString().trim()).isEqualTo("just a sentence")
  }
}
