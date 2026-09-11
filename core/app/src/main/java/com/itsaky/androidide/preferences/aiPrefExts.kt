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
import android.widget.FrameLayout
import androidx.preference.Preference
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.itsaky.androidide.ai.prefs.AiPreferences
import com.itsaky.androidide.ai.prefs.SecretStore
import com.itsaky.androidide.ai.provider.ProviderKind
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.utils.DialogUtils
import kotlinx.parcelize.Parcelize

@Parcelize
class AiPreferencesScreen(
  override val key: String = "idepref_ai",
  override val title: Int = string.idepref_ai_title,
  override val summary: Int? = string.idepref_ai_summary,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceScreen() {

  init {
    addPreference(AiProviderGroup())
    addPreference(AiSafetyGroup())
  }
}

@Parcelize
private class AiProviderGroup(
  override val key: String = "idepref_ai_provider_group",
  override val title: Int = string.idepref_ai_provider_group,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {

  init {
    addPreference(AiProviderPreference())
    addPreference(OpenAiApiKeyPreference())
    addPreference(OpenAiModelPreference())
    addPreference(OpenAiBaseUrlPreference())
    addPreference(AnthropicApiKeyPreference())
    addPreference(AnthropicModelPreference())
    addPreference(AnthropicBaseUrlPreference())
  }
}

@Parcelize
private class AiSafetyGroup(
  override val key: String = "idepref_ai_safety",
  override val title: Int = string.idepref_ai_safety_group,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {

  init {
    addPreference(AiYoloPreference())
    addPreference(AiShellTimeoutPreference())
  }
}

@Parcelize
private class AiProviderPreference(
  override val key: String = AiPreferences.PROVIDER,
  override val title: Int = string.idepref_ai_provider,
) : SingleChoicePreference() {

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updateSummary(it) }
  }

  override fun getEntries(preference: Preference): Array<PreferenceChoices.Entry> {
    val current = AiPreferences.providerIndex
    return arrayOf(
      PreferenceChoices.Entry(
        preference.context.getString(string.idepref_ai_provider_openai),
        current == AiPreferences.PROVIDER_OPENAI,
        AiPreferences.PROVIDER_OPENAI
      ),
      PreferenceChoices.Entry(
        preference.context.getString(string.idepref_ai_provider_anthropic),
        current == AiPreferences.PROVIDER_ANTHROPIC,
        AiPreferences.PROVIDER_ANTHROPIC
      )
    )
  }

  override fun onChoiceConfirmed(
    preference: Preference,
    entry: PreferenceChoices.Entry?,
    position: Int
  ) {
    if (position >= 0) {
      AiPreferences.providerIndex = position
      updateSummary(preference)
    }
  }

  private fun updateSummary(preference: Preference) {
    preference.summary = preference.context.getString(
      if (AiPreferences.providerIndex == AiPreferences.PROVIDER_ANTHROPIC) {
        string.idepref_ai_provider_anthropic
      } else {
        string.idepref_ai_provider_openai
      }
    )
  }
}

private abstract class ApiKeyPreference : SimplePreference() {

  abstract val providerKind: ProviderKind

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updateSummary(it) }
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    val store = SecretStore(context.applicationContext)
    val secretKey = AiPreferences.apiKeyPrefKey(providerKind)

    val input = TextInputEditText(context).apply {
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
      setText(store.get(secretKey))
    }

    DialogUtils.newMaterialDialogBuilder(context)
      .setTitle(title)
      .setView(wrap(context, input))
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok) { dialog, _ ->
        store.put(secretKey, input.text?.toString()?.trim().orEmpty())
        updateSummary(preference)
        dialog.dismiss()
      }
      .show()

    return true
  }

  private fun updateSummary(preference: Preference) {
    val store = SecretStore(preference.context.applicationContext)
    preference.summary = preference.context.getString(
      if (store.has(AiPreferences.apiKeyPrefKey(providerKind))) {
        string.idepref_ai_key_set
      } else {
        string.idepref_ai_key_missing
      }
    )
  }
}

private abstract class TextValuePreference : SimplePreference() {

  abstract fun getValue(): String

  abstract fun setValue(value: String)

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { it.summary = getValue() }
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    val input = TextInputEditText(context).apply {
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
      setText(getValue())
    }

