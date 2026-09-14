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
import com.itsaky.androidide.ai.agent.AgentMode
import com.itsaky.androidide.ai.agent.ChatAgent
import com.itsaky.androidide.ai.agent.SearchAvailability
import com.itsaky.androidide.ai.agent.SubagentOutcome
import com.itsaky.androidide.ai.agent.SubagentRequest
import com.itsaky.androidide.ai.agent.SubagentActivityKind
import com.itsaky.androidide.ai.agent.SubagentMonitor
import com.itsaky.androidide.ai.agent.SubagentSession
import com.itsaky.androidide.ai.agent.SubagentStatus
import com.itsaky.androidide.ai.agent.SystemPrompt
import com.itsaky.androidide.ai.agent.ContextCompactor
import com.itsaky.androidide.ai.agent.EditorContextRegistry
import com.itsaky.androidide.ai.agent.ProjectInstructions
import com.itsaky.androidide.ai.commands.LocalCommands
import com.itsaky.androidide.ai.skills.BundledSkills
import com.itsaky.androidide.ai.skills.Skill
import com.itsaky.androidide.ai.skills.SkillInstallResult
import com.itsaky.androidide.ai.skills.SkillInstaller
import com.itsaky.androidide.ai.skills.SkillRegistry
import com.itsaky.androidide.ai.skills.SkillStore
import com.itsaky.androidide.ai.skills.SkillTreeCopier
import com.itsaky.androidide.ai.commands.CommandRegistry
import com.itsaky.androidide.ai.commands.CommandResolution
import com.itsaky.androidide.ai.commands.SlashCommand
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
import com.itsaky.androidide.ai.tools.TodoItem
import com.itsaky.androidide.ai.tools.ToolAccess
import com.itsaky.androidide.ai.tools.ToolGate
import com.itsaky.androidide.ai.tools.ToolRegistry
import com.itsaky.androidide.ai.tools.ToolScope
import com.itsaky.androidide.ai.tools.WorkspacePaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import com.itsaky.androidide.ai.provider.OpenAiProvider

class AiChatViewModel(application: Application) : AndroidViewModel(application), ToolApprover {

  private val history = mutableListOf<ChatMessage>()
  private val items = mutableListOf<ChatEntry>()
  private val store = ChatHistoryStore(application)
  private var registry = ToolRegistry()

  private var nextId = 0L
  private var turn: Job? = null
  private var pendingApproval: CompletableDeferred<ApprovalDecision>? = null
  private var pendingPublish: Job? = null
  private var lastPublish = 0L
  private var pendingSubagentPublish: Job? = null
  private var lastSubagentPublish = 0L
  private var sessionId = store.newSessionId()
  private var sessionCreatedAt = System.currentTimeMillis()
  private var sessionTitle = ""
  private var titleGenerated = false
  private var deletedSessionId: String? = null
  private var lastPrompt: String? = null
  private var lastSentPrompt: String? = null
  private val delegationsThisTurn = AtomicInteger(0)
  private val nextDelegationId = AtomicLong(0)
  private var commands: CommandRegistry? = null
  private var commandsKey: String? = null
  private var skills: SkillRegistry? = null
  private var skillsKey: String? = null
  private var instructions: String? = null
  private var instructionsKey: String? = null

  private val monitor = SubagentMonitor { publishSubagentsThrottled() }
  private val skillStore = SkillStore(application)
  private val skillInstaller = SkillInstaller(skillStore)
  private val approvalTurnstile = Mutex()

  val entries = MutableLiveData<List<ChatEntry>>(emptyList())
  val busy = MutableLiveData(false)
  val approvalRequest = MutableLiveData<ToolApprovalRequest?>(null)
  val yoloMode = MutableLiveData(AiPreferences.yoloMode)
  val agentMode = MutableLiveData(AiPreferences.agentMode)
  val contextUsage = MutableLiveData<ContextUsage?>(null)
  val sessions = MutableLiveData<List<ChatSessionInfo>>(emptyList())
  val subagents = MutableLiveData<List<SubagentSession>>(emptyList())
  val skillList = MutableLiveData<List<Skill>>(emptyList())
  val skillBusy = MutableLiveData(false)
  val skillMessage = MutableLiveData<SkillMessage?>(null)

  val isBusy: Boolean
    get() = busy.value == true

