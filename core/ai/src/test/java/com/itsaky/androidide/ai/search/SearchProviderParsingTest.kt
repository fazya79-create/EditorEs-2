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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Test

/**
 * Locks the response shapes observed from the live APIs, so a parser regression is caught without
 * spending a request or an API key.
 */
class SearchProviderParsingTest {

  private fun json(body: String): JsonObject = JsonParser.parseString(body).asJsonObject

  @Test
  fun `serper reads organic results and their dates`() {
    val response = json(
      """
      {
        "searchParameters": {"q": "kotlin coroutines guide"},
        "organic": [
          {
            "title": "Coroutines guide",
            "link": "https://kotlinlang.org/docs/coroutines-guide.html",
            "snippet": "Table of contents",
            "date": "Aug 10, 2026",
            "position": 1
          },
          {
            "title": "Coroutine Essentials",
            "link": "https://typealias.com/start/kotlin-coroutines/",
            "snippet": "Discover the power of coroutines."
          }
        ],
        "credits": 1
      }
      """.trimIndent()
    )

    val items = SerperSearchProvider.parseResults(response)

    assertThat(items).hasSize(2)
    assertThat(items[0].url).isEqualTo("https://kotlinlang.org/docs/coroutines-guide.html")
    assertThat(items[0].publishedDate).isEqualTo("Aug 10, 2026")
    assertThat(items[1].publishedDate).isEmpty()
  }

  @Test
  fun `serper surfaces an answer box when the query has a direct answer`() {
    assertThat(SerperSearchProvider.parseAnswer(json("""{"organic": []}"""))).isEmpty()

    assertThat(
      SerperSearchProvider.parseAnswer(json("""{"answerBox": {"answer": "42"}}"""))
    ).isEqualTo("42")

    assertThat(
      SerperSearchProvider.parseAnswer(json("""{"answerBox": {"snippet": "A snippet"}}"""))
    ).isEqualTo("A snippet")
  }

  @Test
  fun `firecrawl reads results nested under data web`() {
    val response = json(
      """
      {
        "success": true,
        "data": {
          "web": [
            {
              "title": "Coroutines guide | Kotlin Documentation",
              "url": "https://kotlinlang.org/docs/coroutines-guide.html",
              "description": "Library guides"
            }
          ]
        },
        "creditsUsed": 1
      }
      """.trimIndent()
    )

    val items = FirecrawlSearchProvider.parseResults(response)

    assertThat(items).hasSize(1)
    assertThat(items[0].title).isEqualTo("Coroutines guide | Kotlin Documentation")
    assertThat(items[0].snippet).isEqualTo("Library guides")
  }

  @Test
  fun `firecrawl also accepts a plain data array and an empty payload`() {
    val array = json(
      """
      {"data": [{"title": "T", "url": "https://example.com", "description": "D"}]}
      """.trimIndent()
    )
    assertThat(FirecrawlSearchProvider.parseResults(array).map { it.url })
      .containsExactly("https://example.com")

    assertThat(FirecrawlSearchProvider.parseResults(json("{}"))).isEmpty()
    assertThat(FirecrawlSearchProvider.parseResults(json("""{"data": {}}"""))).isEmpty()
  }

  @Test
  fun `exa prefers a summary but falls back to the extracted text`() {
    val response = json(
      """
      {
        "requestId": "abc",
        "results": [
          {
            "title": "Coroutines guide",
            "url": "https://kotlinlang.org/docs/coroutines-guide.html",
            "publishedDate": "2026-08-10T00:00:00.000Z",
            "text": "Kotlin provides only minimal low-level APIs."
          },
          {
            "title": "With summary",
            "url": "https://example.com",
            "summary": "Short summary.",
            "text": "Much longer text."
          }
        ]
      }
      """.trimIndent()
    )

    val items = ExaSearchProvider.parseResults(response)

    assertThat(items[0].snippet).isEqualTo("Kotlin provides only minimal low-level APIs.")
    assertThat(items[0].publishedDate).isEqualTo("2026-08-10T00:00:00.000Z")
    assertThat(items[1].snippet).isEqualTo("Short summary.")
  }

  @Test
  fun `exa reports a per-url extraction failure instead of a generic message`() {
    val failed = json(
      """
      {
        "results": [],
        "statuses": [
          {
            "id": "https://example.com",
            "status": "error",
            "error": {"tag": "CRAWL_NOT_FOUND", "httpStatusCode": 404}
          }
        ]
      }
      """.trimIndent()
    )

    assertThat(ExaSearchProvider.statusReason(failed, "https://example.com"))
      .isEqualTo("Exa could not extract https://example.com: CRAWL_NOT_FOUND")

    assertThat(ExaSearchProvider.statusReason(json("{}"), "https://example.com"))
      .isEqualTo("Exa could not extract any content from https://example.com.")
  }
}
