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
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.itsaky.androidide.ai.databinding.FragmentAiChatBinding
import com.itsaky.androidide.ai.tools.ApprovalDecision
import com.itsaky.androidide.fragments.FragmentWithBinding

class AiChatFragment : FragmentWithBinding<FragmentAiChatBinding>(FragmentAiChatBinding::inflate) {

  private val viewModel by viewModels<AiChatViewModel>()
  private val adapter = AiChatAdapter()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.messages.layoutManager = LinearLayoutManager(requireContext()).apply {
      stackFromEnd = true
    }
    binding.messages.adapter = adapter

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
      adapter.submitList(entries) {
        if (entries.isNotEmpty()) {
          binding.messages.scrollToPosition(entries.lastIndex)
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
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshYoloMode()
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
