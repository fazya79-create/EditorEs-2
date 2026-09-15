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

package com.itsaky.androidide.actions.editor

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.cpp.CppLanguageServer
import com.itsaky.androidide.lsp.models.CodeActionItem
import com.itsaky.androidide.lsp.models.CodeActionParams
import com.itsaky.androidide.lsp.models.DocumentChange
import com.itsaky.androidide.lsp.models.TextEdit
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.flashError
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class QuickFixAction(context: Context, override val order: Int) : EditorRelatedAction() {

  override val id: String = "ide.editor.code.text.quickFix"
  override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS
  override var requiresUIThread: Boolean = false

  init {
    label = context.getString(R.string.title_quick_fix)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_bulb)
  }

  override suspend fun execAction(data: ActionData): Any {
    val editor = data.getEditor() ?: return false
    val file = editor.file ?: return false
    val server = ILanguageServerRegistry.getDefault().getServer(CppLanguageServer.SERVER_ID)
      ?: return false

    val cursor = editor.cursor ?: return false
    val range = Range(
      Position(cursor.leftLine, cursor.leftColumn),
      Position(cursor.rightLine, cursor.rightColumn)
    )

    val actions = server.codeActions(CodeActionParams(file.toPath(), range))
    if (actions.isEmpty()) {
      withContext(Dispatchers.Main) {
        data.getActivity()?.flashError(R.string.msg_no_quick_fixes)
      }
      return false
    }

    withContext(Dispatchers.Main) {
      val activity = data.getActivity() ?: return@withContext
      DialogUtils.newMaterialDialogBuilder(activity)
        .setTitle(R.string.title_quick_fix)
        .setItems(actions.map { it.title }.toTypedArray()) { dialog, which ->
          dialog.dismiss()
          actions.getOrNull(which)?.let { action -> apply(data, action) }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
    }

    return true
  }

  private fun apply(data: ActionData, action: CodeActionItem) {
    val activity = data.getActivity() ?: return
    var applied = 0

    action.changes.forEach { change ->
      val path = change.file ?: return@forEach
      if (applyTo(data, change, path.toFile())) {
        applied++
      }
    }

    if (applied == 0) {
      activity.flashError(R.string.msg_cannot_perform_fix)
    }
  }

  private fun applyTo(data: ActionData, change: DocumentChange, file: File): Boolean {
    val activity = data.getActivity() ?: return false
    val target = activity.openFile(file)?.editor ?: return false

    return runCatching {
      orderedEdits(change.edits).forEach { edit ->
        target.text.replace(
          edit.range.start.line,
          edit.range.start.column,
          edit.range.end.line,
          edit.range.end.column,
          edit.newText
        )
      }
      true
    }.getOrElse { error ->
      log.error("Failed to apply quick fix to {}", file, error)
      false
    }
  }

  companion object {

    private val log = LoggerFactory.getLogger(QuickFixAction::class.java)

    /**
     * Later edits are expressed against the text as the server saw it, so applying from the bottom
     * up keeps the ranges of the edits above them valid.
     */
    @JvmStatic
    fun orderedEdits(edits: List<TextEdit>): List<TextEdit> = edits.sortedWith(
      compareByDescending<TextEdit> { it.range.start.line }
        .thenByDescending { it.range.start.column }
    )
  }
}
