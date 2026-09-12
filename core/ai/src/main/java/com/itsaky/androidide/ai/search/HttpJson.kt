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

package com.itsaky.androidide.ai.search

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.itsaky.androidide.ai.net.HttpStatusException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal object HttpJson {

  private const val CONNECT_TIMEOUT = 15_000
  private const val READ_TIMEOUT = 30_000

  fun get(url: String, headers: Map<String, String>): JsonObject =
    parse(request(url, "GET", headers, null))

  fun post(url: String, headers: Map<String, String>, body: String): JsonObject =
    parse(request(url, "POST", headers, body))

  private fun request(
    url: String,
    method: String,
    headers: Map<String, String>,
    body: String?
  ): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = CONNECT_TIMEOUT
      readTimeout = READ_TIMEOUT
      setRequestProperty("Accept", "application/json")
      headers.forEach { (key, value) -> setRequestProperty(key, value) }
      if (body != null) {
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
      }
    }

    try {
      body?.let { payload ->
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
      }

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

  private fun parse(body: String): JsonObject =
    runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
      ?: throw SearchException("The search provider returned a response that is not JSON.")

  fun JsonElement.stringOrEmpty(): String = if (isJsonPrimitive) asString else ""

  fun JsonObject.stringOf(name: String): String =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
}
