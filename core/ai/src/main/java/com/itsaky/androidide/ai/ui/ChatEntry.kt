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

import com.itsaky.androidide.ai.model.ToolCall

enum class ToolEntryState {
  RUNNING,
  AWAITING_APPROVAL,
  SUCCEEDED,
  FAILED
}

sealed interface ChatEntry {

  val id: Long

  data class User(override val id: Long, val text: String) : ChatEntry

  data class Assistant(
    override val id: Long,
    val text: String,
    val streaming: Boolean = false
  ) : ChatEntry

  data class Tool(
    override val id: Long,
    val call: ToolCall,
    val summary: String,
    val state: ToolEntryState,
    val output: String = "",
    val expanded: Boolean = false
  ) : ChatEntry

  data class Thinking(
    override val id: Long,
    val text: String,
    val streaming: Boolean = false,
    val expanded: Boolean = false
  ) : ChatEntry

  data class Error(override val id: Long, val text: String) : ChatEntry
}
