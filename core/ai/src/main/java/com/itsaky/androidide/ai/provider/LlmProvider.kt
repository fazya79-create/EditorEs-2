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

package com.itsaky.androidide.ai.provider

import com.itsaky.androidide.ai.model.ChatRequest
import com.itsaky.androidide.ai.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

enum class ProviderKind {
  OPENAI,
  ANTHROPIC,
  GOOGLE
}

data class ProviderConfig(
  val kind: ProviderKind,
  val baseUrl: String,
  val apiKey: String,
  val model: String,
  val promptCaching: Boolean = true
)

interface LlmProvider {

  val kind: ProviderKind

  fun stream(request: ChatRequest): Flow<ChatStreamEvent>
}
