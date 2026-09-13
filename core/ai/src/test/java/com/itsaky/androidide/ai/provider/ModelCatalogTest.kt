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
  fun `server specific context fields and nested containers are detected`() {
    val body = """
      {
        "data": [
          {"id": "vllm/model", "max_model_len": 32768},
          {"id": "litellm/model", "max_input_tokens": 200000},
          {"id": "router/model", "capabilities": {"contextWindow": 200000}},
          {"id": "ollama/model", "context_length": 262144},
          {"id": "camel/model", "contextLength": 8192},
          {"id": "tgwui/model", "truncation_length": 4096},
          {"id": "proxy/model", "model_info": {"max_input_tokens": 128000}},
          {"id": "unknown/model", "object": "model"}
        ]
      }
    """.trimIndent()

    val windows = ModelCatalog.parse(ProviderKind.OPENAI, body).associate { it.id to it.contextWindow }

    assertThat(windows["vllm/model"]).isEqualTo(32_768)
    assertThat(windows["litellm/model"]).isEqualTo(200_000)
    assertThat(windows["router/model"]).isEqualTo(200_000)
    assertThat(windows["ollama/model"]).isEqualTo(262_144)
    assertThat(windows["camel/model"]).isEqualTo(8_192)
    assertThat(windows["tgwui/model"]).isEqualTo(4_096)
    assertThat(windows["proxy/model"]).isEqualTo(128_000)
    assertThat(windows["unknown/model"]).isEqualTo(0)
  }

  @Test
  fun `a gateway listing keeps provider prefixed ids with their reported window`() {
    val body = """
      {
        "object": "list",
        "data": [
          {"id": "high", "object": "model", "owned_by": "combo"},
          {
            "id": "cl/z-ai/glm-5.3",
            "object": "model",
            "owned_by": "cl",
            "capabilities": {"contextWindow": 200000, "maxOutput": 128000},
            "context_length": 200000,
            "max_completion_tokens": 128000
          },
          {
            "id": "cl/z-ai/glm-5.3-flash",
            "object": "model",
            "owned_by": "cl",
            "capabilities": {"contextWindow": 1000000, "maxOutput": 131072},
            "context_length": 1000000,
            "max_completion_tokens": 131072
          }
        ]
      }
    """.trimIndent()

    val windows = ModelCatalog.parse(ProviderKind.OPENAI, body).associate { it.id to it.contextWindow }

    assertThat(windows["cl/z-ai/glm-5.3"]).isEqualTo(200_000)
    assertThat(windows["cl/z-ai/glm-5.3-flash"]).isEqualTo(1_000_000)
    assertThat(windows["high"]).isEqualTo(0)
  }

  @Test
  fun `a malformed body yields no models instead of failing`() {
    assertThat(ModelCatalog.parse(ProviderKind.OPENAI, "not json")).isEmpty()
    assertThat(ModelCatalog.parse(ProviderKind.GOOGLE, "{}")).isEmpty()
  }
}
