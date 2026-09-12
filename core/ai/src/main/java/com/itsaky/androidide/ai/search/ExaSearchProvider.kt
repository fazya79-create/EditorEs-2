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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.itsaky.androidide.ai.search.HttpJson.stringOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exa: neural web search with page contents from the same API. */
object ExaSearchProvider : WebSearchProvider {

  private const val SEARCH_ENDPOINT = "https://api.exa.ai/search"
  private const val CONTENTS_ENDPOINT = "https://api.exa.ai/contents"

  private const val SNIPPET_CHARS = 800

  override val kind: SearchProviderKind = SearchProviderKind.EXA

  override val supportsFetch: Boolean = true

  override suspend fun search(
    query: String,
    apiKey: String,
    limit: Int
  ): SearchResults = withContext(Dispatchers.IO) {
    val text = JsonObject()
    text.addProperty("maxCharacters", SNIPPET_CHARS)

    val contents = JsonObject()
    contents.add("text", text)

    val body = JsonObject()
    body.addProperty("query", query)
    body.addProperty("numResults", limit)
    body.add("contents", contents)

    val response = HttpJson.post(
      url = SEARCH_ENDPOINT,
      headers = mapOf("x-api-key" to apiKey),
      body = body.toString()
    )

    SearchResults(query = query, items = parseResults(response))
  }

  override suspend fun fetch(url: String, apiKey: String): FetchedPage =
    withContext(Dispatchers.IO) {
      val urls = JsonArray()
      urls.add(url)

      val body = JsonObject()
      body.add("urls", urls)
      body.addProperty("text", true)

      val response = HttpJson.post(
        url = CONTENTS_ENDPOINT,
        headers = mapOf("x-api-key" to apiKey),
        body = body.toString()
      )

      val result = response.getAsJsonArray("results")
        ?.firstOrNull()
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?: throw SearchException(statusReason(response, url))

      val content = result.stringOf("text")
      if (content.isBlank()) {
        throw SearchException("Exa returned no readable content for $url.")
      }

      FetchedPage(url = result.stringOf("url").ifBlank { url }, content = content)
    }

  internal fun parseResults(response: JsonObject): List<SearchResultItem> =
    response.getAsJsonArray("results")
      ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
      ?.map { result ->
        SearchResultItem(
          title = result.stringOf("title"),
          url = result.stringOf("url"),
          snippet = result.stringOf("summary").ifBlank { result.stringOf("text") },
          publishedDate = result.stringOf("publishedDate")
        )
      }
      .orEmpty()

  /** Exa reports per-URL failures in `statuses` rather than as an HTTP error. */
  internal fun statusReason(response: JsonObject, url: String): String {
    val error = response.getAsJsonArray("statuses")
      ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
      ?.firstOrNull()
      ?.let { status ->
        status.getAsJsonObject("error")?.stringOf("tag").orEmpty()
          .ifBlank { status.stringOf("status") }
      }
      .orEmpty()

    return if (error.isBlank()) {
      "Exa could not extract any content from $url."
    } else {
      "Exa could not extract $url: $error"
    }
  }
}
