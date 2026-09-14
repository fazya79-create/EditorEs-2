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

package com.itsaky.androidide.ai.skills

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

object SkillTreeCopier {

  private const val MAX_FILES = 400
  private const val MAX_DEPTH = 5
  private const val MAX_BYTES = 16L * 1024 * 1024

  fun copyTree(context: Context, tree: Uri, into: File): Int {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
      ?: return 0
    val counter = intArrayOf(0)
    val bytes = longArrayOf(0)
    into.mkdirs()
    copy(context, tree, documentId, into, 0, counter, bytes)
    return counter[0]
  }

  private fun copy(
    context: Context,
    tree: Uri,
    documentId: String,
    target: File,
    depth: Int,
    counter: IntArray,
    bytes: LongArray
  ) {
    if (depth > MAX_DEPTH || counter[0] >= MAX_FILES) {
      return
    }

    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
    val projection = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE
    )

    context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
      while (cursor.moveToNext()) {
        if (counter[0] >= MAX_FILES) {
          return
        }

        val childId = cursor.getString(0)
        val name = cursor.getString(1)
        val mime = cursor.getString(2)

        if (name.isNullOrBlank() || name == "." || name == ".." || name.contains(File.separator)) {
          continue
        }

        val child = File(target, name)
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
          child.mkdirs()
          copy(context, tree, childId, child, depth + 1, counter, bytes)
          continue
        }

        val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
        if (bytes[0] + size > MAX_BYTES) {
          return
        }

        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId)
        runCatching {
          context.contentResolver.openInputStream(uri)?.use { input ->
            child.parentFile?.mkdirs()
            child.outputStream().use { output -> bytes[0] += input.copyTo(output) }
          }
        }
        counter[0]++
      }
    }
  }
}
