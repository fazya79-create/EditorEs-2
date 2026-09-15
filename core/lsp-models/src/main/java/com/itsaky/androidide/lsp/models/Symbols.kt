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

package com.itsaky.androidide.lsp.models

import com.itsaky.androidide.models.Range

/**
 * A symbol declared by a document, flattened from the language server's tree so the outline can
 * render it as a list while [container] still records the enclosing type or namespace.
 */
data class DocumentSymbol(
  val name: String,
  val kind: String,
  val detail: String,
  val range: Range,
  val container: String = ""
)

data class DocumentSymbolResult(val symbols: List<DocumentSymbol>) {

  companion object {

    @JvmField
    val EMPTY = DocumentSymbolResult(emptyList())
  }
}

data class HoverResult(val content: String) {

  val isEmpty: Boolean
    get() = content.isBlank()

  companion object {

    @JvmField
    val EMPTY = HoverResult("")
  }
}
