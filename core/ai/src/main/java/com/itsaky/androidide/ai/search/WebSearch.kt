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

enum class SearchProviderKind {
  TAVILY,
  BRAVE;

  val label: String
    get() = when (this) {
      TAVILY -> "Tavily"
      BRAVE -> "Brave Search"
    }
}

data class SearchResultItem(
  val title: String,
  val url: String,
  val snippet: String,
  val publishedDate: String = ""
)

data class SearchResults(
  val query: String,
  val answer: String = "",
  val items: List<SearchResultItem> = emptyList()
)

data class FetchedPage(
  val url: String,
  val content: String
)

class SearchException(message: String) : RuntimeException(message)

interface WebSearchProvider {

  val kind: SearchProviderKind

  val supportsFetch: Boolean
    get() = false

  suspend fun search(query: String, apiKey: String, limit: Int): SearchResults

  suspend fun fetch(url: String, apiKey: String): FetchedPage =
    throw SearchException("${kind.label} cannot fetch page contents.")
}

object WebSearchProviders {

  fun of(kind: SearchProviderKind): WebSearchProvider = when (kind) {
    SearchProviderKind.TAVILY -> TavilySearchProvider
    SearchProviderKind.BRAVE -> BraveSearchProvider
  }
}