  fun refreshYoloMode() {
    yoloMode.value = AiPreferences.yoloMode
  }

  fun setYoloMode(enabled: Boolean) {
    AiPreferences.yoloMode = enabled
    yoloMode.value = enabled
  }

  fun refreshAgentMode() {
    agentMode.value = AiPreferences.agentMode
  }

  fun setAgentMode(mode: AgentMode) {
    AiPreferences.agentMode = mode
    agentMode.value = mode
  }

  fun clear() {
    if (isBusy) {
      return
    }
    val pending = snapshot()?.takeIf { it.info.id != deletedSessionId }
    if (pending != null) {
      viewModelScope.launch { withContext(Dispatchers.IO) { store.save(pending) } }
    }
    startNewSession()
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
      registry = ToolRegistry()
      monitor.clear()
      publishSubagents()
      items += entriesOf(session.messages)
      lastPrompt = null
      lastSentPrompt = null
      sessionId = session.info.id
      sessionCreatedAt = session.info.createdAt
      sessionTitle = session.info.title
      titleGenerated = session.info.titleGenerated
      contextUsage.value = null
      publish()
    }
  }

  fun deleteSession(id: String) {
    // Mark before suspending: a save requested in between must not resurrect the session.
    if (id == sessionId) {
      deletedSessionId = id
    }

    viewModelScope.launch {
      withContext(Dispatchers.IO) { store.delete(id) }
      if (id == sessionId && !isBusy) {
        startNewSession()
      }
      refreshSessions()
    }
  }

  private fun startNewSession() {
    history.clear()
    items.clear()
    registry = ToolRegistry()
    monitor.clear()
    publishSubagents()
    commands = null
    skills = null
    skillsKey = null
    instructions = null
    instructionsKey = null
    lastPrompt = null
    lastSentPrompt = null
    sessionId = store.newSessionId()
    sessionCreatedAt = System.currentTimeMillis()
    sessionTitle = ""
    titleGenerated = false
    contextUsage.value = null
    deletedSessionId = null
    publish()
  }

  fun retry() {
    val prompt = lastPrompt ?: return
    if (isBusy) {
      return
    }

    val index = history.indexOfLast { it.role == ChatRole.USER && it.text == lastSentPrompt }
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
    if (LocalCommands.isCompact(message)) {
      append(ChatEntry.User(nextId(), message))
      compactNow()
      return
    }

    val prompt = when (val resolution = resolveCommand(message)) {
      is CommandResolution.Expanded -> resolution.prompt
      is CommandResolution.NotACommand -> message

      is CommandResolution.Unknown -> {
        append(ChatEntry.User(nextId(), message))
        append(ChatEntry.Error(nextId(), unknownCommandMessage(context, resolution)))
        return
      }
    }

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

    history += ChatMessage.user(prompt)
    append(ChatEntry.User(nextId(), message))
    lastPrompt = message
    lastSentPrompt = prompt

    val access = AiPreferences.agentMode.access
    val searchAvailable = ToolRegistry.searchToolsAvailable(context)
    val provider = createProvider(config)

    if (!searchAvailable) {
      append(ChatEntry.Notice(nextId(), NoticeKind.SEARCH_KEY_MISSING))
    }

    val searchAvailability = if (searchAvailable) {
      SearchAvailability.THIRD_PARTY
    } else {
      SearchAvailability.UNAVAILABLE
    }

    delegationsThisTurn.set(0)
    monitor.clear()
    publishSubagents()

    val skills = skillRegistry()
    val instructions = projectInstructions()

    registry = ToolRegistry(
      todoStore = registry.todos(),
      subagentRunner = { request ->
        runSubagent(context, config, provider, searchAvailability, access, request)
      },
      skills = { skillRegistry() }
    )

    val tools = registry.specs(context, access)
    val gate = ToolGate(context, registry, access, this)

    val agent = ChatAgent(
      provider = provider,
      gate = gate,
      model = config.model,
      systemPrompt = SystemPrompt.build(
        searchAvailability = searchAvailability,
        mode = AiPreferences.agentMode,
        skills = skills.all(),
        instructions = instructions,
        editor = EditorContextRegistry.current()
      ),
      thinkingLevel = AiPreferences.thinkingLevel,
      maxToolRounds = AiPreferences.maxToolRounds,
      contextWindow = AiPreferences.contextWindowOf(config.kind),
      compactThresholdPercent = AiPreferences.effectiveCompactThreshold(),
      tools = tools
    )

    busy.value = true
    AiStreamingService.start(context)
    turn = viewModelScope.launch {
      var cancelled = false
      try {
        collect(agent)
        lastPrompt = null
        lastSentPrompt = null
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
    return approvalTurnstile.withLock { awaitApproval(request) }
  }

  private suspend fun awaitApproval(request: ToolApprovalRequest): ApprovalDecision {
    val deferred = CompletableDeferred<ApprovalDecision>()
    pendingApproval = deferred

    val known = updateTool(request.call.id) { entry ->
      entry.copy(state = ToolEntryState.AWAITING_APPROVAL)
    }
    if (!known) {
      updateRunningSubagentEntries { entry ->
        entry.copy(detail = request.summary, awaitingApproval = true)
      }
      monitor.setAwaitingApproval(true)
    }
    approvalRequest.postValue(request)

    return try {
      deferred.await()
    } finally {
      pendingApproval = null
      approvalRequest.postValue(null)
      if (!known) {
        updateRunningSubagentEntries { entry -> entry.copy(awaitingApproval = false) }
        monitor.setAwaitingApproval(false)
      }
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

        is AgentEvent.Reconnecting -> {
          // The retry replays the request from scratch, so anything already streamed is
          // dropped here rather than left to collide with the replacement text.
          streamingId?.let { id ->
            items.removeAll { it.id == id }
            streamingId = null
          }
          streamed.setLength(0)
          thinkingId?.let { id ->
            items.removeAll { it.id == id }
            thinkingId = null
          }
          thought.setLength(0)
          showReconnecting(event)
        }

        is AgentEvent.TodosUpdated -> showTodos(event.items)

        is AgentEvent.Interrupted -> {
          finishStreaming()
          append(ChatEntry.Interrupted(nextId(), event.message))
        }

        is AgentEvent.UsageUpdated -> {
          contextUsage.postValue(
            if (event.contextWindow > 0 && !event.usage.isEmpty) {
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

        AgentEvent.CompactionFailed ->
          append(ChatEntry.Notice(nextId(), NoticeKind.COMPACTION_FAILED, 0))

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
    if (session.info.id == deletedSessionId) {
      return
    }
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
      } else if (entry is ChatEntry.Subagent && entry.state == SubagentState.RUNNING) {
        entry.copy(
          state = SubagentState.FAILED,
          detail = "",
          awaitingApproval = false,
          summary = cancelledMessage()
        )
      } else {
        entry
      }
    }
    monitor.finishRunning(SubagentStatus.FAILED, cancelledMessage())
    publishSubagents()
    publish()
  }

  private fun cancelledMessage(): String = getApplication<Application>().getString(
    com.itsaky.androidide.resources.R.string.msg_ai_subagent_cancelled
  )

  private fun updateTool(callId: String, transform: (ChatEntry.Tool) -> ChatEntry.Tool): Boolean {
    val index = items.indexOfLast { it is ChatEntry.Tool && it.call.id == callId }
    if (index < 0) {
      return false
    }
    items[index] = transform(items[index] as ChatEntry.Tool)
    publish()
    return true
  }

  private fun showReconnecting(event: AgentEvent.Reconnecting) {
    val existing = items.indexOfLast {
      it is ChatEntry.Notice &&
          (it.kind == NoticeKind.RECONNECTING || it.kind == NoticeKind.OFFLINE)
    }
    val id = if (existing >= 0) items.removeAt(existing).id else nextId()
    items += ChatEntry.Notice(
      id = id,
      kind = if (event.offline) NoticeKind.OFFLINE else NoticeKind.RECONNECTING,
      count = event.attempt
    )
    publish()
  }

  private fun append(entry: ChatEntry) {
    items += entry
    publish()
  }

  private fun appendSubagentEntry(
    delegationId: Long,
    description: String,
    scope: ToolScope
  ): Long {
    val id = nextId()
    append(
      ChatEntry.Subagent(
        id = id,
        delegationId = delegationId,
        description = description,
        scope = scope,
        state = SubagentState.RUNNING
      )
    )
    return id
  }

  private fun updateSubagentEntry(
    entryId: Long,
    transform: (ChatEntry.Subagent) -> ChatEntry.Subagent
  ) {
    val index = items.indexOfFirst { it is ChatEntry.Subagent && it.id == entryId }
    if (index < 0) {
      return
    }
    items[index] = transform(items[index] as ChatEntry.Subagent)
    publishThrottled()
  }

  private fun updateRunningSubagentEntries(
    transform: (ChatEntry.Subagent) -> ChatEntry.Subagent
  ) {
    var changed = false
    items.forEachIndexed { index, entry ->
      if (entry is ChatEntry.Subagent && entry.state == SubagentState.RUNNING) {
        items[index] = transform(entry)
        changed = true
      }
    }
    if (changed) {
      publishThrottled()
    }
  }

  private fun finishSubagentEntry(
    entryId: Long,
    summary: String,
    toolCalls: Int,
    failed: Boolean
  ) {
    updateSubagentEntry(entryId) { entry ->
      entry.copy(
        state = if (failed) SubagentState.FAILED else SubagentState.SUCCEEDED,
        summary = summary,
        toolCalls = toolCalls,
        detail = "",
        awaitingApproval = false
      )
    }
    publish()
  }

  private fun skillRegistry(): SkillRegistry {
    val projectDir = runCatching { WorkspacePaths.projectDir() }.getOrNull()
    val key = projectDir?.path.orEmpty()
    val cached = skills
    if (cached != null && skillsKey == key) {
      return cached
    }
    val merged = SkillRegistry.merge(
      project = SkillRegistry.load(projectDir),
      installed = skillStore.installed(),
      bundled = BundledSkills.load(getApplication(), bundledVersion())
    )
    return merged.also {
      skills = it
      skillsKey = key
    }
  }

  private fun bundledVersion(): String = runCatching {
    val context = getApplication<Application>()
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "${info.versionName}:${info.lastUpdateTime}"
  }.getOrDefault("fallback")

  fun refreshSkills() {
    viewModelScope.launch {
      val loaded = withContext(Dispatchers.IO) {
        skills = null
        skillsKey = null
        skillRegistry().all()
      }
      skillList.value = loaded
    }
  }

  fun installSkillFromUrl(url: String) {
    runSkillInstall { skillInstaller.installFromUrl(url) }
  }

  fun installSkillFromTree(context: android.content.Context, tree: android.net.Uri) {
    runSkillInstall {
      val staging = skillStore.newStagingDir()
      try {
        val copied = SkillTreeCopier.copyTree(context, tree, staging)
        if (copied == 0) {
          SkillInstallResult.Failed("Nothing could be read from that folder.")
        } else {
          skillInstaller.installFromDirectory(staging)
        }
      } finally {
        staging.deleteRecursively()
      }
    }
  }

  fun installSkillFromZip(open: () -> java.io.InputStream, origin: String) {
    runSkillInstall { open().use { skillInstaller.installFromZip(it, origin) } }
  }

  private fun runSkillInstall(block: suspend () -> SkillInstallResult) {
    if (skillBusy.value == true) {
      return
    }
    skillBusy.value = true
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching { block() }.getOrElse { err ->
          SkillInstallResult.Failed(err.message ?: "The install failed.")
        }
      }
      skillBusy.value = false
      skillMessage.value = when (result) {
        is SkillInstallResult.Installed -> SkillMessage.Installed(result.names)
        is SkillInstallResult.Failed -> SkillMessage.Failed(result.reason)
      }
      skillStore.clearStaging()
      refreshSkills()
    }
  }

  fun deleteSkill(skill: Skill) {
    if (!skill.source.canDelete) {
      skillMessage.value = SkillMessage.ReadOnly
      return
    }
    viewModelScope.launch {
      val deleted = withContext(Dispatchers.IO) { skillStore.delete(skill.name) }
      if (deleted) {
        skillMessage.value = SkillMessage.Deleted(skill.name)
      }
      refreshSkills()
    }
  }

  fun consumeSkillMessage() {
    skillMessage.value = null
  }

  private fun projectInstructions(): String {
    val projectDir = runCatching { WorkspacePaths.projectDir() }.getOrNull()
    val key = projectDir?.path.orEmpty()
    val cached = instructions
    if (cached != null && instructionsKey == key) {
      return cached
    }
    return ProjectInstructions.load(projectDir).also {
      instructions = it
      instructionsKey = key
    }
  }

  fun compactNow() {
    if (isBusy) {
      return
    }
    if (history.size <= ContextCompactor.KEEP_RECENT_MESSAGES) {
      append(ChatEntry.Notice(nextId(), NoticeKind.COMPACTION_FAILED, 0))
      return
    }

    val context = getApplication<Application>()
    val config = AiPreferences.providerConfig(context)
    if (config.apiKey.isBlank()) {
      append(
        ChatEntry.Error(
          nextId(),
          context.getString(com.itsaky.androidide.resources.R.string.msg_ai_missing_api_key)
        )
      )
      return
    }

    val provider = createProvider(config)
    val agent = ChatAgent(
      provider = provider,
      gate = ToolGate(context, ToolRegistry(), ToolAccess.FULL, this),
      model = config.model,
      systemPrompt = "",
      contextWindow = AiPreferences.contextWindowOf(config.kind)
    )

    busy.value = true
    turn = viewModelScope.launch {
      try {
        agent.compactNow(history.toList()).forEach { event ->
          when (event) {
            is AgentEvent.ContextCompacted -> {
              history.clear()
              history += event.history
              append(ChatEntry.Notice(nextId(), NoticeKind.COMPACTED, event.replaced))
            }

            AgentEvent.CompactionFailed ->
              append(ChatEntry.Notice(nextId(), NoticeKind.COMPACTION_FAILED, 0))

            is AgentEvent.UsageUpdated -> contextUsage.postValue(null)
            else -> Unit
          }
        }
        persist()
      } finally {
        busy.postValue(false)
      }
    }
  }

  private fun resolveCommand(message: String): CommandResolution {
    if (!message.startsWith('/')) {
      return CommandResolution.NotACommand
    }
    return commandRegistry().resolve(message)
  }

  fun availableCommands(): List<SlashCommand> = commandRegistry().all()

  fun suggestCommands(input: String): List<SlashCommand> {
    val text = input.trimStart()
    if (!text.startsWith('/') || text.any { it.isWhitespace() }) {
      return emptyList()
    }
    val prefix = text.drop(1).lowercase()
    return commandRegistry().all().filter { it.name.startsWith(prefix) }
  }

  private fun commandRegistry(): CommandRegistry {
    val projectDir = runCatching { WorkspacePaths.projectDir() }.getOrNull()
    val key = projectDir?.path.orEmpty()
    val cached = commands
    if (cached != null && commandsKey == key) {
      return cached
    }
    return CommandRegistry.load(projectDir).also {
      commands = it
      commandsKey = key
    }
  }

  private suspend fun runSubagent(
    context: Application,
    config: ProviderConfig,
    provider: LlmProvider,
    searchAvailability: SearchAvailability,
    parentAccess: ToolAccess,
    request: SubagentRequest
  ): SubagentOutcome {
    val claimed = delegationsThisTurn.incrementAndGet()
    if (claimed > MAX_DELEGATIONS_PER_TURN) {
      delegationsThisTurn.decrementAndGet()
      return SubagentOutcome(
        text = "Delegation limit reached ($MAX_DELEGATIONS_PER_TURN sub-agents in one turn). " +
            "Do the remaining work yourself.",
        toolCalls = 0,
        failed = true
      )
    }

    val scope = if (parentAccess.scope == ToolScope.READ_ONLY) {
      ToolScope.READ_ONLY
    } else {
      request.scope
    }
    val access = ToolAccess.forSubagent(scope)

    val childRegistry = ToolRegistry(skills = { skillRegistry() })
    val childGate = ToolGate(context, childRegistry, access, this)

    val childTools = childRegistry.specs(context, access)

    val delegationId = nextDelegationId.getAndIncrement()
    val entryId = appendSubagentEntry(delegationId, request.description, scope)
    monitor.start(delegationId, request.description, request.prompt, scope)

    val child = ChatAgent(
      provider = provider,
      gate = childGate,
      model = config.model,
      systemPrompt = SystemPrompt.buildForSubagent(
        searchAvailability = searchAvailability,
        scope = scope,
        toolNames = childTools.map { it.name },
        skills = skillRegistry().all(),
        instructions = projectInstructions()
      ),
      thinkingLevel = AiPreferences.thinkingLevel,
      maxToolRounds = SUBAGENT_MAX_TOOL_ROUNDS,
      contextWindow = AiPreferences.contextWindowOf(config.kind),
      compactThresholdPercent = AiPreferences.effectiveCompactThreshold(),
      tools = childTools
    )

    val transcript = StringBuilder()
    var toolCalls = 0
    var failure: String? = null

    child.run(listOf(ChatMessage.user(request.prompt))).collect { event ->
      when (event) {
        is AgentEvent.AssistantMessage ->
          if (event.message.text.isNotBlank()) {
            transcript.setLength(0)
            transcript.append(event.message.text)
          }

        is AgentEvent.TextDelta ->
          monitor.appendStream(delegationId, SubagentActivityKind.MESSAGE, event.text)

        is AgentEvent.ReasoningDelta ->
          monitor.appendStream(delegationId, SubagentActivityKind.REASONING, event.text)

        is AgentEvent.ToolStarted -> {
          toolCalls++
          monitor.record(
            delegationId,
            SubagentActivityKind.TOOL_STARTED,
            title = event.call.name,
            text = event.summary
          )
          monitor.update(delegationId) { it.copy(toolCalls = toolCalls) }
          updateSubagentEntry(entryId) { entry ->
            entry.copy(toolCalls = toolCalls, detail = event.summary)
          }
        }

        is AgentEvent.ToolFinished -> monitor.record(
          delegationId,
          SubagentActivityKind.TOOL_FINISHED,
          title = event.call.name,
          text = event.result.content,
          isError = event.result.isError
        )

        is AgentEvent.Failed -> {
          failure = event.message
          monitor.record(
            delegationId,
            SubagentActivityKind.NOTICE,
            text = event.message,
            isError = true
          )
        }

        is AgentEvent.Interrupted -> {
          failure = event.message
          monitor.record(
            delegationId,
            SubagentActivityKind.NOTICE,
            text = event.message,
            isError = true
          )
        }

        else -> Unit
      }
    }

    val summary = transcript.toString().trim()
    val failed = failure != null || summary.isEmpty()
    val reported = if (failed) failure ?: "The sub-agent produced no result." else summary

    monitor.update(delegationId) { session ->
      session.copy(
        status = if (failed) SubagentStatus.FAILED else SubagentStatus.SUCCEEDED,
        awaitingApproval = false,
        toolCalls = toolCalls,
        summary = reported
      )
    }

    finishSubagentEntry(
      entryId = entryId,
      summary = if (failed) failure.orEmpty() else summary,
      toolCalls = toolCalls,
      failed = failed
    )

    return SubagentOutcome(text = reported, toolCalls = toolCalls, failed = failed)
  }

  private fun unknownCommandMessage(
    context: Application,
    resolution: CommandResolution.Unknown
  ): String = if (resolution.available.isEmpty()) {
    context.getString(
      com.itsaky.androidide.resources.R.string.msg_ai_no_commands,
      resolution.name
    )
  } else {
    context.getString(
      com.itsaky.androidide.resources.R.string.msg_ai_unknown_command,
      resolution.name,
      resolution.available.joinToString(", ") { "/$it" }
    )
  }

  private fun showTodos(todos: List<TodoItem>) {
    val existing = items.indexOfLast { it is ChatEntry.Todos }
    val id = if (existing >= 0) items.removeAt(existing).id else nextId()
    if (todos.isNotEmpty()) {
      items += ChatEntry.Todos(id, todos)
    }
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

  private fun publishSubagentsThrottled() {
    val now = SystemClock.uptimeMillis()
    if (now - lastSubagentPublish < STREAM_PUBLISH_INTERVAL_MS) {
      if (pendingSubagentPublish == null) {
        pendingSubagentPublish = viewModelScope.launch {
          delay(STREAM_PUBLISH_INTERVAL_MS)
          pendingSubagentPublish = null
          publishSubagents()
        }
      }
      return
    }
    publishSubagents()
  }

  private fun publishSubagents() {
    pendingSubagentPublish?.cancel()
    pendingSubagentPublish = null
    lastSubagentPublish = SystemClock.uptimeMillis()
    subagents.postValue(monitor.snapshot())
  }

  private fun nextId(): Long = nextId++

  companion object {

    private const val STREAM_PUBLISH_INTERVAL_MS = 80L
    private const val MAX_DELEGATIONS_PER_TURN = 6
    private const val SUBAGENT_MAX_TOOL_ROUNDS = 12
  }
}
