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
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.preference.Preference
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.itsaky.androidide.adapters.AiModelAdapter
import com.itsaky.androidide.ai.model.ThinkingLevel
import com.itsaky.androidide.ai.prefs.AiPreferences
import com.itsaky.androidide.ai.prefs.SecretStore
import com.itsaky.androidide.ai.provider.ModelCatalog
import com.itsaky.androidide.ai.provider.ModelFilter
import com.itsaky.androidide.ai.provider.ModelInfo
import com.itsaky.androidide.ai.provider.ProviderKind
import com.itsaky.androidide.ai.search.SearchProviderKind
import com.itsaky.androidide.databinding.LayoutAiModelPickerBinding
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    when (AiPreferences.providerKind()) {
      ProviderKind.ANTHROPIC -> addPreference(AnthropicGroup())
      ProviderKind.GOOGLE -> addPreference(GoogleGroup())
      ProviderKind.OPENAI -> addPreference(OpenAiGroup())
    }

    addPreference(AiSafetyGroup())
    addPreference(AiSearchGroup())
    addPreference(AiContextGroup())
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
  }
}

@Parcelize
private class OpenAiGroup(
  override val key: String = "idepref_ai_openai",
  override val title: Int = string.idepref_ai_provider_openai,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {

  init {
    addPreference(OpenAiApiKeyPreference())
    addPreference(OpenAiModelPreference())
    addPreference(OpenAiThinkingPreference())
    addPreference(OpenAiBaseUrlPreference())
  }
}

@Parcelize
private class AnthropicGroup(
  override val key: String = "idepref_ai_anthropic",
  override val title: Int = string.idepref_ai_provider_anthropic,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {

  init {
    addPreference(AnthropicApiKeyPreference())
    addPreference(AnthropicModelPreference())
    addPreference(AnthropicThinkingPreference())
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
    addPreference(AiMaxToolRoundsPreference())
  }
}

@Parcelize
private class GoogleGroup(
  override val key: String = "idepref_ai_google",
  override val title: Int = string.idepref_ai_provider_google,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {

  init {
    addPreference(GoogleApiKeyPreference())
    addPreference(GoogleModelPreference())
    addPreference(GoogleThinkingPreference())
    addPreference(GoogleBaseUrlPreference())
  }
}

@Parcelize
private class AiSearchGroup(
  override val key: String = "idepref_ai_search",
  override val title: Int = string.idepref_ai_search_group,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {

  init {
    addPreference(AiSearchProviderPreference())
    addPreference(AiSearchApiKeyPreference())
    addPreference(AiSearchResultLimitPreference())
  }
}

@Parcelize
private class AiContextGroup(
  override val key: String = "idepref_ai_context",
  override val title: Int = string.idepref_ai_context_group,
  override val children: List<IPreference> = mutableListOf(),
) : IPreferenceGroup() {

  init {
    addPreference(AiAutoCompactPreference())
    addPreference(AiCompactThresholdPreference())
    addPreference(AiContextWindowPreference())
    addPreference(AiPromptCachingPreference())
  }
}

@Parcelize
private class AiProviderPreference(
  override val key: String = AiPreferences.PROVIDER,
  override val title: Int = string.idepref_ai_provider,
  override val summary: Int? = string.idepref_ai_provider_summary,
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
      ),
      PreferenceChoices.Entry(
        preference.context.getString(string.idepref_ai_provider_google),
        current == AiPreferences.PROVIDER_GOOGLE,
        AiPreferences.PROVIDER_GOOGLE
      )
    )
  }

  override fun onChoiceConfirmed(
    preference: Preference,
    entry: PreferenceChoices.Entry?,
    position: Int
  ) {
    if (position < 0 || position == AiPreferences.providerIndex) {
      return
    }

    AiPreferences.providerIndex = position
    updateSummary(preference)
    preference.summary = preference.context.getString(string.idepref_ai_provider_changed)
  }

  private fun updateSummary(preference: Preference) {
    preference.summary = preference.context.getString(
      when (AiPreferences.providerIndex) {
        AiPreferences.PROVIDER_ANTHROPIC -> string.idepref_ai_provider_anthropic
        AiPreferences.PROVIDER_GOOGLE -> string.idepref_ai_provider_google
        else -> string.idepref_ai_provider_openai
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

private abstract class ModelPreference : SimplePreference() {

  abstract val providerKind: ProviderKind

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { it.summary = AiPreferences.modelOf(providerKind) }
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    val config = AiPreferences.providerConfig(context.applicationContext, providerKind)

    if (config.apiKey.isBlank()) {
      flashError(context.getString(string.msg_ai_models_need_key))
      showManualEntry(preference)
      return true
    }

    preference.summary = context.getString(string.msg_ai_models_loading)

    CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
      val models = runCatching { ModelCatalog.fetch(config) }
      preference.summary = AiPreferences.modelOf(providerKind)

      models.onFailure { err ->
        flashError(
          context.getString(string.msg_ai_models_failed, err.message ?: err.javaClass.simpleName)
        )
        showManualEntry(preference)
      }

      models.onSuccess { available ->
        if (available.isEmpty()) {
          flashError(context.getString(string.msg_ai_models_empty))
          showManualEntry(preference)
        } else {
          showPicker(preference, available)
        }
      }
    }

    return true
  }

  private fun showPicker(preference: Preference, models: List<ModelInfo>) {
    val context = preference.context
    val binding = LayoutAiModelPickerBinding.inflate(LayoutInflater.from(context))

    lateinit var dialog: AlertDialog
    val adapter = AiModelAdapter { model ->
      selectModel(preference, model)
      dialog.dismiss()
    }.apply { selectedModel = AiPreferences.modelOf(providerKind) }

    binding.models.layoutManager = LinearLayoutManager(context)
    binding.models.adapter = adapter

    fun applyQuery(query: String) {
      val matches = ModelFilter.filter(models, query)
      adapter.submitList(matches)
      binding.models.isVisible = matches.isNotEmpty()
      binding.modelsEmpty.isVisible = matches.isEmpty()
    }

    applyQuery("")
    binding.searchInput.doAfterTextChanged { text -> applyQuery(text?.toString().orEmpty()) }

    dialog = DialogUtils.newMaterialDialogBuilder(context)
      .setTitle(title)
      .setView(binding.root)
      .setNeutralButton(string.idepref_ai_model_manual) { current, _ ->
        current.dismiss()
        showManualEntry(preference)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun selectModel(preference: Preference, model: ModelInfo) {
    AiPreferences.setModelOf(providerKind, model.id)
    preference.summary = model.id

    val window = AiPreferences.applyModelContextWindow(
      kind = providerKind,
      model = model.id,
      reported = model.contextWindow
    )
    if (window > 0) {
      flashSuccess(
        preference.context.getString(string.msg_ai_context_window_detected, model.id, window)
      )
    }
  }

  private fun showManualEntry(preference: Preference) {
    val context = preference.context
    val input = TextInputEditText(context).apply {
      inputType = InputType.TYPE_CLASS_TEXT
      setText(AiPreferences.modelOf(providerKind))
    }

    DialogUtils.newMaterialDialogBuilder(context)
      .setTitle(title)
      .setView(wrap(context, input))
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok) { dialog, _ ->
        val model = input.text?.toString()?.trim().orEmpty()
        if (model.isNotEmpty()) {
          selectModel(preference, ModelInfo(model))
        }
        dialog.dismiss()
      }
      .show()
  }
}

private abstract class ThinkingPreference : SingleChoicePreference() {

  abstract val providerKind: ProviderKind

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updateSummary(it) }
  }

  override fun getEntries(preference: Preference): Array<PreferenceChoices.Entry> {
    val current = AiPreferences.thinkingLevelOf(providerKind)
    return ThinkingLevel.entries.map { level ->
      PreferenceChoices.Entry(
        preference.context.getString(labelOf(level)),
        level == current,
        level.ordinal
      )
    }.toTypedArray()
  }

  override fun onChoiceConfirmed(
    preference: Preference,
    entry: PreferenceChoices.Entry?,
    position: Int
  ) {
    val level = ThinkingLevel.entries.getOrNull(position) ?: return
    AiPreferences.setThinkingLevelOf(providerKind, level)
    updateSummary(preference)
  }

  private fun updateSummary(preference: Preference) {
    preference.summary =
      preference.context.getString(labelOf(AiPreferences.thinkingLevelOf(providerKind)))
  }

  private fun labelOf(level: ThinkingLevel): Int = when (level) {
    ThinkingLevel.OFF -> string.idepref_ai_thinking_off
    ThinkingLevel.LOW -> string.idepref_ai_thinking_low
    ThinkingLevel.MEDIUM -> string.idepref_ai_thinking_medium
    ThinkingLevel.HIGH -> string.idepref_ai_thinking_high
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
  override val title: Int = string.idepref_ai_model,
) : ModelPreference() {

  override val providerKind: ProviderKind
    get() = ProviderKind.OPENAI
}

@Parcelize
private class AnthropicModelPreference(
  override val key: String = AiPreferences.ANTHROPIC_MODEL,
  override val title: Int = string.idepref_ai_model,
) : ModelPreference() {

  override val providerKind: ProviderKind
    get() = ProviderKind.ANTHROPIC
}

@Parcelize
private class OpenAiThinkingPreference(
  override val key: String = AiPreferences.OPENAI_THINKING,
  override val title: Int = string.idepref_ai_thinking,
) : ThinkingPreference() {

  override val providerKind: ProviderKind
    get() = ProviderKind.OPENAI
}

@Parcelize
private class AnthropicThinkingPreference(
  override val key: String = AiPreferences.ANTHROPIC_THINKING,
  override val title: Int = string.idepref_ai_thinking,
) : ThinkingPreference() {

  override val providerKind: ProviderKind
    get() = ProviderKind.ANTHROPIC
}

@Parcelize
private class OpenAiBaseUrlPreference(
  override val key: String = AiPreferences.OPENAI_BASE_URL,
  override val title: Int = string.idepref_ai_base_url,
) : TextValuePreference() {

  override fun getValue(): String = AiPreferences.openAiBaseUrl

  override fun setValue(value: String) {
    AiPreferences.openAiBaseUrl = value
  }
}

@Parcelize
private class AnthropicBaseUrlPreference(
  override val key: String = AiPreferences.ANTHROPIC_BASE_URL,
  override val title: Int = string.idepref_ai_base_url,
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
) : SwitchPreference(
  setValue = { AiPreferences.yoloMode = it },
  getValue = { AiPreferences.yoloMode }
) {

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

private abstract class NumberPreference : SimplePreference() {

  abstract fun getValue(): Int

  abstract fun setValue(value: Int)

  abstract fun summaryOf(context: Context): String

  open fun isValid(value: Int): Boolean = value > 0

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { it.summary = summaryOf(context) }
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    val input = TextInputEditText(context).apply {
      inputType = InputType.TYPE_CLASS_NUMBER
      setText(getValue().toString())
    }

    DialogUtils.newMaterialDialogBuilder(context)
      .setTitle(title)
      .setView(wrap(context, input))
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok) { dialog, _ ->
        val value = input.text?.toString()?.trim()?.toIntOrNull()
        if (value != null && isValid(value)) {
          setValue(value)
          preference.summary = summaryOf(context)
        }
        dialog.dismiss()
      }
      .show()

    return true
  }
}

@Parcelize
private class AiMaxToolRoundsPreference(
  override val key: String = AiPreferences.MAX_TOOL_ROUNDS,
  override val title: Int = string.idepref_ai_max_tool_rounds,
) : NumberPreference() {

  override fun getValue(): Int = AiPreferences.maxToolRounds

  override fun setValue(value: Int) {
    AiPreferences.maxToolRounds = value
  }

  override fun isValid(value: Int): Boolean = value >= 0

  override fun summaryOf(context: Context): String {
    val rounds = AiPreferences.maxToolRounds
    return if (rounds == AiPreferences.UNLIMITED_TOOL_ROUNDS) {
      context.getString(string.idepref_ai_max_tool_rounds_unlimited)
    } else {
      context.getString(string.idepref_ai_max_tool_rounds_current, rounds)
    }
  }
}

@Parcelize
private class AiCompactThresholdPreference(
  override val key: String = AiPreferences.COMPACT_THRESHOLD,
  override val title: Int = string.idepref_ai_compact_threshold,
) : NumberPreference() {

  override fun getValue(): Int = AiPreferences.compactThresholdPercent

  override fun setValue(value: Int) {
    AiPreferences.compactThresholdPercent = value
  }

  override fun isValid(value: Int): Boolean = value in 10..99

  override fun summaryOf(context: Context): String = context.getString(
    string.idepref_ai_compact_threshold_current,
    AiPreferences.compactThresholdPercent
  )
}

@Parcelize
private class AiContextWindowPreference(
  override val key: String = AiPreferences.OPENAI_CONTEXT_WINDOW,
  override val title: Int = string.idepref_ai_context_window,
) : NumberPreference() {

  override fun getValue(): Int = AiPreferences.contextWindowOf(AiPreferences.providerKind())

  override fun setValue(value: Int) {
    AiPreferences.setContextWindowOf(AiPreferences.providerKind(), value)
  }

  override fun isValid(value: Int): Boolean = value >= 1000

  override fun summaryOf(context: Context): String = context.getString(
    string.idepref_ai_context_window_current,
    AiPreferences.contextWindowOf(AiPreferences.providerKind())
  )
}

@Parcelize
private class AiAutoCompactPreference(
  override val key: String = AiPreferences.AUTO_COMPACT,
  override val title: Int = string.idepref_ai_auto_compact,
) : SwitchPreference(
  setValue = { AiPreferences.autoCompact = it },
  getValue = { AiPreferences.autoCompact }
) {

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updateSummary(it) }
  }

  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    return super.onPreferenceChanged(preference, newValue).also { updateSummary(preference) }
  }

  private fun updateSummary(preference: Preference) {
    preference.summary = preference.context.getString(
      if (AiPreferences.autoCompact) {
        string.idepref_ai_auto_compact_on
      } else {
        string.idepref_ai_auto_compact_off
      }
    )
  }
}

