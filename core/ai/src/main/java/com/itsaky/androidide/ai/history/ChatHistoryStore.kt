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

import android.content.Context
import com.google.gson.Gson
import com.itsaky.androidide.ai.model.ChatMessage
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

class ChatHistoryStore(context: Context) {

  private val root = File(context.applicationContext.filesDir, DIR_NAME)

  fun newSessionId(): String = UUID.randomUUID().toString()

  fun list(): List<ChatSessionInfo> {
    val files = root.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
      ?: return emptyList()

    return files.mapNotNull { file -> read(file)?.info }
      .filter { it.messageCount > 0 }
      .sortedByDescending { it.updatedAt }
  }

  fun load(id: String): ChatSession? = read(fileFor(id))

  fun save(session: ChatSession) {
    if (session.messages.isEmpty()) {
      return
    }

    runCatching {
      if (!root.isDirectory) {
        root.mkdirs()
      }
      fileFor(session.info.id).writeText(gson.toJson(session))
    }.onFailure { err ->
      log.error("Failed to save chat session {}", session.info.id, err)
    }
  }

  fun updateTitle(id: String, title: String) {
    val session = load(id) ?: return
    save(session.copy(info = session.info.copy(title = title, titleGenerated = true)))
  }

  fun delete(id: String) {
    runCatching { fileFor(id).delete() }
  }

  private fun read(file: File): ChatSession? {
    if (!file.isFile) {
      return null
    }
    return runCatching { gson.fromJson(file.readText(), ChatSession::class.java) }
      .getOrElse { err ->
        log.error("Failed to read chat session from {}", file.name, err)
        null
      }
      ?.takeIf { it.info.id.isNotBlank() }
  }

  private fun fileFor(id: String) = File(root, "${id}$EXTENSION")

  companion object {

    fun previewTitle(messages: List<ChatMessage>): String =
      messages.firstOrNull { it.text.isNotBlank() }
        ?.text
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(MAX_TITLE_LENGTH)
        .orEmpty()

    const val MAX_TITLE_LENGTH = 60

    private const val DIR_NAME = "ai-history"
    private const val EXTENSION = ".json"

    private val gson = Gson()
    private val log = LoggerFactory.getLogger(ChatHistoryStore::class.java)
  }
}
