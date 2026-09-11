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

package com.itsaky.androidide.ai.net

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class SseParsingTest {

  private fun readerOf(payload: String) = SseFrameReader(BufferedReader(StringReader(payload)))

  @Test
  fun `parses named events with data`() {
    val connection = readerOf(
      """
      event: content_block_delta
      data: {"type":"content_block_delta"}

      event: message_stop
      data: {"type":"message_stop"}

      """.trimIndent()
    )

    val first = connection.next()
    assertThat(first?.name).isEqualTo("content_block_delta")
    assertThat(first?.data).isEqualTo("""{"type":"content_block_delta"}""")

    val second = connection.next()
    assertThat(second?.name).isEqualTo("message_stop")
  }

  @Test
  fun `ignores comments and joins multi line data`() {
    val connection = readerOf(
      """
      : ping comment
      data: one
      data: two

      """.trimIndent()
    )

    val event = connection.next()
    assertThat(event?.name).isNull()
    assertThat(event?.data).isEqualTo("one\ntwo")
  }

  @Test
  fun `returns null at end of stream`() {
    val connection = readerOf("data: [DONE]\n\n")

    assertThat(connection.next()?.data).isEqualTo("[DONE]")
    assertThat(connection.next()).isNull()
  }
}
