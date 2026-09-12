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

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.itsaky.androidide.ai.databinding.FragmentAiChatBinding
import com.itsaky.androidide.ai.tools.ApprovalDecision
import com.itsaky.androidide.fragments.FragmentWithBinding
import com.itsaky.androidide.fragments.ImeInsetsAware

class AiChatFragment : FragmentWithBinding<FragmentAiChatBinding>(FragmentAiChatBinding::inflate),
  ImeInsetsAware {

  private val viewModel by viewModels<AiChatViewModel>(ownerProducer = { requireActivity() })
  private val adapter by lazy { AiChatAdapter(viewModel::toggleExpanded) }

  private var baseInputMargin = 0
  private var lastImeInset = 0

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
    binding.input.doAfterTextChanged { updateSendButton() }

    binding.approvalAccept.setOnClickListener {
      viewModel.resolveApproval(ApprovalDecision.APPROVED)
    }
    binding.approvalReject.setOnClickListener {
      viewModel.resolveApproval(ApprovalDecision.REJECTED)
    }

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

    viewModel.yoloMode.observe(viewLifecycleOwner) { enabled ->
      binding.yoloBanner.visibility = if (enabled == true) View.VISIBLE else View.GONE
    }

    viewModel.approvalRequest.observe(viewLifecycleOwner) { request ->
      binding.approvalBar.visibility = if (request == null) View.GONE else View.VISIBLE
      binding.approvalSummary.text = request?.summary.orEmpty()
    }

    viewModel.contextUsage.observe(viewLifecycleOwner) { usage ->
      if (usage == null) {
        binding.contextUsage.visibility = View.GONE
      } else {
        binding.contextUsage.visibility = View.VISIBLE
        binding.contextUsage.text = getString(
          com.itsaky.androidide.resources.R.string.msg_ai_context_usage,
          usage.percent
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshYoloMode()
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
