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

package com.itsaky.androidide.ai.tools

import android.content.Context
import com.google.gson.JsonObject
import com.itsaky.androidide.ai.model.ToolSpec
import com.itsaky.androidide.ai.net.HttpStatusException
import com.itsaky.androidide.ai.prefs.AiPreferences
import com.itsaky.androidide.ai.prefs.SecretStore
import com.itsaky.androidide.ai.search.SearchException
import com.itsaky.androidide.ai.search.SearchResults
import com.itsaky.androidide.ai.search.WebSearchProviders
import kotlinx.coroutines.CancellationException
import java.io.IOException

class WebSearchTool : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Search the web and return the most relevant results as titles, URLs and " +
        "short snippets. Use it for information that is newer than your training data or that " +
        "you cannot find in the project, and cite the URLs you rely on.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "query": {
            "type": "string",
            "description": "The search query. Use focused keywords rather than a full sentence."
          },
          "max_results": {
            "type": "integer",
            "description": "How many results to return. Defaults to the configured limit."
          }
        },
        "required": ["query"]
      }
    """.trimIndent(),
    mutating = false
  )

  override fun describe(arguments: JsonObject): String =
    "Search the web for ${arguments.get("query")?.asStringOrNull() ?: "<missing query>"}"

  override suspend fun execute(context: Context, arguments: JsonObject): String {
    val query = arguments.get("query")?.asStringOrNull()?.trim()
      ?: throw ToolException("Missing required argument 'query'.")
    if (query.isEmpty()) {
      throw ToolException("The 'query' argument must not be empty.")
    }

    val kind = AiPreferences.searchProviderKind()
    val apiKey = SecretStore(context.applicationContext)
      .get(AiPreferences.searchApiKeyPrefKey(kind))
    if (apiKey.isBlank()) {
      throw ToolException(
        "No API key is configured for ${kind.label}. Add one in Preferences > AI assistant " +
            "before using $NAME."
      )
    }

    val limit = (
      arguments.get("max_results")
        ?.takeIf { it.isJsonPrimitive }
        ?.let { runCatching { it.asInt }.getOrNull() }
        ?: AiPreferences.searchResultLimit
      ).coerceIn(1, MAX_RESULTS)

    val results = try {
      WebSearchProviders.of(kind).search(query, apiKey, limit)
    } catch (err: CancellationException) {
      throw err
    } catch (err: HttpStatusException) {
      throw ToolException("${kind.label} rejected the search (HTTP ${err.status}).")
    } catch (err: SearchException) {
      throw ToolException(err.message ?: "The search provider returned an unexpected response.")
    } catch (err: IOException) {
      throw ToolException(
        "Could not reach ${kind.label}: ${err.message ?: err.javaClass.simpleName}"
      )
    }

    return format(results.copy(items = results.items.take(limit)))
  }

  private fun format(results: SearchResults): String {
    if (results.items.isEmpty() && results.answer.isBlank()) {
      return "No results were found for '${results.query}'."
    }

    return buildString {
      append("Search results for: ").append(results.query).append('\n')
      if (results.answer.isNotBlank()) {
        append("\nProvider summary: ").append(results.answer.take(MAX_ANSWER_CHARS)).append('\n')
      }

      results.items.forEachIndexed { index, item ->
        append('\n').append(index + 1).append(". ").append(item.title.ifBlank { "(untitled)" })
        append('\n').append("   url: ").append(item.url)
        if (item.publishedDate.isNotBlank()) {
          append('\n').append("   published: ").append(item.publishedDate)
        }
        if (item.snippet.isNotBlank()) {
          append('\n').append("   ").append(collapse(item.snippet).take(MAX_SNIPPET_CHARS))
        }
        append('\n')
      }
    }.trimEnd()
  }

  private fun collapse(text: String): String = text.replace(WHITESPACE, " ").trim()

  companion object {

    const val NAME = "web_search"

    private const val MAX_RESULTS = 10
    private const val MAX_ANSWER_CHARS = 1_200
    private const val MAX_SNIPPET_CHARS = 600

    private val WHITESPACE = Regex("\\s+")
  }
}
