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

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import com.itsaky.androidide.utils.resolveAttr
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin

object MarkdownRenderer {

  private const val TABLE_BORDER_DP = 1f
  private const val TABLE_PADDING_DP = 6f

  private val HEADING_SIZES = floatArrayOf(1.6f, 1.4f, 1.25f, 1.15f, 1.05f, 1f)

  @Volatile
  private var markwon: Markwon? = null

  fun render(view: TextView, markdown: String) {
    instance(view.context).setMarkdown(view, markdown)
  }

  private fun instance(context: Context): Markwon {
    markwon?.let { return it }
    return synchronized(this) {
      markwon ?: build(context.applicationContext).also { markwon = it }
    }
  }

  private fun build(context: Context): Markwon {
    val codeBackground =
      context.resolveAttr(com.google.android.material.R.attr.colorSurfaceContainerHighest)
    val codeText = context.resolveAttr(com.google.android.material.R.attr.colorOnSurface)
    val linkColor = context.resolveAttr(com.google.android.material.R.attr.colorPrimary)
    val ruleColor = context.resolveAttr(com.google.android.material.R.attr.colorOutlineVariant)
    val headerBackground =
      context.resolveAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh)
    val evenRowBackground =
      context.resolveAttr(com.google.android.material.R.attr.colorSurfaceContainer)

    val density = context.resources.displayMetrics.density
    val borderWidth = (density * TABLE_BORDER_DP).toInt().coerceAtLeast(1)
    val cellPadding = (density * TABLE_PADDING_DP).toInt()

    return Markwon.builder(context)
      .usePlugin(StrikethroughPlugin.create())
      .usePlugin(TaskListPlugin.create(context))
      .usePlugin(LinkifyPlugin.create())
      .usePlugin(
        TablePlugin.create(
          TableTheme.Builder()
            .tableBorderColor(ruleColor)
            .tableBorderWidth(borderWidth)
            .tableCellPadding(cellPadding)
            .tableHeaderRowBackgroundColor(headerBackground)
            .tableEvenRowBackgroundColor(evenRowBackground)
            .tableOddRowBackgroundColor(0)
            .build()
        )
      )
      .usePlugin(object : AbstractMarkwonPlugin() {
        override fun configureTheme(builder: MarkwonTheme.Builder) {
          builder
            .headingBreakHeight(0)
            .headingTypeface(Typeface.DEFAULT_BOLD)
            .headingTextSizeMultipliers(HEADING_SIZES)
            .codeBackgroundColor(codeBackground)
            .codeBlockBackgroundColor(codeBackground)
            .codeTextColor(codeText)
            .codeBlockTextColor(codeText)
            .codeTypeface(Typeface.MONOSPACE)
            .codeBlockTypeface(Typeface.MONOSPACE)
            .linkColor(linkColor)
            .thematicBreakColor(ruleColor)
            .blockQuoteColor(ruleColor)
        }
      })
      .build()
  }
}
