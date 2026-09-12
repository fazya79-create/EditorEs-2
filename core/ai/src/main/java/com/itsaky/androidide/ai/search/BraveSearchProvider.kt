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

import com.itsaky.androidide.ai.search.HttpJson.stringOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

object BraveSearchProvider : WebSearchProvider {

  private const val ENDPOINT = "https://api.search.brave.com/res/v1/web/search"
  private const val MAX_COUNT = 20

  override val kind: SearchProviderKind = SearchProviderKind.BRAVE

  override suspend fun search(
    query: String,
    apiKey: String,
    limit: Int
  ): SearchResults = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
    val count = limit.coerceIn(1, MAX_COUNT)

    val response = HttpJson.get(
      url = "$ENDPOINT?q=$encoded&count=$count",
      headers = mapOf(
        "X-Subscription-Token" to apiKey,
        "Accept-Encoding" to "gzip"
      )
    )

    val items = response.getAsJsonObject("web")
      ?.getAsJsonArray("results")
      ?.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject }
      ?.map { result ->
        SearchResultItem(
          title = result.stringOf("title"),
          url = result.stringOf("url"),
          snippet = result.stringOf("description"),
          publishedDate = result.stringOf("age")
        )
      }
      .orEmpty()

    SearchResults(query = query, items = items)
  }
}
