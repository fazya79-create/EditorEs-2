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

package com.itsaky.androidide.ai.prefs

import android.content.Context
import com.itsaky.androidide.ai.model.ThinkingLevel
import com.itsaky.androidide.ai.provider.AnthropicProvider
import com.itsaky.androidide.ai.provider.ContextWindows
import com.itsaky.androidide.ai.provider.GoogleProvider
import com.itsaky.androidide.ai.provider.OpenAiProvider
import com.itsaky.androidide.ai.provider.ProviderConfig
import com.itsaky.androidide.ai.provider.ProviderKind
import com.itsaky.androidide.ai.search.SearchProviderKind
import com.itsaky.androidide.preferences.internal.prefManager

@Suppress("MemberVisibilityCanBePrivate")
object AiPreferences {

  const val PROVIDER = "ide.ai.provider"
  const val OPENAI_BASE_URL = "ide.ai.openai.baseUrl"
  const val OPENAI_MODEL = "ide.ai.openai.model"
  const val ANTHROPIC_BASE_URL = "ide.ai.anthropic.baseUrl"
  const val ANTHROPIC_MODEL = "ide.ai.anthropic.model"
  const val GOOGLE_BASE_URL = "ide.ai.google.baseUrl"
  const val GOOGLE_MODEL = "ide.ai.google.model"
  const val YOLO_MODE = "ide.ai.yoloMode"
  const val SHELL_TIMEOUT = "ide.ai.shellTimeoutSeconds"
  const val OPENAI_THINKING = "ide.ai.openai.thinking"
  const val ANTHROPIC_THINKING = "ide.ai.anthropic.thinking"
  const val GOOGLE_THINKING = "ide.ai.google.thinking"
  const val MAX_TOOL_ROUNDS = "ide.ai.maxToolRounds"
  const val AUTO_COMPACT = "ide.ai.autoCompact"
  const val COMPACT_THRESHOLD = "ide.ai.compactThresholdPercent"
  const val OPENAI_CONTEXT_WINDOW = "ide.ai.openai.contextWindow"
  const val ANTHROPIC_CONTEXT_WINDOW = "ide.ai.anthropic.contextWindow"
  const val GOOGLE_CONTEXT_WINDOW = "ide.ai.google.contextWindow"
  const val PROMPT_CACHING = "ide.ai.promptCaching"
  const val SEARCH_PROVIDER = "ide.ai.search.provider"
  const val SEARCH_RESULT_LIMIT = "ide.ai.search.resultLimit"

  const val SECRET_OPENAI_KEY = "openai.apiKey"
  const val SECRET_ANTHROPIC_KEY = "anthropic.apiKey"
  const val SECRET_GOOGLE_KEY = "google.apiKey"
  const val SECRET_TAVILY_KEY = "tavily.apiKey"
  const val SECRET_FIRECRAWL_KEY = "firecrawl.apiKey"
  const val SECRET_SERPER_KEY = "serper.apiKey"
  const val SECRET_EXA_KEY = "exa.apiKey"

  const val PROVIDER_OPENAI = 0
  const val PROVIDER_ANTHROPIC = 1
  const val PROVIDER_GOOGLE = 2

  const val DEFAULT_SHELL_TIMEOUT = 120
  const val DEFAULT_MAX_TOOL_ROUNDS = 25
  const val DEFAULT_COMPACT_THRESHOLD = 80
  const val DEFAULT_CONTEXT_WINDOW = 200_000
  const val DEFAULT_SEARCH_RESULT_LIMIT = 5
  const val UNLIMITED_TOOL_ROUNDS = 0

  var providerIndex: Int
    get() = prefManager.getInt(PROVIDER, PROVIDER_OPENAI)
    set(value) {
      prefManager.putInt(PROVIDER, value)
    }

  var openAiBaseUrl: String
    get() = prefManager.getString(OPENAI_BASE_URL, OpenAiProvider.DEFAULT_BASE_URL)
      .ifBlank { OpenAiProvider.DEFAULT_BASE_URL }
    set(value) {
      prefManager.putString(OPENAI_BASE_URL, value)
    }

