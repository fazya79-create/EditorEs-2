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

import com.google.gson.JsonParser
import com.itsaky.androidide.ai.net.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ModelCatalog {

  private const val CONNECT_TIMEOUT = 20_000
  private const val READ_TIMEOUT = 30_000
  private const val PAGE_LIMIT = 1000

  suspend fun fetch(config: ProviderConfig): List<String> = withContext(Dispatchers.IO) {
    val base = config.baseUrl.trimEnd('/')
    val url = when (config.kind) {
      ProviderKind.ANTHROPIC -> "$base/models?limit=$PAGE_LIMIT"
      ProviderKind.OPENAI -> "$base/models"
    }

    val headers = when (config.kind) {
      ProviderKind.ANTHROPIC -> mapOf(
        "x-api-key" to config.apiKey,
        "anthropic-version" to ANTHROPIC_VERSION
      )

      ProviderKind.OPENAI -> mapOf("Authorization" to "Bearer ${config.apiKey}")
    }

    parse(get(url, headers))
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

  private fun parse(body: String): List<String> {
    val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
      ?: return emptyList()

    return root.getAsJsonArray("data")
      ?.mapNotNull { element ->
        element.takeIf { it.isJsonObject }
          ?.asJsonObject
          ?.get("id")
          ?.takeIf { it.isJsonPrimitive }
          ?.asString
      }
      ?.filter { it.isNotBlank() }
      ?.distinct()
      ?: emptyList()
  }

  private const val ANTHROPIC_VERSION = "2023-06-01"
}
