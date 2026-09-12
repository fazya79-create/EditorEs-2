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

package com.itsaky.androidide.ai.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GoogleStreamTextTest {

  private val provider = GoogleProvider(
    ProviderConfig(ProviderKind.GOOGLE, GoogleProvider.DEFAULT_BASE_URL, "key", "gemini-2.5-flash")
  )

  private fun newText(text: StringBuilder, content: String): String? {
    val method = provider.javaClass.getDeclaredMethod(
      "newText",
      StringBuilder::class.java,
      String::class.java
    )
    method.isAccessible = true
    return method.invoke(provider, text, content) as String?
  }

  private fun accumulate(vararg parts: String): String {
    val text = StringBuilder()
    parts.forEach { part ->
      newText(text, part)?.let { text.append(it) }
    }
    return text.toString()
  }

  private fun stream(vararg parts: String): String {
    val text = StringBuilder()
    val emitted = StringBuilder()
    parts.forEach { part ->
      newText(text, part)?.let {
        text.append(it)
        emitted.append(it)
      }
    }
    assertThat(emitted.toString()).isEqualTo(text.toString())
    return text.toString()
  }

  @Test
  fun `ordinary incremental deltas are appended unchanged`() {
    assertThat(stream("Spain won Euro ", "2024, defeating England 2-1 in the final."))
      .isEqualTo("Spain won Euro 2024, defeating England 2-1 in the final.")
  }

  @Test
  fun `a full snapshot repeated after the deltas is not duplicated`() {
    val full = "Spain won Euro 2024, defeating England 2-1 in the final."

    assertThat(stream("Spain won Euro ", "2024, defeating England 2-1 in the final.", full))
      .isEqualTo(full)
  }

  @Test
  fun `a snapshot that extends the accumulated text only appends the remainder`() {
    val result = stream(
      "Spain won Euro 2024",
      "Spain won Euro 2024, defeating England 2-1 in the final."
    )

    assertThat(result).isEqualTo("Spain won Euro 2024, defeating England 2-1 in the final.")
  }

  @Test
  fun `a partial snapshot shorter than the accumulated text is not re-emitted`() {
    val result = accumulate(
      "Spain won Euro 2024, defeating England 2-1 in the final to claim their fourth title.",
      "Spain won Euro 2024, defeating England 2-1 in the final",
      " to claim their fourth title."
    )

    assertThat(result)
      .isEqualTo("Spain won Euro 2024, defeating England 2-1 in the final to claim their fourth title.")
  }

  @Test
  fun `short repeated fragments are still treated as real deltas`() {
    assertThat(stream("ab", "ab")).isEqualTo("abab")
    assertThat(stream("Yes", ". ", "Yes", ".")).isEqualTo("Yes. Yes.")
  }
}