  var openAiModel: String
    get() = prefManager.getString(OPENAI_MODEL, OpenAiProvider.DEFAULT_MODEL)
      .ifBlank { OpenAiProvider.DEFAULT_MODEL }
    set(value) {
      prefManager.putString(OPENAI_MODEL, value)
    }

  var anthropicBaseUrl: String
    get() = prefManager.getString(ANTHROPIC_BASE_URL, AnthropicProvider.DEFAULT_BASE_URL)
      .ifBlank { AnthropicProvider.DEFAULT_BASE_URL }
    set(value) {
      prefManager.putString(ANTHROPIC_BASE_URL, value)
    }

  var anthropicModel: String
    get() = prefManager.getString(ANTHROPIC_MODEL, AnthropicProvider.DEFAULT_MODEL)
      .ifBlank { AnthropicProvider.DEFAULT_MODEL }
    set(value) {
      prefManager.putString(ANTHROPIC_MODEL, value)
    }

  var googleBaseUrl: String
    get() = prefManager.getString(GOOGLE_BASE_URL, GoogleProvider.DEFAULT_BASE_URL)
      .ifBlank { GoogleProvider.DEFAULT_BASE_URL }
    set(value) {
      prefManager.putString(GOOGLE_BASE_URL, value)
    }

  var googleModel: String
    get() = prefManager.getString(GOOGLE_MODEL, GoogleProvider.DEFAULT_MODEL)
      .ifBlank { GoogleProvider.DEFAULT_MODEL }
    set(value) {
      prefManager.putString(GOOGLE_MODEL, value)
    }

  var yoloMode: Boolean
    get() = prefManager.getBoolean(YOLO_MODE, false)
    set(value) {
      prefManager.putBoolean(YOLO_MODE, value)
    }

  var promptCaching: Boolean
    get() = prefManager.getBoolean(PROMPT_CACHING, true)
    set(value) {
      prefManager.putBoolean(PROMPT_CACHING, value)
    }

  var shellTimeoutSeconds: Int
    get() = prefManager.getInt(SHELL_TIMEOUT, DEFAULT_SHELL_TIMEOUT)
    set(value) {
      prefManager.putInt(SHELL_TIMEOUT, value)
    }

  var maxToolRounds: Int
    get() = prefManager.getInt(MAX_TOOL_ROUNDS, DEFAULT_MAX_TOOL_ROUNDS)
    set(value) {
      prefManager.putInt(MAX_TOOL_ROUNDS, value)
    }

  var autoCompact: Boolean
    get() = prefManager.getBoolean(AUTO_COMPACT, true)
    set(value) {
      prefManager.putBoolean(AUTO_COMPACT, value)
    }

  var compactThresholdPercent: Int
    get() = prefManager.getInt(COMPACT_THRESHOLD, DEFAULT_COMPACT_THRESHOLD)
    set(value) {
      prefManager.putInt(COMPACT_THRESHOLD, value)
    }

  var searchProviderIndex: Int
    get() = prefManager.getInt(SEARCH_PROVIDER, SearchProviderKind.TAVILY.ordinal)
    set(value) {
      prefManager.putInt(SEARCH_PROVIDER, value)
    }

  var searchResultLimit: Int
    get() = prefManager.getInt(SEARCH_RESULT_LIMIT, DEFAULT_SEARCH_RESULT_LIMIT)
    set(value) {
      prefManager.putInt(SEARCH_RESULT_LIMIT, value)
    }

  fun searchProviderKind(): SearchProviderKind =
    SearchProviderKind.entries.getOrElse(searchProviderIndex) { SearchProviderKind.TAVILY }

  fun searchApiKeyPrefKey(kind: SearchProviderKind): String = when (kind) {
    SearchProviderKind.TAVILY -> SECRET_TAVILY_KEY
    SearchProviderKind.FIRECRAWL -> SECRET_FIRECRAWL_KEY
    SearchProviderKind.SERPER -> SECRET_SERPER_KEY
    SearchProviderKind.EXA -> SECRET_EXA_KEY
  }