@Parcelize
private class GoogleApiKeyPreference(
  override val key: String = AiPreferences.SECRET_GOOGLE_KEY,
  override val title: Int = string.idepref_ai_google_key,
) : ApiKeyPreference() {

  override val providerKind: ProviderKind
    get() = ProviderKind.GOOGLE
}

@Parcelize
private class GoogleModelPreference(
  override val key: String = AiPreferences.GOOGLE_MODEL,
  override val title: Int = string.idepref_ai_model,
) : ModelPreference() {

  override val providerKind: ProviderKind
    get() = ProviderKind.GOOGLE
}

@Parcelize
private class GoogleThinkingPreference(
  override val key: String = AiPreferences.GOOGLE_THINKING,
  override val title: Int = string.idepref_ai_thinking,
) : ThinkingPreference() {

  override val providerKind: ProviderKind
    get() = ProviderKind.GOOGLE
}

@Parcelize
private class GoogleBaseUrlPreference(
  override val key: String = AiPreferences.GOOGLE_BASE_URL,
  override val title: Int = string.idepref_ai_base_url,
) : TextValuePreference() {

  override fun getValue(): String = AiPreferences.googleBaseUrl

  override fun setValue(value: String) {
    AiPreferences.googleBaseUrl = value
  }
}

@Parcelize
private class AiPromptCachingPreference(
  override val key: String = AiPreferences.PROMPT_CACHING,
  override val title: Int = string.idepref_ai_prompt_caching,
) : SwitchPreference(
  setValue = { AiPreferences.promptCaching = it },
  getValue = { AiPreferences.promptCaching }
) {

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updateSummary(it) }
  }

  override fun onPreferenceChanged(preference: Preference, newValue: Any?): Boolean {
    return super.onPreferenceChanged(preference, newValue).also { updateSummary(preference) }
  }

  private fun updateSummary(preference: Preference) {
    preference.summary = preference.context.getString(
      if (AiPreferences.promptCaching) {
        string.idepref_ai_prompt_caching_on
      } else {
        string.idepref_ai_prompt_caching_off
      }
    )
  }
}

