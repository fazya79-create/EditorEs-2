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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.itsaky.androidide.ai.net.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ModelInfo(
  val id: String,
  val contextWindow: Int = 0
)

object ModelCatalog {

  private const val CONNECT_TIMEOUT = 20_000
  private const val READ_TIMEOUT = 30_000
  private const val PAGE_LIMIT = 1000

  suspend fun fetch(config: ProviderConfig): List<ModelInfo> = withContext(Dispatchers.IO) {
    val base = config.baseUrl.trimEnd('/')
    val url = when (config.kind) {
      ProviderKind.ANTHROPIC -> "$base/models?limit=$PAGE_LIMIT"
      ProviderKind.OPENAI -> "$base/models"
      ProviderKind.GOOGLE -> "$base/models?pageSize=$PAGE_LIMIT"
    }

    val headers = when (config.kind) {
      ProviderKind.ANTHROPIC -> mapOf(
        "x-api-key" to config.apiKey,
        "anthropic-version" to ANTHROPIC_VERSION
      )

      ProviderKind.OPENAI -> mapOf("Authorization" to "Bearer ${config.apiKey}")

      ProviderKind.GOOGLE -> mapOf("x-goog-api-key" to config.apiKey)
    }

    parse(config.kind, get(url, headers))
  }

  private fun get(url: String, headers: Map<String, String>): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = CONNECT_TIMEOUT
      readTimeout = READ_TIMEOUT
      setRequestProperty("Accept", "application/json")
      headers.forEach { (key, value) -> setRequestProperty(key, value) }
    }

    try {
      val status = connection.responseCode
      if (status !in 200..299) {
        val error = connection.errorStream?.use { stream ->
          InputStreamReader(stream, Charsets.UTF_8).readText()
        } ?: ""
        throw HttpStatusException(status, error)
      }
      return connection.inputStream.use { stream ->
        InputStreamReader(stream, Charsets.UTF_8).readText()
      }
    } finally {
      connection.disconnect()
    }
  }

  internal fun parse(kind: ProviderKind, body: String): List<ModelInfo> {
    val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
      ?: return emptyList()

    val entries = when (kind) {
      ProviderKind.GOOGLE -> root.getAsJsonArray("models")
      else -> root.getAsJsonArray("data")
    } ?: return emptyList()

    return entries.mapNotNull { element ->
      val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
      when (kind) {
        ProviderKind.GOOGLE -> googleModel(entry)
        ProviderKind.ANTHROPIC -> anthropicModel(entry)
        ProviderKind.OPENAI -> openAiModel(entry)
      }
    }
      .filter { it.id.isNotBlank() }
      .distinctBy { it.id }
  }

  private fun googleModel(entry: JsonObject): ModelInfo? {
    val methods = entry.getAsJsonArray("supportedGenerationMethods")
      ?.mapNotNull { it.takeIf { method -> method.isJsonPrimitive }?.asString }
      .orEmpty()
    if (methods.isNotEmpty() && GENERATE_CONTENT !in methods) {
      return null
    }

    val name = entry.string("name")?.substringAfter("models/") ?: return null
    return ModelInfo(id = name, contextWindow = entry.positiveInt("inputTokenLimit"))
  }

  private fun anthropicModel(entry: JsonObject): ModelInfo? {
    val id = entry.string("id") ?: return null
    return ModelInfo(id = id, contextWindow = entry.positiveInt("max_input_tokens"))
  }

  private fun openAiModel(entry: JsonObject): ModelInfo? {
    val id = entry.string("id") ?: return null
    val window = OPENAI_CONTEXT_FIELDS.firstNotNullOfOrNull { field ->
      entry.positiveInt(field).takeIf { it > 0 }
    } ?: entry.getAsJsonObject("top_provider")?.positiveInt("context_length") ?: 0

    return ModelInfo(id = id, contextWindow = window)
  }

  private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

  private fun JsonObject.positiveInt(name: String): Int =
    get(name)?.takeIf { it.isJsonPrimitive }
      ?.let { runCatching { it.asInt }.getOrNull() }
      ?.takeIf { it > 0 }
      ?: 0

  private const val ANTHROPIC_VERSION = "2023-06-01"
  private const val GENERATE_CONTENT = "generateContent"

  private val OPENAI_CONTEXT_FIELDS = listOf(
    "context_window",
    "context_length",
    "max_context_length",
    "max_input_tokens"
  )
}
