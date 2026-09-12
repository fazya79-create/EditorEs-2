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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.itsaky.androidide.ai.agent.AgentEvent
import com.itsaky.androidide.ai.agent.ChatAgent
import com.itsaky.androidide.ai.agent.SystemPrompt
import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.prefs.AiPreferences
import com.itsaky.androidide.ai.provider.AnthropicProvider
import com.itsaky.androidide.ai.provider.LlmProvider
import com.itsaky.androidide.ai.provider.ProviderConfig
import com.itsaky.androidide.ai.provider.ProviderKind
import com.itsaky.androidide.ai.tools.ApprovalDecision
import com.itsaky.androidide.ai.tools.ToolApprovalRequest
import com.itsaky.androidide.ai.tools.ToolApprover
import com.itsaky.androidide.ai.tools.ToolGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.itsaky.androidide.ai.provider.OpenAiProvider

class AiChatViewModel(application: Application) : AndroidViewModel(application), ToolApprover {

  private val history = mutableListOf<ChatMessage>()
  private val items = mutableListOf<ChatEntry>()

  private var nextId = 0L
  private var turn: Job? = null
  private var pendingApproval: CompletableDeferred<ApprovalDecision>? = null

  val entries = MutableLiveData<List<ChatEntry>>(emptyList())
  val busy = MutableLiveData(false)
  val approvalRequest = MutableLiveData<ToolApprovalRequest?>(null)
  val yoloMode = MutableLiveData(AiPreferences.yoloMode)

  val isBusy: Boolean
    get() = busy.value == true

  fun refreshYoloMode() {
    yoloMode.value = AiPreferences.yoloMode
  }

  fun setYoloMode(enabled: Boolean) {
    AiPreferences.yoloMode = enabled
    yoloMode.value = enabled
  }

  fun clear() {
    if (isBusy) {
      return
    }
    history.clear()
    items.clear()
    publish()
  }

  fun send(text: String) {
    val message = text.trim()
    if (message.isEmpty() || isBusy) {
      return
    }

    val context = getApplication<Application>()
    val config = AiPreferences.providerConfig(context)
    if (config.apiKey.isBlank()) {
      append(ChatEntry.User(nextId(), message))
      append(
        ChatEntry.Error(
          nextId(),
          context.getString(
            com.itsaky.androidide.resources.R.string.msg_ai_missing_api_key
          )
        )
      )
      return
    }

    history += ChatMessage.user(message)
    append(ChatEntry.User(nextId(), message))

    val gate = ToolGate(context, this)
    val agent = ChatAgent(
      provider = createProvider(config),
      gate = gate,
      model = config.model,
      systemPrompt = SystemPrompt.build(),
      thinkingLevel = AiPreferences.thinkingLevel
    )

    busy.value = true
    turn = viewModelScope.launch {
      try {
        collect(agent)
      } catch (err: CancellationException) {
        finishStreaming()
        throw err
      } finally {
        busy.postValue(false)
        approvalRequest.postValue(null)
        pendingApproval = null
      }
    }
  }

  fun cancel() {
    pendingApproval?.complete(ApprovalDecision.REJECTED)
    pendingApproval = null
    approvalRequest.value = null
    turn?.cancel()
    turn = null
    finishStreaming()
    busy.value = false
  }

  override suspend fun requestApproval(request: ToolApprovalRequest): ApprovalDecision {
    val deferred = CompletableDeferred<ApprovalDecision>()
    pendingApproval = deferred

    updateTool(request.call.id) { entry ->
      entry.copy(state = ToolEntryState.AWAITING_APPROVAL)
    }
    approvalRequest.postValue(request)

    return try {
      deferred.await()
    } finally {
      pendingApproval = null
      approvalRequest.postValue(null)
    }
  }

  fun resolveApproval(decision: ApprovalDecision) {
    val deferred = pendingApproval ?: return
    pendingApproval = null
    approvalRequest.value = null
    deferred.complete(decision)
  }

