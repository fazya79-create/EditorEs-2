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

object TavilySearchProvider : WebSearchProvider {

  private const val SEARCH_ENDPOINT = "https://api.tavily.com/search"
  private const val EXTRACT_ENDPOINT = "https://api.tavily.com/extract"

  override val kind: SearchProviderKind = SearchProviderKind.TAVILY

  override val supportsFetch: Boolean = true

  override suspend fun search(
    query: String,
    apiKey: String,
    limit: Int
  ): SearchResults = withContext(Dispatchers.IO) {
    val body = JsonObject()
    body.addProperty("query", query)
    body.addProperty("max_results", limit)
    body.addProperty("search_depth", "basic")
    body.addProperty("topic", "general")
    body.addProperty("include_answer", true)
    body.addProperty("include_raw_content", false)
    body.addProperty("include_images", false)

    val response = HttpJson.post(
      url = SEARCH_ENDPOINT,
      headers = mapOf("Authorization" to "Bearer $apiKey"),
      body = body.toString()
    )

    val items = response.getAsJsonArray("results")
      ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
      ?.map { result ->
        SearchResultItem(
          title = result.stringOf("title"),
          url = result.stringOf("url"),
          snippet = result.stringOf("content"),
          publishedDate = result.stringOf("published_date")
        )
      }
      .orEmpty()

    SearchResults(
      query = query,
      answer = response.stringOf("answer"),
      items = items
    )
  }

  override suspend fun fetch(url: String, apiKey: String): FetchedPage =
    withContext(Dispatchers.IO) {
      val body = JsonObject()
      body.addProperty("urls", url)
      body.addProperty("extract_depth", "basic")
      body.addProperty("format", "markdown")
      body.addProperty("include_images", false)

      val response = HttpJson.post(
        url = EXTRACT_ENDPOINT,
        headers = mapOf("Authorization" to "Bearer $apiKey"),
        body = body.toString()
      )

      val result = response.getAsJsonArray("results")
        ?.firstOrNull()
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject

      if (result == null) {
        val reason = response.getAsJsonArray("failed_results")
          ?.firstOrNull()
          ?.takeIf { it.isJsonObject }
          ?.asJsonObject
          ?.stringOf("error")
          .orEmpty()

        throw SearchException(
          if (reason.isBlank()) {
            "Tavily could not extract any content from $url."
          } else {
            "Tavily could not extract $url: $reason"
          }
        )
      }

      val content = result.stringOf("raw_content")
      if (content.isBlank()) {
        throw SearchException("Tavily returned no readable content for $url.")
      }

      FetchedPage(url = result.stringOf("url").ifBlank { url }, content = content)
    }
}
