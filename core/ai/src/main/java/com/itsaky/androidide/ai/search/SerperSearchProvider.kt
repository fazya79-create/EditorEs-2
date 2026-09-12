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

import com.google.gson.JsonObject
import com.itsaky.androidide.ai.search.HttpJson.stringOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Serper: Google SERP results. It has no extraction endpoint, so it cannot fetch pages. */
object SerperSearchProvider : WebSearchProvider {

  private const val SEARCH_ENDPOINT = "https://google.serper.dev/search"

  override val kind: SearchProviderKind = SearchProviderKind.SERPER

  override suspend fun search(
    query: String,
    apiKey: String,
    limit: Int
  ): SearchResults = withContext(Dispatchers.IO) {
    val body = JsonObject()
    body.addProperty("q", query)
    body.addProperty("num", limit)

    val response = HttpJson.post(
      url = SEARCH_ENDPOINT,
      headers = mapOf("X-API-KEY" to apiKey),
      body = body.toString()
    )

    SearchResults(
      query = query,
      answer = parseAnswer(response),
      items = parseResults(response)
    )
  }

  internal fun parseResults(response: JsonObject): List<SearchResultItem> =
    response.getAsJsonArray("organic")
      ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
      ?.map { result ->
        SearchResultItem(
          title = result.stringOf("title"),
          url = result.stringOf("link"),
          snippet = result.stringOf("snippet"),
          publishedDate = result.stringOf("date")
        )
      }
      .orEmpty()

  /** Serper reports a direct answer in `answerBox`, whose useful field varies per query. */
  internal fun parseAnswer(response: JsonObject): String {
    val box = response.getAsJsonObject("answerBox") ?: return ""
    return ANSWER_FIELDS.firstNotNullOfOrNull { field ->
      box.stringOf(field).takeIf { it.isNotBlank() }
    }.orEmpty()
  }

  private val ANSWER_FIELDS = listOf("answer", "snippet", "title")
}
