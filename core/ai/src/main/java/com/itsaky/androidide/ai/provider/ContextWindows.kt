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

object ContextWindows {

  private val KNOWN = listOf(
    Rule(ProviderKind.OPENAI, Regex("^gpt-3\\.5"), 16_385),
    Rule(ProviderKind.OPENAI, Regex("^gpt-4-turbo"), 128_000),
    Rule(ProviderKind.OPENAI, Regex("^gpt-4(-|$)"), 8_192),
    Rule(ProviderKind.OPENAI, Regex("^gpt-4o"), 128_000),
    Rule(ProviderKind.OPENAI, Regex("^gpt-4\\.1"), 1_047_576),
    Rule(ProviderKind.OPENAI, Regex("^o[134](-|$)"), 200_000),
    Rule(ProviderKind.OPENAI, Regex("^gpt-5(\\.[0-3])?(-|$)"), 400_000),
    Rule(ProviderKind.OPENAI, Regex("^gpt-[56]"), 1_050_000),

    Rule(ProviderKind.ANTHROPIC, Regex("^claude-3"), 200_000),
    Rule(ProviderKind.ANTHROPIC, Regex("^claude-haiku"), 200_000),
    Rule(ProviderKind.ANTHROPIC, Regex("^claude-opus-4-5"), 200_000),
    Rule(ProviderKind.ANTHROPIC, Regex("^claude-sonnet-4-5"), 1_000_000),
    Rule(ProviderKind.ANTHROPIC, Regex("^claude-(opus|sonnet|fable|mythos)-(4-[6-9]|5)"), 1_000_000),

    Rule(ProviderKind.GOOGLE, Regex("^gemini-\\d"), 1_048_576)
  )

  fun of(kind: ProviderKind, model: String): Int {
    val id = model.substringAfterLast('/').lowercase()
    return KNOWN.filter { it.kind == kind && it.pattern.containsMatchIn(id) }
      .maxByOrNull { it.pattern.pattern.length }
      ?.tokens
      ?: 0
  }

  private class Rule(val kind: ProviderKind, val pattern: Regex, val tokens: Int)
}
