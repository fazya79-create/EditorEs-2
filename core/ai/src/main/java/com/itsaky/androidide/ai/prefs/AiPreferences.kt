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
import com.itsaky.androidide.ai.provider.AnthropicProvider
import com.itsaky.androidide.ai.provider.OpenAiProvider
import com.itsaky.androidide.ai.provider.ProviderConfig
import com.itsaky.androidide.ai.provider.ProviderKind
import com.itsaky.androidide.preferences.internal.prefManager

@Suppress("MemberVisibilityCanBePrivate")
object AiPreferences {

  const val PROVIDER = "ide.ai.provider"
  const val OPENAI_BASE_URL = "ide.ai.openai.baseUrl"
  const val OPENAI_MODEL = "ide.ai.openai.model"
  const val ANTHROPIC_BASE_URL = "ide.ai.anthropic.baseUrl"
  const val ANTHROPIC_MODEL = "ide.ai.anthropic.model"
  const val YOLO_MODE = "ide.ai.yoloMode"
  const val SHELL_TIMEOUT = "ide.ai.shellTimeoutSeconds"

  const val SECRET_OPENAI_KEY = "openai.apiKey"
  const val SECRET_ANTHROPIC_KEY = "anthropic.apiKey"

  const val PROVIDER_OPENAI = 0
  const val PROVIDER_ANTHROPIC = 1

  const val DEFAULT_SHELL_TIMEOUT = 120

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

  var yoloMode: Boolean
    get() = prefManager.getBoolean(YOLO_MODE, false)
    set(value) {
      prefManager.putBoolean(YOLO_MODE, value)
    }

  var shellTimeoutSeconds: Int
    get() = prefManager.getInt(SHELL_TIMEOUT, DEFAULT_SHELL_TIMEOUT)
    set(value) {
      prefManager.putInt(SHELL_TIMEOUT, value)
    }

  fun providerKind(): ProviderKind = when (providerIndex) {
    PROVIDER_ANTHROPIC -> ProviderKind.ANTHROPIC
    else -> ProviderKind.OPENAI
  }

  fun apiKeyPrefKey(kind: ProviderKind): String = when (kind) {
    ProviderKind.ANTHROPIC -> SECRET_ANTHROPIC_KEY
    ProviderKind.OPENAI -> SECRET_OPENAI_KEY
  }

  fun providerConfig(context: Context): ProviderConfig {
    val kind = providerKind()
    val apiKey = SecretStore(context).get(apiKeyPrefKey(kind))
    return when (kind) {
      ProviderKind.ANTHROPIC -> ProviderConfig(
        kind = kind,
        baseUrl = anthropicBaseUrl,
        apiKey = apiKey,
        model = anthropicModel
      )

      ProviderKind.OPENAI -> ProviderConfig(
        kind = kind,
        baseUrl = openAiBaseUrl,
        apiKey = apiKey,
        model = openAiModel
      )
    }
  }
}
