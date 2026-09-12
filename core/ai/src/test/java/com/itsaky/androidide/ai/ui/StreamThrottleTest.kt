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

class StreamThrottleTest {

  @Test
  fun `streaming assistant entries compare unequal so the list still updates`() {
    val first = ChatEntry.Assistant(1, "Hel", streaming = true)
    val second = ChatEntry.Assistant(1, "Hello", streaming = true)

    assertThat(first).isNotEqualTo(second)
    assertThat(first.id).isEqualTo(second.id)
  }

  @Test
  fun `finishing a stream changes the entry even when the text is identical`() {
    val streaming = ChatEntry.Assistant(1, "done", streaming = true)
    val settled = streaming.copy(streaming = false)

    assertThat(streaming).isNotEqualTo(settled)
  }

  @Test
  fun `toggling expansion produces a distinct tool entry`() {
    val collapsed = ChatEntry.Tool(
      id = 2,
      call = com.itsaky.androidide.ai.model.ToolCall("c1", "read_file", "{}"),
      summary = "Read a.txt",
      state = ToolEntryState.SUCCEEDED
    )

    assertThat(collapsed).isNotEqualTo(collapsed.copy(expanded = true))
  }
}
