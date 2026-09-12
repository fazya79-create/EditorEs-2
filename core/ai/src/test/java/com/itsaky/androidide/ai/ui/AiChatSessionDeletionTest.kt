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

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.ai.history.ChatHistoryStore
import com.itsaky.androidide.ai.history.ChatSession
import com.itsaky.androidide.ai.history.ChatSessionInfo
import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.app.BaseApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = BaseApplication::class)
class AiChatSessionDeletionTest {

  private val application: Application = ApplicationProvider.getApplicationContext()

  private fun idle() = shadowOf(Looper.getMainLooper()).idle()

  private fun awaitUntil(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
      idle()
      if (condition()) {
        return
      }
      Thread.sleep(AWAIT_POLL_MS)
    }
    idle()
  }

  private fun store() = ChatHistoryStore(application)

  private fun saveSession(store: ChatHistoryStore, title: String): ChatSession {
    val session = ChatSession(
      info = ChatSessionInfo(
        id = store.newSessionId(),
        title = title,
        createdAt = 1L,
        updatedAt = 1L,
        messageCount = 2
      ),
      messages = listOf(
        ChatMessage.user("hello there"),
        ChatMessage.assistant("hi, how can I help?")
      )
    )
    store.save(session)
    return session
  }

  private fun resumedViewModel(session: ChatSession): AiChatViewModel {
    val viewModel = AiChatViewModel(application)
    viewModel.resume(session.info.id)
    awaitUntil { !viewModel.entries.value.isNullOrEmpty() }
    return viewModel
  }

  @Test
  fun `deleting the conversation that is open also clears it from the screen`() {
    val store = store()
    val session = saveSession(store, "open conversation")
    val viewModel = resumedViewModel(session)

    assertThat(viewModel.entries.value).isNotEmpty()

    viewModel.deleteSession(session.info.id)
    awaitUntil { viewModel.entries.value.isNullOrEmpty() }

    assertThat(viewModel.entries.value).isEmpty()
  }

  @Test
  fun `a deleted conversation is not written back to disk afterwards`() {
    val store = store()
    val session = saveSession(store, "open conversation")
    val viewModel = resumedViewModel(session)

    viewModel.deleteSession(session.info.id)
    awaitUntil { store.load(session.info.id) == null }

    viewModel.clear()
    awaitUntil { store.load(session.info.id) != null }

    assertThat(store.load(session.info.id)).isNull()
    assertThat(store.list().map { it.id }).doesNotContain(session.info.id)
  }

  @Test
  fun `deleting a different conversation leaves the open one untouched`() {
    val store = store()
    val open = saveSession(store, "open conversation")
    val other = saveSession(store, "some other conversation")
    val viewModel = resumedViewModel(open)

    viewModel.deleteSession(other.info.id)
    awaitUntil { store.load(other.info.id) == null }

    assertThat(viewModel.entries.value).isNotEmpty()
    assertThat(store.load(open.info.id)).isNotNull()
    assertThat(store.load(other.info.id)).isNull()
  }

  companion object {

    private const val AWAIT_TIMEOUT_MS = 5_000L
    private const val AWAIT_POLL_MS = 10L
  }
}
