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
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.itsaky.androidide.ai.agent.AgentEvent
import com.itsaky.androidide.ai.agent.ChatAgent
import com.itsaky.androidide.ai.agent.SystemPrompt
import com.itsaky.androidide.ai.history.ChatHistoryStore
import com.itsaky.androidide.ai.history.ChatSession
import com.itsaky.androidide.ai.history.ChatSessionInfo
import com.itsaky.androidide.ai.history.TitleGenerator
import com.itsaky.androidide.ai.model.ChatMessage
import com.itsaky.androidide.ai.model.ChatRole
import com.itsaky.androidide.ai.prefs.AiPreferences
import com.itsaky.androidide.ai.service.AiStreamingService
import com.itsaky.androidide.ai.provider.AnthropicProvider
import com.itsaky.androidide.ai.provider.GoogleProvider
import com.itsaky.androidide.ai.provider.LlmProvider
import com.itsaky.androidide.ai.provider.ProviderConfig
import com.itsaky.androidide.ai.provider.ProviderKind
import com.itsaky.androidide.ai.tools.ApprovalDecision
import com.itsaky.androidide.ai.tools.ToolApprovalRequest
import com.itsaky.androidide.ai.tools.ToolApprover
import com.itsaky.androidide.ai.tools.ToolGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.itsaky.androidide.ai.provider.OpenAiProvider

class AiChatViewModel(application: Application) : AndroidViewModel(application), ToolApprover {

  private val history = mutableListOf<ChatMessage>()
  private val items = mutableListOf<ChatEntry>()
  private val store = ChatHistoryStore(application)

  private var nextId = 0L
  private var turn: Job? = null
  private var pendingApproval: CompletableDeferred<ApprovalDecision>? = null
  private var pendingPublish: Job? = null
  private var lastPublish = 0L
  private var sessionId = store.newSessionId()
  private var sessionCreatedAt = System.currentTimeMillis()
  private var sessionTitle = ""
  private var titleGenerated = false
  private var lastPrompt: String? = null