  fun contextWindowPrefKey(kind: ProviderKind): String = when (kind) {
    ProviderKind.ANTHROPIC -> ANTHROPIC_CONTEXT_WINDOW
    ProviderKind.OPENAI -> OPENAI_CONTEXT_WINDOW
    ProviderKind.GOOGLE -> GOOGLE_CONTEXT_WINDOW
  }

  fun contextWindowOf(kind: ProviderKind): Int =
    prefManager.getInt(contextWindowPrefKey(kind), DEFAULT_CONTEXT_WINDOW)

  fun setContextWindowOf(kind: ProviderKind, tokens: Int) {
    prefManager.putInt(contextWindowPrefKey(kind), tokens)
  }

  fun applyModelContextWindow(kind: ProviderKind, model: String, reported: Int): Int {
    val window = reported.takeIf { it > 0 } ?: ContextWindows.of(kind, model)
    if (window > 0) {
      setContextWindowOf(kind, window)
    }
    return window
  }

  fun effectiveCompactThreshold(): Int =
    if (autoCompact) compactThresholdPercent else 0

  fun providerKind(): ProviderKind = when (providerIndex) {
    PROVIDER_ANTHROPIC -> ProviderKind.ANTHROPIC
    PROVIDER_GOOGLE -> ProviderKind.GOOGLE
    else -> ProviderKind.OPENAI
  }

  var thinkingLevel: ThinkingLevel
    get() {
      val key = thinkingPrefKey(providerKind())
      val stored = prefManager.getInt(key, ThinkingLevel.OFF.ordinal)
      return ThinkingLevel.entries.getOrElse(stored) { ThinkingLevel.OFF }
    }
    set(value) {
      prefManager.putInt(thinkingPrefKey(providerKind()), value.ordinal)
    }

  fun thinkingPrefKey(kind: ProviderKind): String = when (kind) {
    ProviderKind.ANTHROPIC -> ANTHROPIC_THINKING
    ProviderKind.OPENAI -> OPENAI_THINKING
    ProviderKind.GOOGLE -> GOOGLE_THINKING
  }

  fun thinkingLevelOf(kind: ProviderKind): ThinkingLevel {
    val stored = prefManager.getInt(thinkingPrefKey(kind), ThinkingLevel.OFF.ordinal)
    return ThinkingLevel.entries.getOrElse(stored) { ThinkingLevel.OFF }
  }

  fun setThinkingLevelOf(kind: ProviderKind, level: ThinkingLevel) {
    prefManager.putInt(thinkingPrefKey(kind), level.ordinal)
  }

  fun modelOf(kind: ProviderKind): String = when (kind) {
    ProviderKind.ANTHROPIC -> anthropicModel
    ProviderKind.OPENAI -> openAiModel
    ProviderKind.GOOGLE -> googleModel
  }

  fun setModelOf(kind: ProviderKind, model: String) {
    when (kind) {
      ProviderKind.ANTHROPIC -> anthropicModel = model
      ProviderKind.OPENAI -> openAiModel = model
      ProviderKind.GOOGLE -> googleModel = model
    }
  }

  fun baseUrlOf(kind: ProviderKind): String = when (kind) {
    ProviderKind.ANTHROPIC -> anthropicBaseUrl
    ProviderKind.OPENAI -> openAiBaseUrl
    ProviderKind.GOOGLE -> googleBaseUrl
  }

  fun apiKeyPrefKey(kind: ProviderKind): String = when (kind) {
    ProviderKind.ANTHROPIC -> SECRET_ANTHROPIC_KEY
    ProviderKind.OPENAI -> SECRET_OPENAI_KEY
    ProviderKind.GOOGLE -> SECRET_GOOGLE_KEY
  }

  fun providerConfig(context: Context): ProviderConfig = providerConfig(context, providerKind())

  fun providerConfig(context: Context, kind: ProviderKind): ProviderConfig = ProviderConfig(
    kind = kind,
    baseUrl = baseUrlOf(kind),
    apiKey = SecretStore(context).get(apiKeyPrefKey(kind)),
    model = modelOf(kind),
    promptCaching = promptCaching
  )
}