    DialogUtils.newMaterialDialogBuilder(context)
      .setTitle(title)
      .setView(wrap(context, input))
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok) { dialog, _ ->
        setValue(input.text?.toString()?.trim().orEmpty())
        preference.summary = getValue()
        dialog.dismiss()
      }
      .show()

    return true
  }
}

private fun wrap(context: Context, input: TextInputEditText): TextInputLayout {
  return TextInputLayout(context).apply {
    val padding = (24 * context.resources.displayMetrics.density).toInt()
    setPadding(padding, padding / 2, padding, 0)
    layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.WRAP_CONTENT
    )
    addView(input)
  }
}

@Parcelize
private class OpenAiApiKeyPreference(
  override val key: String = AiPreferences.SECRET_OPENAI_KEY,
  override val title: Int = string.idepref_ai_openai_key,
) : ApiKeyPreference() {

  override val providerKind: ProviderKind
    get() = ProviderKind.OPENAI
}

@Parcelize
private class AnthropicApiKeyPreference(
  override val key: String = AiPreferences.SECRET_ANTHROPIC_KEY,
  override val title: Int = string.idepref_ai_anthropic_key,
) : ApiKeyPreference() {

  override val providerKind: ProviderKind
    get() = ProviderKind.ANTHROPIC
}

@Parcelize
private class OpenAiModelPreference(
  override val key: String = AiPreferences.OPENAI_MODEL,
  override val title: Int = string.idepref_ai_openai_model,
) : TextValuePreference() {

  override fun getValue(): String = AiPreferences.openAiModel

  override fun setValue(value: String) {
    AiPreferences.openAiModel = value
  }
}

@Parcelize
private class OpenAiBaseUrlPreference(
  override val key: String = AiPreferences.OPENAI_BASE_URL,
  override val title: Int = string.idepref_ai_openai_base_url,
) : TextValuePreference() {

  override fun getValue(): String = AiPreferences.openAiBaseUrl

  override fun setValue(value: String) {
    AiPreferences.openAiBaseUrl = value
  }
}

@Parcelize
private class AnthropicModelPreference(
  override val key: String = AiPreferences.ANTHROPIC_MODEL,
  override val title: Int = string.idepref_ai_anthropic_model,
) : TextValuePreference() {

  override fun getValue(): String = AiPreferences.anthropicModel

  override fun setValue(value: String) {
    AiPreferences.anthropicModel = value
  }
}

@Parcelize
private class AnthropicBaseUrlPreference(
  override val key: String = AiPreferences.ANTHROPIC_BASE_URL,
  override val title: Int = string.idepref_ai_anthropic_base_url,
) : TextValuePreference() {

  override fun getValue(): String = AiPreferences.anthropicBaseUrl

  override fun setValue(value: String) {
    AiPreferences.anthropicBaseUrl = value
  }
}

@Parcelize
private class AiYoloPreference(
  override val key: String = AiPreferences.YOLO_MODE,
  override val title: Int = string.idepref_ai_yolo,
) : SwitchPreference(setValue = { AiPreferences.yoloMode = it }, getValue = { AiPreferences.yoloMode }) {

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updateSummary(it) }
  }

  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    return super.onPreferenceChanged(preference, newValue).also { updateSummary(preference) }
  }

  private fun updateSummary(preference: Preference) {
    preference.summary = preference.context.getString(
      if (AiPreferences.yoloMode) {
        string.idepref_ai_yolo_on
      } else {
        string.idepref_ai_yolo_off
      }
    )
  }
}

@Parcelize
private class AiShellTimeoutPreference(
  override val key: String = AiPreferences.SHELL_TIMEOUT,
  override val title: Int = string.idepref_ai_shell_timeout,
) : SimplePreference() {

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updateSummary(it) }
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    val input = TextInputEditText(context).apply {
      inputType = InputType.TYPE_CLASS_NUMBER
      setText(AiPreferences.shellTimeoutSeconds.toString())
    }

    DialogUtils.newMaterialDialogBuilder(context)
      .setTitle(title)
      .setView(wrap(context, input))
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok) { dialog, _ ->
        val seconds = input.text?.toString()?.trim()?.toIntOrNull()
        if (seconds != null && seconds > 0) {
          AiPreferences.shellTimeoutSeconds = seconds
          updateSummary(preference)
        }
        dialog.dismiss()
      }
      .show()

    return true
  }

  private fun updateSummary(preference: Preference) {
    preference.summary = preference.context.getString(
      string.idepref_ai_shell_timeout_current,
      AiPreferences.shellTimeoutSeconds
    )
  }
}