  val entries = MutableLiveData<List<ChatEntry>>(emptyList())
  val busy = MutableLiveData(false)
  val approvalRequest = MutableLiveData<ToolApprovalRequest?>(null)
  val yoloMode = MutableLiveData(AiPreferences.yoloMode)
  val contextUsage = MutableLiveData<ContextUsage?>(null)
  val sessions = MutableLiveData<List<ChatSessionInfo>>(emptyList())

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
    val pending = snapshot()
    if (pending != null) {
      viewModelScope.launch { withContext(Dispatchers.IO) { store.save(pending) } }
    }
    history.clear()
    items.clear()
    lastPrompt = null
    sessionId = store.newSessionId()
    sessionCreatedAt = System.currentTimeMillis()
    sessionTitle = ""
    titleGenerated = false
    contextUsage.value = null
    publish()
  }

  fun refreshSessions() {
    viewModelScope.launch {
      val saved = withContext(Dispatchers.IO) { store.list() }
      sessions.value = saved.filter { it.id != sessionId || history.isNotEmpty() }
    }
  }

  fun resume(id: String) {
    if (isBusy || id == sessionId) {
      return
    }

    viewModelScope.launch {
      val session = withContext(Dispatchers.IO) { store.load(id) } ?: return@launch

      persist()

      history.clear()
      history += session.messages
      items.clear()
      items += entriesOf(session.messages)
      lastPrompt = null
      sessionId = session.info.id
      sessionCreatedAt = session.info.createdAt
      sessionTitle = session.info.title
      titleGenerated = session.info.titleGenerated
      contextUsage.value = null
      publish()
    }
  }

  fun deleteSession(id: String) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { store.delete(id) }
      refreshSessions()
    }
  }

  fun retry() {
    val prompt = lastPrompt ?: return
    if (isBusy) {
      return
    }

    val index = history.indexOfLast { it.role == ChatRole.USER && it.text == prompt }
    if (index >= 0) {
      while (history.size > index) {
        history.removeAt(history.size - 1)
      }
    }

    val interrupted = items.indexOfLast { it is ChatEntry.Interrupted }
    if (interrupted >= 0) {
      items.removeAt(interrupted)
      publish()
    }

    lastPrompt = null
    send(prompt)
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
    lastPrompt = message

    val gate = ToolGate(context, this)
    val provider = createProvider(config)
    val agent = ChatAgent(
      provider = provider,
      gate = gate,
      model = config.model,
      systemPrompt = SystemPrompt.build(),
      thinkingLevel = AiPreferences.thinkingLevel,
      maxToolRounds = AiPreferences.maxToolRounds,
      contextWindow = AiPreferences.contextWindowOf(config.kind),
      compactThresholdPercent = AiPreferences.effectiveCompactThreshold()
    )

    busy.value = true
    AiStreamingService.start(context)
    turn = viewModelScope.launch {
      var cancelled = false
      try {
        collect(agent)
        lastPrompt = null
      } catch (err: CancellationException) {
        cancelled = true
        finishStreaming()
        throw err
      } catch (err: Throwable) {
        interrupt(err.message)
      } finally {
        AiStreamingService.stop(context)
        busy.postValue(false)
        approvalRequest.postValue(null)
        pendingApproval = null
        persist()
        if (!cancelled) {
          generateTitleIfNeeded(provider, config.model)
        }
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
    AiStreamingService.stop(getApplication())
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

        is AgentEvent.Interrupted -> {
          finishStreaming()
          append(ChatEntry.Interrupted(nextId(), event.message))
        }

        is AgentEvent.UsageUpdated -> {
          contextUsage.postValue(
            if (event.contextWindow > 0) {
              ContextUsage(event.usage.total, event.contextWindow, event.usage.cachedInputTokens)
            } else {
              null
            }
          )
        }

        is AgentEvent.ContextCompacted -> {
          history.clear()
          history += event.history
          append(ChatEntry.Notice(nextId(), NoticeKind.COMPACTED, event.replaced))
        }

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

  private fun interrupt(message: String?) {
    finishStreaming()
    append(
      ChatEntry.Interrupted(
        nextId(),
        message ?: getApplication<Application>().getString(
          com.itsaky.androidide.resources.R.string.msg_ai_interrupted
        )
      )
    )
  }

  private suspend fun persist() {
    val session = snapshot() ?: return
    withContext(NonCancellable + Dispatchers.IO) { store.save(session) }
  }

  private fun snapshot(): ChatSession? {
    if (history.isEmpty()) {
      return null
    }

    val now = System.currentTimeMillis()
    return ChatSession(
      info = ChatSessionInfo(
        id = sessionId,
        title = sessionTitle.ifBlank { ChatHistoryStore.previewTitle(history) },
        createdAt = if (sessionCreatedAt > 0) sessionCreatedAt else now,
        updatedAt = now,
        messageCount = history.size,
        titleGenerated = titleGenerated
      ),
      messages = history.toList()
    )
  }

  private suspend fun generateTitleIfNeeded(provider: LlmProvider, model: String) {
    if (titleGenerated || history.isEmpty()) {
      return
    }

    val id = sessionId
    val title = runCatching { TitleGenerator.generate(provider, model, history.toList()) }
      .getOrNull()
      ?: return

    if (id != sessionId) {
      withContext(Dispatchers.IO) { store.updateTitle(id, title) }
      return
    }

    sessionTitle = title
    titleGenerated = true
    withContext(Dispatchers.IO) { store.updateTitle(id, title) }
  }

  private fun entriesOf(messages: List<ChatMessage>): List<ChatEntry> =
    messages.mapNotNull { message ->
      when (message.role) {
        ChatRole.USER -> ChatEntry.User(nextId(), message.text)

        ChatRole.ASSISTANT -> message.text
          .takeIf { it.isNotBlank() }
          ?.let { ChatEntry.Assistant(nextId(), it) }

        else -> null
      }
    }

  override fun onCleared() {
    super.onCleared()
    AiStreamingService.stop(getApplication())
  }

  private fun createProvider(config: ProviderConfig): LlmProvider = when (config.kind) {
    ProviderKind.ANTHROPIC -> AnthropicProvider(config)
    ProviderKind.OPENAI -> OpenAiProvider(config)
    ProviderKind.GOOGLE -> GoogleProvider(config)
  }

  private fun finishStreaming() {
    items.replaceAll { entry ->
      if (entry is ChatEntry.Assistant && entry.streaming) {
        entry.copy(streaming = false)
      } else if (entry is ChatEntry.Thinking && entry.streaming) {
        entry.copy(streaming = false)
      } else if (entry is ChatEntry.Tool && entry.state == ToolEntryState.RUNNING) {
        entry.copy(state = ToolEntryState.FAILED, output = "Cancelled.")
      } else {
        entry
      }
    }
    publish()
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
    publishThrottled()
  }

  private fun publishThrottled() {
    val now = SystemClock.uptimeMillis()
    if (now - lastPublish < STREAM_PUBLISH_INTERVAL_MS) {
      if (pendingPublish == null) {
        pendingPublish = viewModelScope.launch {
          delay(STREAM_PUBLISH_INTERVAL_MS)
          pendingPublish = null
          publish()
        }
      }
      return
    }
    publish()
  }

  private fun publish() {
    pendingPublish?.cancel()
    pendingPublish = null
    lastPublish = SystemClock.uptimeMillis()
    entries.postValue(items.toList())
  }

  private fun nextId(): Long = nextId++

  companion object {

    private const val STREAM_PUBLISH_INTERVAL_MS = 80L
  }
}
