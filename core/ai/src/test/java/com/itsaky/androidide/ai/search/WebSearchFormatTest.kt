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

import com.google.common.truth.Truth.assertThat
import com.itsaky.androidide.ai.tools.WebFetchTool
import com.itsaky.androidide.ai.tools.WebSearchTool
import org.junit.Test

class WebSearchFormatTest {

  private val tool = WebSearchTool()

  private fun format(results: SearchResults): String {
    val method = tool.javaClass.getDeclaredMethod("format", SearchResults::class.java)
    method.isAccessible = true
    return method.invoke(tool, results) as String
  }

  @Test
  fun `results are rendered as numbered title url snippet entries`() {
    val output = format(
      SearchResults(
        query = "kotlin coroutines",
        answer = "Coroutines are Kotlin's concurrency primitive.",
        items = listOf(
          SearchResultItem(
            title = "Coroutines guide",
            url = "https://kotlinlang.org/docs/coroutines-guide.html",
            snippet = "A   guide\nwith   whitespace.",
            publishedDate = "2025-01-01"
          ),
          SearchResultItem(
            title = "Second",
            url = "https://example.com",
            snippet = "Another result."
          )
        )
      )
    )

    assertThat(output).contains("Search results for: kotlin coroutines")
    assertThat(output).contains("Provider summary: Coroutines are Kotlin's concurrency primitive.")
    assertThat(output).contains("1. Coroutines guide")
    assertThat(output).contains("url: https://kotlinlang.org/docs/coroutines-guide.html")
    assertThat(output).contains("published: 2025-01-01")
    assertThat(output).contains("A guide with whitespace.")
    assertThat(output).contains("2. Second")
  }

  @Test
  fun `long snippets are capped so a search cannot flood the context window`() {
    val output = format(
      SearchResults(
        query = "q",
        items = listOf(SearchResultItem("t", "https://example.com", "x".repeat(10_000)))
      )
    )

    assertThat(output.length).isLessThan(2_000)
  }

  @Test
  fun `an empty result set is reported as text instead of an error`() {
    assertThat(format(SearchResults(query = "nothing here")))
      .isEqualTo("No results were found for 'nothing here'.")
  }

  @Test
  fun `the tool is read only and declares a query parameter`() {
    assertThat(tool.spec.mutating).isFalse()
    assertThat(tool.spec.name).isEqualTo(WebSearchTool.NAME)
    assertThat(tool.spec.parametersSchemaJson).contains("\"query\"")
  }

  @Test
  fun `fetched pages are labelled with their url and truncated when long`() {
    val fetchTool = WebFetchTool()
    val method = fetchTool.javaClass.getDeclaredMethod("format", FetchedPage::class.java)
    method.isAccessible = true

    val short = method.invoke(
      fetchTool,
      FetchedPage("https://example.com", "  Short page body.  ")
    ) as String
    assertThat(short).isEqualTo("Contents of https://example.com\n\nShort page body.")

    val long = method.invoke(
      fetchTool,
      FetchedPage("https://example.com", "x".repeat(100_000))
    ) as String
    assertThat(long).contains("characters omitted")
    assertThat(long.length).isLessThan(50_000)
  }

  @Test
  fun `web_fetch is read only and only accepts absolute http urls`() {
    val fetchTool = WebFetchTool()
    assertThat(fetchTool.spec.mutating).isFalse()
    assertThat(fetchTool.spec.name).isEqualTo(WebFetchTool.NAME)

    val method = fetchTool.javaClass.getDeclaredMethod("isHttpUrl", String::class.java)
    method.isAccessible = true

    assertThat(method.invoke(fetchTool, "https://example.com") as Boolean).isTrue()
    assertThat(method.invoke(fetchTool, "http://example.com/a?b=c") as Boolean).isTrue()
    assertThat(method.invoke(fetchTool, "file:///etc/passwd") as Boolean).isFalse()
    assertThat(method.invoke(fetchTool, "example.com") as Boolean).isFalse()
    assertThat(method.invoke(fetchTool, "not a url") as Boolean).isFalse()
  }

  @Test
  fun `only providers that implement extraction advertise fetch support`() {
    assertThat(TavilySearchProvider.supportsFetch).isTrue()
    assertThat(FirecrawlSearchProvider.supportsFetch).isTrue()
    assertThat(ExaSearchProvider.supportsFetch).isTrue()
    assertThat(SerperSearchProvider.supportsFetch).isFalse()
  }

  @Test
  fun `every provider kind resolves to the implementation that declares it`() {
    SearchProviderKind.entries.forEach { kind ->
      assertThat(WebSearchProviders.of(kind).kind).isEqualTo(kind)
      assertThat(kind.label).isNotEmpty()
    }
  }
}
