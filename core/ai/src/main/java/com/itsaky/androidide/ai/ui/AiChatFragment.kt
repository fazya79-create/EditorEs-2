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

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.itsaky.androidide.ai.databinding.FragmentAiChatBinding
import com.itsaky.androidide.ai.databinding.LayoutAiHistoryBinding
import com.itsaky.androidide.ai.databinding.LayoutAiSkillUrlBinding
import com.itsaky.androidide.ai.databinding.LayoutAiSkillsBinding
import com.itsaky.androidide.ai.databinding.LayoutAiSubagentsBinding
import com.itsaky.androidide.ai.agent.AgentMode
import com.itsaky.androidide.ai.agent.SubagentSession
import com.itsaky.androidide.ai.history.ChatSessionInfo
import com.itsaky.androidide.ai.tools.ApprovalDecision
import com.itsaky.androidide.fragments.FragmentWithBinding
import com.itsaky.androidide.fragments.ImeInsetsAware
import com.itsaky.androidide.utils.DialogUtils

class AiChatFragment : FragmentWithBinding<FragmentAiChatBinding>(FragmentAiChatBinding::inflate),
  ImeInsetsAware {

  private val viewModel by viewModels<AiChatViewModel>(ownerProducer = { requireActivity() })
  private val adapter by lazy { AiChatAdapter(viewModel::toggleExpanded, ::onRetryClicked) }
  private val commandAdapter by lazy { CommandSuggestionAdapter(::onCommandPicked) }

  private var baseInputMargin = 0
  private var lastImeInset = 0

  private val pickSkillArchive = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri -> uri?.let { installSkillArchive(it) } }

  private val pickSkillFolder = registerForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
  ) { uri -> uri?.let { installSkillFolder(it) } }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.messages.layoutManager = LinearLayoutManager(requireContext())
    binding.messages.adapter = adapter
    binding.messages.itemAnimator = null

    applyImeInsets()

    binding.send.setOnClickListener { onSendClicked() }
    binding.input.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        onSendClicked()
        true
      } else {
        false
      }
    }
    binding.commandList.layoutManager = LinearLayoutManager(requireContext())
    binding.commandList.adapter = commandAdapter

    binding.input.doAfterTextChanged {
      updateSendButton()
      updateCommandSuggestions()
    }

    binding.approvalAccept.setOnClickListener {
      viewModel.resolveApproval(ApprovalDecision.APPROVED)
    }
    binding.approvalReject.setOnClickListener {
      viewModel.resolveApproval(ApprovalDecision.REJECTED)
    }

    binding.newChat.setOnClickListener { viewModel.clear() }
    binding.history.setOnClickListener { showHistory() }
    binding.subagents.setOnClickListener { showSubagents() }
    binding.skills.setOnClickListener { showSkills() }
    binding.agentMode.setOnClickListener { toggleAgentMode() }

    viewModel.entries.observe(viewLifecycleOwner) { entries ->
      val atBottom = isAtBottom()
      adapter.submitList(entries) {
        if (entries.isNotEmpty() && atBottom) {
          scrollToEnd()
        }
      }
      binding.emptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    viewModel.busy.observe(viewLifecycleOwner) { updateSendButton() }

    viewModel.subagents.observe(viewLifecycleOwner) { sessions ->
      binding.subagents.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
    }

    viewModel.agentMode.observe(viewLifecycleOwner) { mode ->
      val plan = mode == AgentMode.PLAN
      binding.agentMode.setText(
        if (plan) {
          com.itsaky.androidide.resources.R.string.title_ai_mode_plan
        } else {
          com.itsaky.androidide.resources.R.string.title_ai_mode_build
        }
      )
    }

    viewModel.approvalRequest.observe(viewLifecycleOwner) { request ->
      binding.approvalBar.visibility = if (request == null) View.GONE else View.VISIBLE
      binding.approvalSummary.text = request?.summary.orEmpty()

      val preview = request?.preview.orEmpty()
      binding.approvalPreviewScroll.visibility =
        if (preview.isBlank()) View.GONE else View.VISIBLE
      binding.approvalPreview.text = preview
      if (preview.isNotBlank()) {
        binding.approvalPreviewScroll.scrollTo(0, 0)
      }
    }

    viewModel.contextUsage.observe(viewLifecycleOwner) { usage ->
      if (usage == null) {
        binding.contextUsage.visibility = View.GONE
      } else {
        binding.contextUsage.visibility = View.VISIBLE
        binding.contextUsage.text = if (usage.cachedPercent > 0) {
          getString(
            com.itsaky.androidide.resources.R.string.msg_ai_context_usage_cached,
            usage.percent,
            usage.cachedPercent
          )
        } else {
          getString(
            com.itsaky.androidide.resources.R.string.msg_ai_context_usage,
            usage.percent
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshYoloMode()
    viewModel.refreshAgentMode()
  }

  private fun onCommandPicked(command: com.itsaky.androidide.ai.commands.SlashCommand) {
    binding.input.setText("/${command.name} ")
    binding.input.setSelection(binding.input.text?.length ?: 0)
  }

  private fun updateCommandSuggestions() {
    val text = binding.input.text?.toString().orEmpty()
    val matches = viewModel.suggestCommands(text)
    commandAdapter.submitList(matches)
    binding.commandSuggestions.visibility = if (matches.isEmpty()) View.GONE else View.VISIBLE
  }

  private fun toggleAgentMode() {    val next = if (viewModel.agentMode.value == AgentMode.PLAN) {
      AgentMode.BUILD
    } else {
      AgentMode.PLAN
    }
    viewModel.setAgentMode(next)
  }

  private fun onRetryClicked() {
    viewModel.retry()
  }

  private fun showHistory() {
    viewModel.refreshSessions()

    val historyBinding = LayoutAiHistoryBinding.inflate(LayoutInflater.from(requireContext()))
    val dialog = DialogUtils.newMaterialDialogBuilder(requireContext())
      .setTitle(com.itsaky.androidide.resources.R.string.title_ai_history)
      .setView(historyBinding.root)
      .setNegativeButton(android.R.string.cancel, null)
      .create()

    val historyAdapter = AiHistoryAdapter(
      onResume = { info ->
        viewModel.resume(info.id)
        dialog.dismiss()
      },
      onDelete = { info -> confirmDeleteSession(info) }
    )

    historyBinding.sessions.layoutManager = LinearLayoutManager(requireContext())
    historyBinding.sessions.adapter = historyAdapter

    viewModel.sessions.observe(viewLifecycleOwner) { saved ->
      submitSessions(historyBinding, historyAdapter, saved)
    }

    dialog.setOnDismissListener { viewModel.sessions.removeObservers(viewLifecycleOwner) }
    dialog.show()
  }

  private fun showSubagents() {
    val panelBinding = LayoutAiSubagentsBinding.inflate(LayoutInflater.from(requireContext()))
    val dialog = DialogUtils.newMaterialDialogBuilder(requireContext())
      .setTitle(com.itsaky.androidide.resources.R.string.title_ai_subagents)
      .setView(panelBinding.root)
      .setNegativeButton(android.R.string.cancel, null)
      .create()

    val activityAdapter = SubagentActivityAdapter()
    panelBinding.subagentActivity.layoutManager = LinearLayoutManager(requireContext())
    panelBinding.subagentActivity.adapter = activityAdapter
    panelBinding.subagentActivity.itemAnimator = null

    val panel = SubagentPanel(panelBinding, activityAdapter)

    panelBinding.subagentTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
      override fun onTabSelected(tab: TabLayout.Tab) {
        panel.select(tab.tag as? Long ?: return)
      }

      override fun onTabUnselected(tab: TabLayout.Tab) = Unit

      override fun onTabReselected(tab: TabLayout.Tab) = Unit
    })

    val observer = Observer<List<SubagentSession>> { sessions -> panel.submit(sessions) }
    viewModel.subagents.observe(viewLifecycleOwner, observer)

    dialog.setOnDismissListener { viewModel.subagents.removeObserver(observer) }
    dialog.show()
  }

  private fun showSkills() {
    viewModel.refreshSkills()

    val panel = LayoutAiSkillsBinding.inflate(LayoutInflater.from(requireContext()))
    val dialog = DialogUtils.newMaterialDialogBuilder(requireContext())
      .setTitle(com.itsaky.androidide.resources.R.string.title_ai_skills)
      .setView(panel.root)
      .setNegativeButton(android.R.string.cancel, null)
      .create()

    val adapter = AiSkillAdapter(
      onDelete = { skill -> confirmDeleteSkill(skill) },
      onSelect = { skill -> showSkillDetail(skill) }
    )
    panel.skillList.layoutManager = LinearLayoutManager(requireContext())
    panel.skillList.adapter = adapter

    panel.addFromStorage.setOnClickListener { promptStorageSource() }
    panel.addFromUrl.setOnClickListener { promptSkillUrl() }

    val listObserver = Observer<List<com.itsaky.androidide.ai.skills.Skill>> { skills ->
      adapter.submitList(skills)
      panel.skillsEmpty.visibility = if (skills.isEmpty()) View.VISIBLE else View.GONE
    }
    val busyObserver = Observer<Boolean> { busy ->
      panel.skillProgress.visibility = if (busy) View.VISIBLE else View.GONE
      panel.addFromStorage.isEnabled = !busy
      panel.addFromUrl.isEnabled = !busy
      if (busy) {
        panel.skillStatus.visibility = View.VISIBLE
        panel.skillStatus.setText(com.itsaky.androidide.resources.R.string.msg_ai_skill_installing)
      }
    }
    val messageObserver = Observer<SkillMessage?> { message ->
      if (message == null) {
        return@Observer
      }
      panel.skillStatus.visibility = View.VISIBLE
      panel.skillStatus.text = skillMessageText(message)
      viewModel.consumeSkillMessage()
    }

    viewModel.skillList.observe(viewLifecycleOwner, listObserver)
    viewModel.skillBusy.observe(viewLifecycleOwner, busyObserver)
    viewModel.skillMessage.observe(viewLifecycleOwner, messageObserver)

    dialog.setOnDismissListener {
      viewModel.skillList.removeObserver(listObserver)
      viewModel.skillBusy.removeObserver(busyObserver)
      viewModel.skillMessage.removeObserver(messageObserver)
    }
    dialog.show()
  }

  private fun skillMessageText(message: SkillMessage): String = when (message) {
    is SkillMessage.Installed -> resources.getQuantityString(
      com.itsaky.androidide.resources.R.plurals.msg_ai_skill_installed,
      message.names.size,
      message.names.size
    ) + ": " + message.names.joinToString(", ")

    is SkillMessage.Failed -> getString(
      com.itsaky.androidide.resources.R.string.msg_ai_skill_install_failed,
      message.reason
    )

    is SkillMessage.Deleted -> getString(
      com.itsaky.androidide.resources.R.string.msg_ai_skill_deleted,
      message.name
    )

    SkillMessage.ReadOnly ->
      getString(com.itsaky.androidide.resources.R.string.msg_ai_skill_builtin_readonly)
  }

  private fun promptStorageSource() {
    val options = arrayOf(
      getString(com.itsaky.androidide.resources.R.string.action_ai_skill_pick_folder),
      getString(com.itsaky.androidide.resources.R.string.action_ai_skill_pick_zip)
    )
    DialogUtils.newMaterialDialogBuilder(requireContext())
      .setTitle(com.itsaky.androidide.resources.R.string.action_ai_skill_add_storage)
      .setItems(options) { d, which ->
        d.dismiss()
        if (which == 0) {
          pickSkillFolder.launch(null)
        } else {
          pickSkillArchive.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun promptSkillUrl() {
    val urlBinding = LayoutAiSkillUrlBinding.inflate(LayoutInflater.from(requireContext()))
    DialogUtils.newMaterialDialogBuilder(requireContext())
      .setTitle(com.itsaky.androidide.resources.R.string.title_ai_skill_add_url)
      .setView(urlBinding.root)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(com.itsaky.androidide.resources.R.string.action_ai_skill_install) { d, _ ->
        val url = urlBinding.urlInput.text?.toString().orEmpty().trim()
        d.dismiss()
        if (url.isNotEmpty()) {
          viewModel.installSkillFromUrl(url)
        }
      }
      .show()
  }

  private fun confirmDeleteSkill(skill: com.itsaky.androidide.ai.skills.Skill) {
    DialogUtils.newYesNoDialog(
      context = requireContext(),
      title = getString(com.itsaky.androidide.resources.R.string.title_confirm_delete),
      message = getString(
        com.itsaky.androidide.resources.R.string.msg_ai_skill_confirm_delete,
        skill.name
      ),
      positiveClickListener = { d, _ ->
        d.dismiss()
        viewModel.deleteSkill(skill)
      }
    ) { d, _ -> d.dismiss() }.show()
  }

  private fun showSkillDetail(skill: com.itsaky.androidide.ai.skills.Skill) {
    DialogUtils.newMaterialDialogBuilder(requireContext())
      .setTitle(skill.name)
      .setMessage(
        buildString {
          if (skill.description.isNotBlank()) {
            append(skill.description).append("\n\n")
          }
          if (skill.license.isNotBlank()) {
            append("License: ").append(skill.license).append('\n')
          }
          if (skill.compatibility.isNotBlank()) {
            append("Requires: ").append(skill.compatibility).append('\n')
          }
          if (skill.origin.isNotBlank()) {
            append("Source: ").append(skill.origin).append('\n')
          }
          if (skill.resources.isNotEmpty()) {
            append("Files: ").append(skill.resources.joinToString(", "))
          }
        }.trim()
      )
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }

  private fun installSkillFolder(tree: Uri) {
    val context = requireContext().applicationContext
    viewModel.installSkillFromTree(context, tree)
  }

  private fun installSkillArchive(uri: Uri) {
    val context = requireContext().applicationContext
    viewModel.installSkillFromZip(
      open = { context.contentResolver.openInputStream(uri) ?: error("Cannot read that file.") },
      origin = uri.lastPathSegment.orEmpty()
    )
  }

  private fun confirmDeleteSession(info: ChatSessionInfo) {    val title = info.title.ifBlank {
      getString(com.itsaky.androidide.resources.R.string.msg_ai_history_untitled)
    }
    DialogUtils.newYesNoDialog(
      context = requireContext(),
      title = getString(com.itsaky.androidide.resources.R.string.title_confirm_delete),
      message = getString(com.itsaky.androidide.resources.R.string.msg_confirm_delete, title),
      positiveClickListener = { dialog, _ ->
        dialog.dismiss()
        viewModel.deleteSession(info.id)
      }
    ) { dialog, _ -> dialog.dismiss() }.show()
  }

  private fun submitSessions(
    historyBinding: LayoutAiHistoryBinding,
    historyAdapter: AiHistoryAdapter,
    saved: List<ChatSessionInfo>
  ) {
    historyAdapter.submitList(saved)
    historyBinding.historyEmpty.visibility = if (saved.isEmpty()) View.VISIBLE else View.GONE
    historyBinding.sessions.visibility = if (saved.isEmpty()) View.GONE else View.VISIBLE
  }

  private fun applyImeInsets() {
    baseInputMargin = binding.inputRow.marginBottom
    applyImeInset(lastImeInset)
  }

  override fun onImeInsetChanged(bottom: Int) {
    lastImeInset = bottom
    if (_binding != null) {
      applyImeInset(bottom)
    }
  }

  private fun applyImeInset(bottom: Int) {
    binding.inputRow.updateLayoutParams<MarginLayoutParams> {
      bottomMargin = baseInputMargin + bottom
    }
    if (bottom > 0) {
      scrollToEnd()
    }
  }

  private fun isAtBottom(): Boolean {
    val binding = _binding ?: return true
    return !binding.messages.canScrollVertically(1)
  }

  private fun scrollToEnd() {
    val binding = _binding ?: return
    if (adapter.itemCount == 0) {
      return
    }

    binding.messages.post {
      val view = _binding?.messages ?: return@post
      val overflow = view.computeVerticalScrollRange() -
          view.computeVerticalScrollOffset() -
          view.computeVerticalScrollExtent()
      if (overflow > 0) {
        view.scrollBy(0, overflow)
      }
    }
  }

  private fun onSendClicked() {
    if (viewModel.isBusy) {
      viewModel.cancel()
      return
    }

    val text = binding.input.text?.toString().orEmpty()
    if (text.isBlank()) {
      return
    }

    binding.input.setText("")
    viewModel.send(text)
  }

  private fun updateSendButton() {
    val busy = viewModel.isBusy
    binding.send.setIconResource(
      if (busy) {
        com.itsaky.androidide.resources.R.drawable.ic_stop_circle
      } else {
        com.itsaky.androidide.resources.R.drawable.ic_run
      }
    )
    binding.send.contentDescription = getString(
      if (busy) {
        com.itsaky.androidide.resources.R.string.action_ai_stop
      } else {
        com.itsaky.androidide.resources.R.string.action_ai_send
      }
    )
    binding.send.isEnabled = busy || !binding.input.text.isNullOrBlank()
  }
}