@Parcelize
private class AiSearchProviderPreference(
  override val key: String = AiPreferences.SEARCH_PROVIDER,
  override val title: Int = string.idepref_ai_search_provider,
) : SingleChoicePreference() {

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updateSummary(it) }
  }

  override fun getEntries(preference: Preference): Array<PreferenceChoices.Entry> {
    val current = AiPreferences.searchProviderKind()
    return SearchProviderKind.entries.map { kind ->
      PreferenceChoices.Entry(kind.label, kind == current, kind.ordinal)
    }.toTypedArray()
  }

  override fun onChoiceConfirmed(
    preference: Preference,
    entry: PreferenceChoices.Entry?,
    position: Int
  ) {
    if (position < 0 || position >= SearchProviderKind.entries.size) {
      return
    }
    AiPreferences.searchProviderIndex = position
    updateSummary(preference)
  }

  private fun updateSummary(preference: Preference) {
    preference.summary = AiPreferences.searchProviderKind().label
  }
}

@Parcelize
private class AiSearchApiKeyPreference(
  override val key: String = "idepref_ai_search_key",
  override val title: Int = string.idepref_ai_search_key,
) : SimplePreference() {

  override fun onCreatePreference(context: Context): Preference {
    return super.onCreatePreference(context).also { updateSummary(it) }
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    val context = preference.context
    val store = SecretStore(context.applicationContext)
    val secretKey = AiPreferences.searchApiKeyPrefKey(AiPreferences.searchProviderKind())

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
    val secretKey = AiPreferences.searchApiKeyPrefKey(AiPreferences.searchProviderKind())
    preference.summary = preference.context.getString(
      if (store.has(secretKey)) string.idepref_ai_key_set else string.idepref_ai_key_missing
    )
  }
}

@Parcelize
private class AiSearchResultLimitPreference(
  override val key: String = AiPreferences.SEARCH_RESULT_LIMIT,
  override val title: Int = string.idepref_ai_search_result_limit,
) : NumberPreference() {

  override fun getValue(): Int = AiPreferences.searchResultLimit

  override fun setValue(value: Int) {
    AiPreferences.searchResultLimit = value
  }

  override fun isValid(value: Int): Boolean = value in 1..10

  override fun summaryOf(context: Context): String = context.getString(
    string.idepref_ai_search_result_limit_current,
    AiPreferences.searchResultLimit
  )
}
