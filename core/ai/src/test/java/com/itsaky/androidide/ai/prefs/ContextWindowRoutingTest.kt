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

import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.ai.provider.ContextWindowSource
import com.itsaky.androidide.ai.provider.ProviderKind
import com.itsaky.androidide.app.BaseApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = BaseApplication::class)
class ContextWindowRoutingTest {

  @Test
  fun `a window detected at one endpoint is not served for another`() {
    val model = "cl/z-ai/glm-5.3-flash"

    AiPreferences.openAiBaseUrl = "https://api.openai.com/v1"
    AiPreferences.setModelOf(ProviderKind.OPENAI, model)
    AiPreferences.applyModelContextWindow(ProviderKind.OPENAI, model, 200_000)

    assertThat(AiPreferences.contextWindowOf(ProviderKind.OPENAI)).isEqualTo(200_000)

    AiPreferences.openAiBaseUrl = "https://gateway.example/v1"

    assertThat(AiPreferences.contextWindowOf(ProviderKind.OPENAI))
      .isEqualTo(AiPreferences.DEFAULT_CONTEXT_WINDOW)
    assertThat(AiPreferences.contextWindowSourceOf(ProviderKind.OPENAI))
      .isEqualTo(ContextWindowSource.UNKNOWN)
  }

  @Test
  fun `re-detecting at the new endpoint stores that endpoint's window`() {
    val model = "cl/z-ai/glm-5.3-flash"

    AiPreferences.openAiBaseUrl = "https://api.openai.com/v1"
    AiPreferences.setModelOf(ProviderKind.OPENAI, model)
    AiPreferences.applyModelContextWindow(ProviderKind.OPENAI, model, 200_000)

    AiPreferences.openAiBaseUrl = "https://gateway.example/v1"
    AiPreferences.applyModelContextWindow(ProviderKind.OPENAI, model, 1_000_000)

    assertThat(AiPreferences.contextWindowOf(ProviderKind.OPENAI)).isEqualTo(1_000_000)
    assertThat(AiPreferences.contextWindowSourceOf(ProviderKind.OPENAI))
      .isEqualTo(ContextWindowSource.DETECTED)
  }

  @Test
  fun `returning to the original endpoint requires re-detection rather than reusing the other window`() {
    val model = "cl/z-ai/glm-5.3-flash"

    AiPreferences.openAiBaseUrl = "https://gateway.example/v1"
    AiPreferences.setModelOf(ProviderKind.OPENAI, model)
    AiPreferences.applyModelContextWindow(ProviderKind.OPENAI, model, 1_000_000)
    assertThat(AiPreferences.contextWindowOf(ProviderKind.OPENAI)).isEqualTo(1_000_000)

    AiPreferences.openAiBaseUrl = "https://api.openai.com/v1"

    assertThat(AiPreferences.contextWindowOf(ProviderKind.OPENAI))
      .isEqualTo(AiPreferences.DEFAULT_CONTEXT_WINDOW)
  }

  @Test
  fun `a trailing slash does not count as a different endpoint`() {
    val model = "cl/z-ai/glm-5.3-flash"

    AiPreferences.openAiBaseUrl = "https://gateway.example/v1"
    AiPreferences.setModelOf(ProviderKind.OPENAI, model)
    AiPreferences.applyModelContextWindow(ProviderKind.OPENAI, model, 1_000_000)

    AiPreferences.openAiBaseUrl = "https://gateway.example/v1/"

    assertThat(AiPreferences.contextWindowOf(ProviderKind.OPENAI)).isEqualTo(1_000_000)
    assertThat(AiPreferences.contextWindowSourceOf(ProviderKind.OPENAI))
      .isEqualTo(ContextWindowSource.DETECTED)
  }

  @Test
  fun `a model the gateway reports no window for falls back to the default`() {
    AiPreferences.openAiBaseUrl = "https://gateway.example/v1"
    AiPreferences.setModelOf(ProviderKind.OPENAI, "high")

    val applied = AiPreferences.applyModelContextWindow(ProviderKind.OPENAI, "high", 0)

    assertThat(applied).isEqualTo(0)
    assertThat(AiPreferences.contextWindowOf(ProviderKind.OPENAI))
      .isEqualTo(AiPreferences.DEFAULT_CONTEXT_WINDOW)
    assertThat(AiPreferences.contextWindowSourceOf(ProviderKind.OPENAI))
      .isEqualTo(ContextWindowSource.UNKNOWN)
  }

  @Test
  fun `a familiar model name is no longer guessed when the listing reports nothing`() {
    AiPreferences.openAiBaseUrl = "https://gateway.example/v1"
    AiPreferences.setModelOf(ProviderKind.OPENAI, "gpt-4o-mini")

    AiPreferences.applyModelContextWindow(ProviderKind.OPENAI, "gpt-4o-mini", 0)

    assertThat(AiPreferences.contextWindowOf(ProviderKind.OPENAI))
      .isEqualTo(AiPreferences.DEFAULT_CONTEXT_WINDOW)
  }

  @Test
  fun `a manually entered window still applies at the endpoint it was set for`() {
    val model = "cl/z-ai/glm-5.3"

    AiPreferences.openAiBaseUrl = "https://gateway.example/v1"
    AiPreferences.setModelOf(ProviderKind.OPENAI, model)
    AiPreferences.setContextWindowOf(ProviderKind.OPENAI, 321_000)

    assertThat(AiPreferences.contextWindowOf(ProviderKind.OPENAI)).isEqualTo(321_000)
    assertThat(AiPreferences.contextWindowSourceOf(ProviderKind.OPENAI))
      .isEqualTo(ContextWindowSource.MANUAL)
  }
}
