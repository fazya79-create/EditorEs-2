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

package com.itsaky.androidide.preferences

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.itsaky.androidide.preferences.databinding.LayoutDialogTextInputBinding
import com.itsaky.androidide.projects.ClangFormat
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.tasks.executeAsync
import com.itsaky.androidide.utils.flashError
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
class CodeStylePreferencesScreen(
  override val key: String = "idepref_codestyle",
  override val title: Int = string.idepref_codestyle_title,
  override val summary: Int? = string.idepref_codestyle_summary,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceScreen() {

  init {
    addPreference(ClangFormatGroup())
  }
}

@Parcelize
private class ClangFormatGroup(
  override val key: String = "idepref_codestyle_clang_format",
  override val title: Int = string.idepref_codestyle_group,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {

  init {
    addPreference(BasedOnStylePreference())
    addPreference(IndentWidthPreference())
    addPreference(TabWidthPreference())
    addPreference(UseTabPreference())
    addPreference(ColumnLimitPreference())
  }
}

private fun currentProjectDir(): File? {
  val manager = IProjectManager.getInstance()
  if (!manager.projectInitialized) {
    return null
  }
  val dir = runCatching { manager.projectDir }.getOrNull() ?: return null
  return if (ClangFormat.isCppProject(dir)) dir else null
}

private fun readClangFormatValue(key: String, default: String): String {
  val dir = currentProjectDir() ?: return default
  return ClangFormat.readValue(dir, key) ?: default
}

private fun writeClangFormatValue(key: String, value: String, onDone: () -> Unit) {
  val dir = currentProjectDir() ?: run {
    flashError(string.idepref_codestyle_no_project)
    return
  }
  executeAsync(callable = {
    ClangFormat.ensureDefault(dir)
    ClangFormat.writeValue(dir, key, value)
  }) { onDone() }
}

private fun Preference.showCurrent(value: String) {
  summary = context.getString(string.idepref_backend_current, value)
}

@Parcelize
private class BasedOnStylePreference(
  override val key: String = "idepref_codestyle_based_on_style",
  override val title: Int = string.idepref_codestyle_based_on_style,
) : SingleChoicePreference() {

  @IgnoredOnParcel
  override val dialogCancellable = true

  override fun getEntries(preference: Preference): Array<PreferenceChoices.Entry> {
    val current = readClangFormatValue(
      ClangFormat.KEY_BASED_ON_STYLE, ClangFormat.DEFAULT_BASED_ON_STYLE)
    return ClangFormat.BASED_ON_STYLES.map { style ->
      PreferenceChoices.Entry(style, current.equals(style, ignoreCase = true), style)
    }.toTypedArray()
  }

  override fun onChoiceConfirmed(
    preference: Preference,
    entry: PreferenceChoices.Entry?,
    position: Int
  ) {
    super.onChoiceConfirmed(preference, entry, position)
    val style = (entry?.data as? String) ?: return
    writeClangFormatValue(ClangFormat.KEY_BASED_ON_STYLE, style) { preference.showCurrent(style) }
  }

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also {
      it.showCurrent(
        readClangFormatValue(ClangFormat.KEY_BASED_ON_STYLE, ClangFormat.DEFAULT_BASED_ON_STYLE))
    }
  }
}

@Parcelize
private class UseTabPreference(
  override val key: String = "idepref_codestyle_use_tab",
  override val title: Int = string.idepref_codestyle_use_tab,
) : SingleChoicePreference() {

  @IgnoredOnParcel
  override val dialogCancellable = true

  override fun getEntries(preference: Preference): Array<PreferenceChoices.Entry> {
    val current = readClangFormatValue(ClangFormat.KEY_USE_TAB, ClangFormat.DEFAULT_USE_TAB)
    return ClangFormat.USE_TAB_VALUES.map { value ->
      PreferenceChoices.Entry(value, current.equals(value, ignoreCase = true), value)
    }.toTypedArray()
  }

  override fun onChoiceConfirmed(
    preference: Preference,
    entry: PreferenceChoices.Entry?,
    position: Int
  ) {
    super.onChoiceConfirmed(preference, entry, position)
    val value = (entry?.data as? String) ?: return
    writeClangFormatValue(ClangFormat.KEY_USE_TAB, value) { preference.showCurrent(value) }
  }

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also {
      it.showCurrent(readClangFormatValue(ClangFormat.KEY_USE_TAB, ClangFormat.DEFAULT_USE_TAB))
    }
  }
}

private abstract class NumericClangFormatPreference : DialogPreference() {

  abstract val formatKey: String
  abstract val defaultValue: Int
  abstract val minValue: Int
  abstract val maxValue: Int

  @IgnoredOnParcel
  override val dialogCancellable = true

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also {
      it.showCurrent(readClangFormatValue(formatKey, defaultValue.toString()))
    }
  }

  override fun onConfigureDialog(preference: Preference, dialog: MaterialAlertDialogBuilder) {
    val binding = LayoutDialogTextInputBinding.inflate(LayoutInflater.from(preference.context))
    binding.name.editText!!.inputType = InputType.TYPE_CLASS_NUMBER
    binding.name.editText!!.setText(readClangFormatValue(formatKey, defaultValue.toString()))

    dialog.setView(binding.root)
    dialog.setPositiveButton(android.R.string.ok) { iface, _ ->
      iface.dismiss()
      val value = binding.name.editText!!.text.toString().trim().toIntOrNull()
      if (value == null || value < minValue || value > maxValue) {
        flashError(
          preference.context.getString(string.idepref_codestyle_invalid_number, minValue, maxValue))
        return@setPositiveButton
      }
      writeClangFormatValue(formatKey, value.toString()) { preference.showCurrent(value.toString()) }
    }
    dialog.setNegativeButton(android.R.string.cancel, null)
  }
}

@Parcelize
private class IndentWidthPreference(
  override val key: String = "idepref_codestyle_indent_width",
  override val title: Int = string.idepref_codestyle_indent_width,
  override val formatKey: String = ClangFormat.KEY_INDENT_WIDTH,
  override val defaultValue: Int = ClangFormat.DEFAULT_INDENT_WIDTH,
  override val minValue: Int = 1,
  override val maxValue: Int = 16,
) : NumericClangFormatPreference()

@Parcelize
private class TabWidthPreference(
  override val key: String = "idepref_codestyle_tab_width",
  override val title: Int = string.idepref_codestyle_tab_width,
  override val formatKey: String = ClangFormat.KEY_TAB_WIDTH,
  override val defaultValue: Int = ClangFormat.DEFAULT_TAB_WIDTH,
  override val minValue: Int = 1,
  override val maxValue: Int = 16,
) : NumericClangFormatPreference()

@Parcelize
private class ColumnLimitPreference(
  override val key: String = "idepref_codestyle_column_limit",
  override val title: Int = string.idepref_codestyle_column_limit,
  override val formatKey: String = ClangFormat.KEY_COLUMN_LIMIT,
  override val defaultValue: Int = ClangFormat.DEFAULT_COLUMN_LIMIT,
  override val minValue: Int = 0,
  override val maxValue: Int = 500,
) : NumericClangFormatPreference()