  private suspend fun collect(agent: ChatAgent) {
    var streamingId: Long? = null
    var thinkingId: Long? = null
    val streamed = StringBuilder()
    val thought = StringBuilder()

    agent.run(history.toList()).collect { event ->
      when (event) {
        is AgentEvent.ReasoningDelta -> {
          thought.append(event.text)
          val id = thinkingId ?: nextId().also { created ->
            thinkingId = created
            append(ChatEntry.Thinking(created, "", streaming = true))
          }
          replace(id, ChatEntry.Thinking(id, thought.toString(), streaming = true, expanded = isExpanded(id)))
        }

        is AgentEvent.TextDelta -> {
          thinkingId?.let { id ->
            replace(id, ChatEntry.Thinking(id, thought.toString(), streaming = false, expanded = isExpanded(id)))
            thinkingId = null
            thought.setLength(0)
          }
          streamed.append(event.text)
          val id = streamingId ?: nextId().also { created ->
            streamingId = created
            append(ChatEntry.Assistant(created, "", streaming = true))
          }
          replace(id, ChatEntry.Assistant(id, streamed.toString(), streaming = true))
        }

        is AgentEvent.AssistantMessage -> {
          history += event.message
          thinkingId?.let { id ->
            replace(id, ChatEntry.Thinking(id, thought.toString(), streaming = false, expanded = isExpanded(id)))
            thinkingId = null
            thought.setLength(0)
          }
          val id = streamingId
          if (id != null) {
            replace(id, ChatEntry.Assistant(id, event.message.text, streaming = false))
          } else if (event.message.text.isNotBlank()) {
            append(ChatEntry.Assistant(nextId(), event.message.text))
          }
          streamingId = null
          streamed.setLength(0)
        }

        is AgentEvent.ToolStarted -> append(
          ChatEntry.Tool(
            id = nextId(),
            call = event.call,
            summary = event.summary,
            state = ToolEntryState.RUNNING
          )
        )

        is AgentEvent.ToolFinished -> {
          history += ChatMessage.toolResults(listOf(event.result))
          updateTool(event.call.id) { entry ->
            entry.copy(
              state = if (event.result.isError) {
                ToolEntryState.FAILED
              } else {
                ToolEntryState.SUCCEEDED
              },
              output = event.result.content
            )
          }
        }

        is AgentEvent.Failed -> append(ChatEntry.Error(nextId(), event.message))

        AgentEvent.TurnCompleted -> finishStreaming()
      }
    }
  }

  fun toggleExpanded(id: Long) {
    val index = items.indexOfFirst { it.id == id }
    if (index < 0) {
      return
    }

    items[index] = when (val entry = items[index]) {
      is ChatEntry.Tool -> entry.copy(expanded = !entry.expanded)
      is ChatEntry.Thinking -> entry.copy(expanded = !entry.expanded)
      else -> return
    }
    publish()
  }

  private fun isExpanded(id: Long): Boolean =
    (items.firstOrNull { it.id == id } as? ChatEntry.Thinking)?.expanded ?: false

  private fun createProvider(config: ProviderConfig): LlmProvider = when (config.kind) {
    ProviderKind.ANTHROPIC -> AnthropicProvider(config)
    ProviderKind.OPENAI -> OpenAiProvider(config)
  }

  private fun finishStreaming() {
    var changed = false
    items.replaceAll { entry ->
      if (entry is ChatEntry.Assistant && entry.streaming) {
        changed = true
        entry.copy(streaming = false)
      } else if (entry is ChatEntry.Thinking && entry.streaming) {
        changed = true
        entry.copy(streaming = false)
      } else if (entry is ChatEntry.Tool && entry.state == ToolEntryState.RUNNING) {
        changed = true
        entry.copy(state = ToolEntryState.FAILED, output = "Cancelled.")
      } else {
        entry
      }
    }
    if (changed) {
      publish()
    }
  }

  private fun updateTool(callId: String, transform: (ChatEntry.Tool) -> ChatEntry.Tool) {
    val index = items.indexOfLast { it is ChatEntry.Tool && it.call.id == callId }
    if (index < 0) {
      return
    }
    items[index] = transform(items[index] as ChatEntry.Tool)
    publish()
  }

  private fun append(entry: ChatEntry) {
    items += entry
    publish()
  }

  private fun replace(id: Long, entry: ChatEntry) {
    val index = items.indexOfFirst { it.id == id }
    if (index < 0) {
      return
    }
    items[index] = entry
    publish()
  }

  private fun publish() {
    entries.postValue(items.toList())
  }

  private fun nextId(): Long = nextId++
}
