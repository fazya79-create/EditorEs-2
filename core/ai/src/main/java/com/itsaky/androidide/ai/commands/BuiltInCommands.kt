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

  fun all(): List<SlashCommand> = listOf(COMMIT, REVIEW, EXPLAIN, TEST, INIT, BUILD, FIX, DOCS)

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

  private val INIT = SlashCommand(
    name = "init",
    description = "Write or refresh AGENTS.md for this project",
    builtIn = true,
    template = """
      Create or update `AGENTS.md` at the root of this project so a future assistant session
      can work here without rediscovering everything.

      Investigate first. Read the CMakeLists.txt and any CMake presets, the README, and
      enough of the sources to know what the project actually builds and how its directories
      are organised. Use glob and grep rather than guessing at paths.

      Include only what an assistant would otherwise get wrong:
      - the exact commands that configure, build and test this project
      - how the targets and directories relate to each other
      - conventions this project uses that differ from the language defaults
      - anything surprising: generated files, required environment, awkward dependencies

      Leave out generic programming advice, tutorials and anything you could not verify from
      the project itself. If `AGENTS.md` already exists, improve it in place: keep what is
      still true, drop what is stale, and say what you changed.

      ${'$'}ARGUMENTS
    """.trimIndent()
  )

  private val BUILD = SlashCommand(
    name = "build",
    description = "Configure and build this project, then triage errors",
    builtIn = true,
    template = """
      Build this project and get it to compile cleanly.

      Read its CMakeLists.txt and presets first so you run the configure and build the way
      this project expects, then run them with run_shell.

      If I named a target or preset, use it: ${'$'}ARGUMENTS

      For every error, read the source around it before proposing anything, fix the cause
      rather than silencing the warning, and rebuild to confirm. Report the commands you ran
      and put compiler output in fenced blocks.
    """.trimIndent()
  )

  private val FIX = SlashCommand(
    name = "fix",
    description = "Diagnose and fix a described bug or error",
    builtIn = true,
    template = """
      Diagnose and fix this: ${'$'}ARGUMENTS

      Work from evidence rather than a guess. Use grep and glob to find the code involved,
      read it, and identify the actual cause before you change anything. If the report
      includes an error message or stack trace, trace it to the line it came from.

      Then make the smallest change that fixes the cause, and verify it by building or
      running the relevant test with run_shell. Tell me what was wrong, why it happened, and
      what you changed. If you cannot reproduce it, say so and explain what you would need.
    """.trimIndent()
  )

  private val DOCS = SlashCommand(
    name = "docs",
    description = "Document a file, symbol or subsystem",
    builtIn = true,
    template = """
      Write documentation for: ${'$'}ARGUMENTS

      Read the relevant code first and describe what it actually does, not what its names
      suggest. Cover its purpose, how callers are meant to use it, the parameters and return
      values that are not self-explanatory, and any constraint or invariant a caller could
      break without noticing.

      Match the documentation style already used in the files you touched. If the project has
      no convention yet, keep it plain and say what you chose.
    """.trimIndent()
  )
}
