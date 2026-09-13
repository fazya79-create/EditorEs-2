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

class TodoStoreTest {

  @Test
  fun `writing replaces the whole list`() {
    val store = TodoStore()
    store.write(listOf(item("1", "first"), item("2", "second")))

    val result = store.write(listOf(item("1", "only one left", TodoStatus.COMPLETED)))

    assertThat(result).hasSize(1)
    assertThat(result.single().content).isEqualTo("only one left")
  }

  @Test
  fun `duplicate ids collapse to the last one written`() {
    val store = TodoStore()

    val result = store.write(listOf(item("1", "first"), item("1", "revised")))

    assertThat(result).hasSize(1)
    assertThat(result.single().content).isEqualTo("revised")
  }

  @Test
  fun `an unchanged write does not bump the revision`() {
    val store = TodoStore()
    store.write(listOf(item("1", "first")))
    val revision = store.currentRevision()

    store.write(listOf(item("1", "first")))

    assertThat(store.currentRevision()).isEqualTo(revision)
  }

  @Test
  fun `only unfinished work is replayed after compaction`() {
    val store = TodoStore()
    store.write(
      listOf(
        item("1", "already done", TodoStatus.COMPLETED),
        item("2", "being worked on", TodoStatus.IN_PROGRESS),
        item("3", "still queued", TodoStatus.PENDING),
        item("4", "abandoned", TodoStatus.CANCELLED)
      )
    )

    val summary = store.activeSummary()

    assertThat(summary).contains("being worked on")
    assertThat(summary).contains("still queued")
    assertThat(summary).doesNotContain("already done")
    assertThat(summary).doesNotContain("abandoned")
  }

  @Test
  fun `a list with nothing outstanding is not replayed at all`() {
    val store = TodoStore()
    store.write(listOf(item("1", "done", TodoStatus.COMPLETED)))

    assertThat(store.activeSummary()).isNull()
  }

  @Test
  fun `an empty store has nothing to replay`() {
    assertThat(TodoStore().activeSummary()).isNull()
  }

  @Test
  fun `oversized lists and content are capped`() {
    val store = TodoStore()
    val many = (1..TodoStore.MAX_ITEMS + 20).map { item(it.toString(), "step $it") }

    val result = store.write(many)

    assertThat(result).hasSize(TodoStore.MAX_ITEMS)

    val long = store.write(listOf(item("1", "x".repeat(TodoStore.MAX_CONTENT_CHARS + 500))))
    assertThat(long.single().content.length).isAtMost(TodoStore.MAX_CONTENT_CHARS)
  }

  @Test
  fun `an unknown status falls back to pending`() {
    assertThat(TodoStatus.fromWire("nonsense")).isEqualTo(TodoStatus.PENDING)
    assertThat(TodoStatus.fromWire(null)).isEqualTo(TodoStatus.PENDING)
    assertThat(TodoStatus.fromWire("IN_PROGRESS")).isEqualTo(TodoStatus.IN_PROGRESS)
  }

  private fun item(id: String, content: String, status: TodoStatus = TodoStatus.PENDING) =
    TodoItem(id = id, content = content, status = status)
}
