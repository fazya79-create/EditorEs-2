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

package com.itsaky.androidide.editor.ui

/**
 * Popup window used to show the language server's description of the symbol at the cursor.
 */
class HoverWindow(editor: IDEEditor) : BaseEditorWindow(editor) {

  fun showContent(content: String) {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) {
      if (isShowing) {
        dismiss()
      }
      return
    }

    text.text = if (trimmed.length > MAX_CHARS) {
      trimmed.take(MAX_CHARS) + "\u2026"
    } else {
      trimmed
    }
    displayWindow()
  }

  companion object {

    private const val MAX_CHARS = 2000
  }
}
