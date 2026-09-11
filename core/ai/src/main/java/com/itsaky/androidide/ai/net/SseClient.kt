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

package com.itsaky.androidide.ai.net

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class SseEvent(val name: String?, val data: String)

class HttpStatusException(val status: Int, val body: String) :
  RuntimeException("HTTP $status: ${body.take(MAX_BODY)}") {

  companion object {
    private const val MAX_BODY = 1024
  }
}

class SseFrameReader(private val reader: BufferedReader) {

  fun next(): SseEvent? {
    var eventName: String? = null
    val data = StringBuilder()

    while (true) {
      val line = reader.readLine()

      if (line == null) {
        return if (data.isEmpty()) null else SseEvent(eventName, data.toString())
      }

      if (line.isEmpty()) {
        if (data.isEmpty()) {
          eventName = null
          continue
        }
        return SseEvent(eventName, data.toString())
      }

      if (line.startsWith(":")) {
        continue
      }

      val separator = line.indexOf(':')
      val field = if (separator < 0) line else line.substring(0, separator)
      var value = if (separator < 0) "" else line.substring(separator + 1)
      if (value.startsWith(" ")) {
        value = value.substring(1)
      }

      when (field) {
        "event" -> eventName = value
        "data" -> {
          if (data.isNotEmpty()) {
            data.append('\n')
          }
          data.append(value)
        }
      }
    }
  }
}

class SseConnection internal constructor(
  private val connection: HttpURLConnection,
  private val reader: BufferedReader
) : Closeable {

  private val frames = SseFrameReader(reader)

  fun next(): SseEvent? = frames.next()

  override fun close() {
    runCatching { reader.close() }
    runCatching { connection.disconnect() }
  }
}

object SseClient {

  private const val CONNECT_TIMEOUT = 30_000
  private const val READ_TIMEOUT = 300_000

  fun post(url: String, headers: Map<String, String>, body: String): SseConnection {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      connectTimeout = CONNECT_TIMEOUT
      readTimeout = READ_TIMEOUT
      doOutput = true
      doInput = true
      setRequestProperty("Content-Type", "application/json")
      setRequestProperty("Accept", "text/event-stream")
      headers.forEach { (key, value) -> setRequestProperty(key, value) }
    }

    try {
      connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

      val status = connection.responseCode
      if (status !in 200..299) {
        val error = connection.errorStream?.use { stream ->
          InputStreamReader(stream, Charsets.UTF_8).readText()
        } ?: ""
        throw HttpStatusException(status, error)
      }

      return SseConnection(
        connection,
        BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
      )
    } catch (err: Throwable) {
      connection.disconnect()
      throw err
    }
  }
}
