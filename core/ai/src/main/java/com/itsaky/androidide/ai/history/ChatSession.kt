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

import com.google.gson.annotations.SerializedName
import com.itsaky.androidide.ai.model.ChatMessage

data class ChatSessionInfo(
  @SerializedName("id") val id: String,
  @SerializedName("title") val title: String = "",
  @SerializedName("createdAt") val createdAt: Long = 0L,
  @SerializedName("updatedAt") val updatedAt: Long = 0L,
  @SerializedName("messageCount") val messageCount: Int = 0,
  @SerializedName("titleGenerated") val titleGenerated: Boolean = false
)

data class ChatSession(
  @SerializedName("info") val info: ChatSessionInfo,
  @SerializedName("messages") val messages: List<ChatMessage> = emptyList()
)
