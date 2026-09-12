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

package com.itsaky.androidide.ai.provider

import com.google.gson.JsonObject

object PromptCache {

  private const val CHARS_PER_TOKEN = 4
  private const val MIN_CACHEABLE_TOKENS = 1024

  const val MIN_CACHEABLE_CHARS = MIN_CACHEABLE_TOKENS * CHARS_PER_TOKEN

  fun isWorthCaching(text: String): Boolean = text.length >= MIN_CACHEABLE_CHARS

  fun isWorthCaching(vararg parts: String): Boolean =
    parts.sumOf { it.length } >= MIN_CACHEABLE_CHARS

  fun ephemeral(): JsonObject {
    val control = JsonObject()
    control.addProperty("type", "ephemeral")
    return control
  }
}
