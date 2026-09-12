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
import com.itsaky.androidide.ai.search.FetchedPage
import com.itsaky.androidide.ai.search.SearchException
import com.itsaky.androidide.ai.search.WebSearchProviders
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.URI

class WebFetchTool : AiTool {

  override val spec = ToolSpec(
    name = NAME,
    description = "Fetch the readable contents of a single web page as text. Use it after " +
        "$SEARCH_TOOL when a result's snippet is not enough, or when the user points at a " +
        "specific URL. Long pages are truncated.",
    parametersSchemaJson = """
      {
        "type": "object",
        "properties": {
          "url": {
            "type": "string",
            "description": "Absolute http or https URL of the page to fetch."
          }
        },
        "required": ["url"]
      }
    """.trimIndent(),
    mutating = false
  )

  override fun describe(arguments: JsonObject): String =
    "Fetch ${arguments.get("url")?.asStringOrNull() ?: "<missing url>"}"

  override suspend fun execute(context: Context, arguments: JsonObject): String {
    val url = arguments.get("url")?.asStringOrNull()?.trim()
      ?: throw ToolException("Missing required argument 'url'.")
    if (!isHttpUrl(url)) {
      throw ToolException("The 'url' argument must be an absolute http or https URL.")
    }

    val kind = AiPreferences.searchProviderKind()
    val provider = WebSearchProviders.of(kind)
    if (!provider.supportsFetch) {
      throw ToolException(
        "${kind.label} cannot fetch page contents. Switch the search provider in " +
            "Preferences > AI assistant to one that supports it, or rely on $SEARCH_TOOL results."
      )
    }

    val apiKey = SecretStore(context.applicationContext)
      .get(AiPreferences.searchApiKeyPrefKey(kind))
    if (apiKey.isBlank()) {
      throw ToolException(
        "No API key is configured for ${kind.label}. Add one in Preferences > AI assistant " +
            "before using $NAME."
      )
    }

    val page = try {
      provider.fetch(url, apiKey)
    } catch (err: CancellationException) {
      throw err
    } catch (err: HttpStatusException) {
      throw ToolException("${kind.label} rejected the fetch (HTTP ${err.status}).")
    } catch (err: SearchException) {
      throw ToolException(err.message ?: "The provider returned an unexpected response.")
    } catch (err: IOException) {
      throw ToolException(
        "Could not reach ${kind.label}: ${err.message ?: err.javaClass.simpleName}"
      )
    }

    return format(page)
  }

  private fun isHttpUrl(url: String): Boolean {
    val scheme = runCatching { URI(url).scheme }.getOrNull()?.lowercase()
    return scheme == "http" || scheme == "https"
  }

  private fun format(page: FetchedPage): String {
    val content = page.content.trim()
    val body = if (content.length > MAX_CHARS) {
      content.take(MAX_CHARS) +
          "\n... [truncated, ${content.length - MAX_CHARS} characters omitted]"
    } else {
      content
    }

    return buildString {
      append("Contents of ").append(page.url).append('\n').append('\n')
      append(body)
    }
  }

  companion object {

    const val NAME = "web_fetch"

    private const val SEARCH_TOOL = WebSearchTool.NAME

    private const val MAX_CHARS = 40_000
  }
}
