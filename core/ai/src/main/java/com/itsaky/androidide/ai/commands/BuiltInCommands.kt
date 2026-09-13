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

package com.itsaky.androidide.ai.commands

object BuiltInCommands {

  fun all(): List<SlashCommand> = listOf(COMMIT, REVIEW, EXPLAIN, TEST)

  private val COMMIT = SlashCommand(
    name = "commit",
    description = "Write a commit message for the current changes",
    builtIn = true,
    template = """
      Write a commit message for the changes in this project.

      Use run_shell to inspect the working tree before you write anything:
      `git status --short`, `git diff` and `git diff --cached`.

      Then propose a single conventional commit message:
      - a lowercase imperative subject, scoped by module, for example `fix(ai): ...`
      - a body explaining why the change was made when that is not obvious from the diff

      Show the message in a fenced block. Do not run `git commit` yourself unless I ask.

      ${'$'}ARGUMENTS
    """.trimIndent()
  )

  private val REVIEW = SlashCommand(
    name = "review",
    description = "Review the uncommitted changes in this project",
    builtIn = true,
    template = """
      Review the uncommitted changes in this project.

      Use run_shell to collect the diff (`git status --short`, then `git diff` and
      `git diff --cached`), and read_file for any surrounding code you need to judge a
      change in context.

      Report, in order of importance:
      - correctness bugs and broken edge cases
      - missing or now-wrong tests
      - anything that contradicts the conventions already used in the touched files

      Reference each finding by relative path. Say so plainly if the changes look fine.

      ${'$'}ARGUMENTS
    """.trimIndent()
  )

  private val EXPLAIN = SlashCommand(
    name = "explain",
    description = "Explain a file or symbol in this project",
    builtIn = true,
    template = """
      Explain the following part of this project: ${'$'}ARGUMENTS

      Read the relevant files first. Describe what it does, how it fits into the rest of
      the project, and anything about it that would surprise someone reading it for the
      first time. Do not change any files.
    """.trimIndent()
  )

  private val TEST = SlashCommand(
    name = "test",
    description = "Run the project's tests and triage failures",
    builtIn = true,
    template = """
      Run this project's tests with run_shell and triage whatever fails.

      If I named a target, run that: ${'$'}ARGUMENTS
      Otherwise work out the right command from the build files before running anything.

      For each failure, identify the cause in the source rather than only quoting the
      output, and propose the smallest fix. Put build and test output in fenced blocks.
    """.trimIndent()
  )
}
