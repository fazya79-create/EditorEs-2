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

/** Firecrawl v2: web search plus markdown scraping of a single page. */
object FirecrawlSearchProvider : WebSearchProvider {

  private const val SEARCH_ENDPOINT = "https://api.firecrawl.dev/v2/search"
  private const val SCRAPE_ENDPOINT = "https://api.firecrawl.dev/v2/scrape"

  override val kind: SearchProviderKind = SearchProviderKind.FIRECRAWL

  override val supportsFetch: Boolean = true

  override suspend fun search(
    query: String,
    apiKey: String,
    limit: Int
  ): SearchResults = withContext(Dispatchers.IO) {
    val body = JsonObject()
    body.addProperty("query", query)
    body.addProperty("limit", limit)

    val response = HttpJson.post(
      url = SEARCH_ENDPOINT,
      headers = mapOf("Authorization" to "Bearer $apiKey"),
      body = body.toString()
    )

    SearchResults(query = query, items = parseResults(response))
  }

  override suspend fun fetch(url: String, apiKey: String): FetchedPage =
    withContext(Dispatchers.IO) {
      val formats = JsonArray()
      formats.add("markdown")

      val body = JsonObject()
      body.addProperty("url", url)
      body.add("formats", formats)
      body.addProperty("onlyMainContent", true)

      val response = HttpJson.post(
        url = SCRAPE_ENDPOINT,
        headers = mapOf("Authorization" to "Bearer $apiKey"),
        body = body.toString()
      )

      val data = response.getAsJsonObject("data")
        ?: throw SearchException("Firecrawl returned no content for $url.")

      val content = data.stringOf("markdown")
      if (content.isBlank()) {
        throw SearchException("Firecrawl returned no readable content for $url.")
      }

      val sourceUrl = data.getAsJsonObject("metadata")?.stringOf("sourceURL").orEmpty()
      FetchedPage(url = sourceUrl.ifBlank { url }, content = content)
    }

  /** Firecrawl nests results per source, e.g. `data.web`, and older responses use a plain array. */
  internal fun parseResults(response: JsonObject): List<SearchResultItem> {
    val data = response.get("data") ?: return emptyList()
    val entries = when {
      data.isJsonArray -> data.asJsonArray
      data.isJsonObject -> data.asJsonObject.getAsJsonArray("web") ?: JsonArray()
      else -> JsonArray()
    }

    return entries.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
      .map { result ->
        SearchResultItem(
          title = result.stringOf("title"),
          url = result.stringOf("url"),
          snippet = result.stringOf("description"),
          publishedDate = result.stringOf("date")
        )
      }
  }
}
