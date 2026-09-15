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
import com.itsaky.androidide.lsp.models.DocumentSymbol
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.flashError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoToSymbolAction(context: Context, override val order: Int) : EditorRelatedAction() {

  override val id: String = "ide.editor.code.text.goToSymbol"
  override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS
  override var requiresUIThread: Boolean = false

  init {
    label = context.getString(R.string.title_go_to_symbol)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_search_project)
  }

  override suspend fun execAction(data: ActionData): Any {
    val editor = data.getEditor() ?: return false
    val file = editor.file ?: return false
    val server = ILanguageServerRegistry.getDefault().getServer(CppLanguageServer.SERVER_ID)
      ?: return false

    val symbols = server.documentSymbols(file.toPath()).symbols
    if (symbols.isEmpty()) {
      withContext(Dispatchers.Main) {
        data.getActivity()?.flashError(R.string.msg_no_symbols)
      }
      return false
    }

    withContext(Dispatchers.Main) {
      val activity = data.getActivity() ?: return@withContext
      val labels = symbols.map(::labelOf).toTypedArray()
      DialogUtils.newMaterialDialogBuilder(activity)
        .setTitle(R.string.title_go_to_symbol)
        .setItems(labels) { dialog, which ->
          dialog.dismiss()
          val symbol = symbols.getOrNull(which) ?: return@setItems
          editor.setSelection(
            Position(symbol.range.start.line, symbol.range.start.column)
          )
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
    }

    return true
  }

  private fun labelOf(symbol: DocumentSymbol): String = buildString {
    append(symbol.range.start.line + 1).append("  ")
    if (symbol.container.isNotBlank()) {
      append(symbol.container).append("::")
    }
    append(symbol.name)
    if (symbol.detail.isNotBlank()) {
      append("  ").append(symbol.detail.take(MAX_DETAIL))
    }
  }

  companion object {

    private const val MAX_DETAIL = 60
  }
}
