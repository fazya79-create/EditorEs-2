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

package com.itsaky.androidide.ai.history

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.ai.model.ChatMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatHistoryStoreTest {

  private fun store() = ChatHistoryStore(ApplicationProvider.getApplicationContext())

  private fun session(
    store: ChatHistoryStore,
    title: String = "",
    updatedAt: Long = 1L,
    messages: List<ChatMessage> = listOf(ChatMessage.user("hello"))
  ) = ChatSession(
    info = ChatSessionInfo(
      id = store.newSessionId(),
      title = title,
      createdAt = updatedAt,
      updatedAt = updatedAt,
      messageCount = messages.size
    ),
    messages = messages
  )

  @Test
  fun `a saved session is loaded back with its messages`() {
    val store = store()
    val saved = session(store, title = "Fix the build", messages = listOf(
      ChatMessage.user("why does it fail"),
      ChatMessage.assistant("because of a typo")
    ))

    store.save(saved)

    val loaded = store.load(saved.info.id)
    assertThat(loaded).isNotNull()
    assertThat(loaded!!.info.title).isEqualTo("Fix the build")
    assertThat(loaded.messages).hasSize(2)
    assertThat(loaded.messages.first().text).isEqualTo("why does it fail")
  }

  @Test
  fun `sessions are listed newest first`() {
    val store = store()
    val older = session(store, title = "older", updatedAt = 100L)
    val newer = session(store, title = "newer", updatedAt = 900L)

    store.save(older)
    store.save(newer)

    assertThat(store.list().map { it.title })
      .containsExactly("newer", "older")
      .inOrder()
  }

  @Test
  fun `an empty conversation is never saved`() {
    val store = store()
    val empty = session(store, messages = emptyList())

    store.save(empty)

    assertThat(store.load(empty.info.id)).isNull()
    assertThat(store.list()).isEmpty()
  }

  @Test
  fun `updating the title keeps the messages and marks it generated`() {
    val store = store()
    val saved = session(store, messages = listOf(ChatMessage.user("hi")))
    store.save(saved)

    store.updateTitle(saved.info.id, "Generated title")

    val loaded = store.load(saved.info.id)!!
    assertThat(loaded.info.title).isEqualTo("Generated title")
    assertThat(loaded.info.titleGenerated).isTrue()
    assertThat(loaded.messages).hasSize(1)
  }

  @Test
  fun `a deleted session disappears from the list`() {
    val store = store()
    val saved = session(store, title = "gone soon")
    store.save(saved)

    store.delete(saved.info.id)

    assertThat(store.load(saved.info.id)).isNull()
    assertThat(store.list()).isEmpty()
  }

  @Test
  fun `the preview title falls back to the first non blank line`() {
    val title = ChatHistoryStore.previewTitle(
      listOf(ChatMessage.user("\n\nexplain the gradle setup\nmore detail"))
    )

    assertThat(title).isEqualTo("explain the gradle setup")
  }
}
