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

package com.itsaky.androidide.lsp.cpp

import com.itsaky.androidide.ai.agent.DocumentSymbolEntry
import com.itsaky.androidide.ai.agent.SymbolIndex
import com.itsaky.androidide.ai.agent.SymbolLocation
import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.models.DefinitionParams
import com.itsaky.androidide.lsp.models.ReferenceParams
import com.itsaky.androidide.models.Location
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.progress.ICancelChecker
import com.itsaky.androidide.projects.IProjectManager
import java.io.File

/**
 * Exposes clangd's index to the AI assistant, which cannot depend on this module directly.
 */
object ClangdSymbolIndex : SymbolIndex {

  override suspend fun definition(file: File, line: Int, column: Int): List<SymbolLocation> {
    val server = server() ?: return emptyList()
    val result = server.findDefinition(
      DefinitionParams(file.toPath(), Position(line, column), ICancelChecker.Default())
    )
    return result.locations.map { it.toSymbolLocation() }
  }

  override suspend fun references(file: File, line: Int, column: Int): List<SymbolLocation> {
    val server = server() ?: return emptyList()
    val result = server.findReferences(
      ReferenceParams(file.toPath(), Position(line, column), true, ICancelChecker.Default())
    )
    return result.locations.map { it.toSymbolLocation() }
  }

  override suspend fun documentSymbols(file: File): List<DocumentSymbolEntry> {
    val server = server() ?: return emptyList()
    return server.documentSymbols(file.toPath()).symbols.map { symbol ->
      DocumentSymbolEntry(
        name = symbol.name,
        kind = symbol.kind,
        detail = symbol.detail,
        line = symbol.range.start.line + 1,
        container = symbol.container
      )
    }
  }

  private fun server() =
    ILanguageServerRegistry.getDefault().getServer(CppLanguageServer.SERVER_ID)

  private fun Location.toSymbolLocation(): SymbolLocation {
    val target = file.toFile()
    return SymbolLocation(
      path = relativize(target),
      line = range.start.line + 1,
      column = range.start.column + 1,
      preview = previewOf(target, range.start.line)
    )
  }

  private fun relativize(file: File): String {
    val root = runCatching { IProjectManager.getInstance().projectDir.canonicalPath }.getOrNull()
      ?: return file.absolutePath
    val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
    return if (path.startsWith("$root${File.separator}")) {
      path.substring(root.length + 1)
    } else {
      path
    }
  }

  private fun previewOf(file: File, line: Int): String {
    if (line < 0 || !file.isFile || file.length() > MAX_PREVIEW_FILE_SIZE) {
      return ""
    }
    return runCatching {
      file.useLines { lines -> lines.drop(line).firstOrNull()?.trim().orEmpty() }
    }.getOrDefault("")
  }

  private const val MAX_PREVIEW_FILE_SIZE = 2L * 1024 * 1024
}
