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

package com.itsaky.androidide.ai.agent

import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.model.ChatRole

// Every request resends the whole history, so a handful of large tool outputs dominate
// upload size and latency for the rest of the turn. Recent results stay verbatim because
// the model is still acting on them; older ones keep a head and a tail so a reference to
// them still resolves.
object ToolResultTrimmer {

  const val KEEP_VERBATIM = 3
  const val TRIMMED_HEAD = 2_000
  const val TRIMMED_TAIL = 500

  fun trim(messages: List<ChatMessage>): List<ChatMessage> {
    val toolIndices = messages.mapIndexedNotNull { index, message ->
      index.takeIf { message.role == ChatRole.TOOL }
    }
    if (toolIndices.size <= KEEP_VERBATIM) {
      return messages
    }

    val trimBefore = toolIndices[toolIndices.size - KEEP_VERBATIM]
    return messages.mapIndexed { index, message ->
      if (message.role != ChatRole.TOOL || index >= trimBefore) {
        message
      } else {
        message.copy(toolResults = message.toolResults.map { it.copy(content = shorten(it.content)) })
      }
    }
  }

  fun shorten(content: String): String {
    if (content.length <= TRIMMED_HEAD + TRIMMED_TAIL) {
      return content
    }
    val omitted = content.length - TRIMMED_HEAD - TRIMMED_TAIL
    return buildString {
      append(content.take(TRIMMED_HEAD))
      append("\n... [")
      append(omitted)
      append(" characters trimmed from an earlier tool result; re-run the tool if you need them]\n")
      append(content.takeLast(TRIMMED_TAIL))
    }
  }
}
