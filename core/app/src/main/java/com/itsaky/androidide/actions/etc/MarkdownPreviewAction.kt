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

package com.itsaky.androidide.actions.etc

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorActivityAction
import com.itsaky.androidide.resources.R

class MarkdownPreviewAction(context: Context, override val order: Int) : EditorActivityAction() {

  override val id: String = "ide.editor.markdown.preview"

  override var requiresUIThread: Boolean = true

  init {
    label = context.getString(R.string.action_markdown_preview)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_markdown_preview)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)

    val editorView = data.getActivity()?.getCurrentEditor()
    val isMarkdown = editorView?.isMarkdownFile() == true
    visible = isMarkdown
    enabled = isMarkdown

    val context = data.getActivity() ?: return
    label = context.getString(
      if (editorView?.isMarkdownPreviewVisible == true) {
        R.string.action_markdown_source
      } else {
        R.string.action_markdown_preview
      }
    )
  }

  override suspend fun execAction(data: ActionData): Boolean {
    val editorView = data.getActivity()?.getCurrentEditor() ?: return false
    editorView.setMarkdownPreviewVisible(!editorView.isMarkdownPreviewVisible)
    return true
  }
}
