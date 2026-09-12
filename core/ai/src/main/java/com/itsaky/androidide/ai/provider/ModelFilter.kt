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

import java.util.Locale

/** Filters a fetched model listing by a user-typed query. */
object ModelFilter {

  /**
   * Returns the models whose id contains every whitespace-separated term of [query], ignoring case.
   * Terms may match in any order, so "4 gpt" finds "gpt-4o". A blank query keeps every model.
   */
  fun filter(models: List<ModelInfo>, query: String): List<ModelInfo> {
    val terms = query.lowercase(Locale.ROOT).split(' ', '\t', '\n').filter { it.isNotBlank() }
    if (terms.isEmpty()) {
      return models
    }

    return models.filter { model ->
      val id = model.id.lowercase(Locale.ROOT)
      terms.all { term -> id.contains(term) }
    }
  }
}
