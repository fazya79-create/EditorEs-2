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

package com.itsaky.androidide.ai.agent

import java.io.File

data class SymbolLocation(
  val path: String,
  val line: Int,
  val column: Int,
  val preview: String = ""
)

/**
 * Semantic lookups backed by the C/C++ language server. The language server lives in the
 * application module, which already depends on this one, so the implementation is installed at
 * runtime rather than linked against here.
 */
interface SymbolIndex {

  suspend fun definition(file: File, line: Int, column: Int): List<SymbolLocation>

  suspend fun references(file: File, line: Int, column: Int): List<SymbolLocation>

  suspend fun documentSymbols(file: File): List<DocumentSymbolEntry>
}

data class DocumentSymbolEntry(
  val name: String,
  val kind: String,
  val detail: String,
  val line: Int,
  val container: String = ""
)

object SymbolIndexRegistry {

  @Volatile
  private var index: SymbolIndex? = null

  fun install(value: SymbolIndex?) {
    index = value
  }

  fun current(): SymbolIndex? = index
}
