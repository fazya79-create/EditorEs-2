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

package com.itsaky.androidide.actions.editor

import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.lsp.models.TextEdit
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import org.junit.Test

class QuickFixEditOrderTest {

  private fun edit(line: Int, column: Int, text: String = "x") =
    TextEdit(Range(Position(line, column), Position(line, column + 1)), text)

  @Test
  fun `edits are applied from the bottom of the file upwards`() {
    val ordered = QuickFixAction.orderedEdits(
      listOf(edit(1, 0, "first"), edit(9, 0, "last"), edit(4, 0, "middle"))
    )

    assertThat(ordered.map { it.newText }).containsExactly("last", "middle", "first").inOrder()
  }

  @Test
  fun `two edits on one line are applied right to left`() {
    val ordered = QuickFixAction.orderedEdits(
      listOf(edit(3, 2, "left"), edit(3, 40, "right"))
    )

    assertThat(ordered.map { it.newText }).containsExactly("right", "left").inOrder()
  }

  @Test
  fun `an already ordered single edit is returned unchanged`() {
    val only = edit(7, 5, "only")

    assertThat(QuickFixAction.orderedEdits(listOf(only))).containsExactly(only)
  }

  @Test
  fun `an empty edit list stays empty`() {
    assertThat(QuickFixAction.orderedEdits(emptyList())).isEmpty()
  }
}
