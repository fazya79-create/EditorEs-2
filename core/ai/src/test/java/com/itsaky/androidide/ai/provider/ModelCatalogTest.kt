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

package com.itsaky.androidide.ai.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelCatalogTest {

  @Test
  fun `google listing keeps generateContent models and reads inputTokenLimit`() {
    val body = """
      {
        "models": [
          {
            "name": "models/gemini-2.5-flash",
            "inputTokenLimit": 1048576,
            "supportedGenerationMethods": ["generateContent", "countTokens"]
          },
          {
            "name": "models/gemini-embedding-001",
            "inputTokenLimit": 2048,
            "supportedGenerationMethods": ["embedContent"]
          }
        ]
      }
    """.trimIndent()

    val models = ModelCatalog.parse(ProviderKind.GOOGLE, body)

    assertThat(models).hasSize(1)
    assertThat(models[0].id).isEqualTo("gemini-2.5-flash")
    assertThat(models[0].contextWindow).isEqualTo(1_048_576)
  }

  @Test
  fun `anthropic listing reads max_input_tokens and tolerates a missing value`() {
    val body = """
      {
        "data": [
          {"type": "model", "id": "claude-sonnet-4-5", "max_input_tokens": 1000000},
          {"type": "model", "id": "claude-haiku-4-5", "max_input_tokens": null}
        ]
      }
    """.trimIndent()

    val models = ModelCatalog.parse(ProviderKind.ANTHROPIC, body)

    assertThat(models.map { it.id })
      .containsExactly("claude-sonnet-4-5", "claude-haiku-4-5")
      .inOrder()
    assertThat(models[0].contextWindow).isEqualTo(1_000_000)
    assertThat(models[1].contextWindow).isEqualTo(0)
  }

  @Test
  fun `openai listing reports no window but gateway extensions are read`() {
    val body = """
      {
        "data": [
          {"id": "gpt-4o-mini", "object": "model"},
          {"id": "some/model", "context_length": 131072},
          {"id": "other/model", "top_provider": {"context_length": 200000}}
        ]
      }
    """.trimIndent()

    val models = ModelCatalog.parse(ProviderKind.OPENAI, body)

    assertThat(models[0].contextWindow).isEqualTo(0)
    assertThat(models[1].contextWindow).isEqualTo(131_072)
    assertThat(models[2].contextWindow).isEqualTo(200_000)
  }

  @Test
  fun `a malformed body yields no models instead of failing`() {
    assertThat(ModelCatalog.parse(ProviderKind.OPENAI, "not json")).isEmpty()
    assertThat(ModelCatalog.parse(ProviderKind.GOOGLE, "{}")).isEmpty()
  }

  @Test
  fun `known families provide a window when the listing reports none`() {
    assertThat(ContextWindows.of(ProviderKind.OPENAI, "gpt-4o-mini")).isEqualTo(128_000)
    assertThat(ContextWindows.of(ProviderKind.OPENAI, "gpt-3.5-turbo")).isEqualTo(16_385)
    assertThat(ContextWindows.of(ProviderKind.ANTHROPIC, "claude-opus-4-5")).isEqualTo(200_000)
    assertThat(ContextWindows.of(ProviderKind.ANTHROPIC, "claude-sonnet-4-5")).isEqualTo(1_000_000)
    assertThat(ContextWindows.of(ProviderKind.GOOGLE, "gemini-2.5-flash")).isEqualTo(1_048_576)
    assertThat(ContextWindows.of(ProviderKind.OPENAI, "some-unknown-model")).isEqualTo(0)
  }
}
