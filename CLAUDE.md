# CLAUDE.md

Guidance for Claude Code working in the AndroidIDE repository.

All project facts — module layout, build and test commands, code style, `core/ai`
invariants, commit and PR rules — live in one place and are imported here:

@AGENTS.md

Read that first. Everything below is Claude-specific working method that does not belong in
a tool-agnostic file. When the two ever disagree, `AGENTS.md` wins on project facts.

## Do not assume this builds Android apps

The name misleads, so anchor on this before proposing anything: **AndroidIDE compiles native
C/C++ with CMake and the NDK inside a PRoot Ubuntu guest. It does not build Android
applications, and that is a deliberate, settled scope — not a gap to fill.**

There is no Gradle/AGP pipeline for user projects, no APK assembly from source, no app
signing, no emulator. `core/backend` holds a CMake/Ninja runner and a toolchain installer;
the Gradle Tooling API it once contained is gone. The only templates are C++ executable and
C++ shared library, and the only language server is C/C++.

So: never offer to "add back" Gradle project support, an Android app template, or Java/Kotlin
compilation of user code. If a request seems to assume the IDE builds APKs, say so and
confirm what the user actually wants before writing code. `AGENTS.md` has the full scope
section with the exact files.

Two traps worth naming:

- `./gradlew` commands in this repository build **AndroidIDE itself**. They say nothing about
  how the IDE builds a user's project.
- `ApkPatchEngine.kt` exists, but it patches and injects native libraries into an
  **already-built** APK. It is not an app-compilation path.

## Verify before you report

This repository gives you a real check for almost everything, so use it instead of stopping
at "looks done":

```bash
./gradlew :core:ai:testDebugUnitTest          # behaviour you changed in :core:ai
./gradlew :core:app:compileDebugKotlin        # the change still compiles app-wide
```

Rules that matter here:

- Never claim a build or test passed without having run it in this session. A plausible
  command is not evidence.
- `:core:app:compileDebugKotlin` pulls in ~450 tasks and takes over a minute on a cold
  daemon, and `assembleDebug` takes several minutes. Budget for it rather than killing it
  early; a long-running Gradle task is normal here, not a hang.
- A first Gradle run downloads dependencies. `--offline` fails on a cold cache; run online
  once before assuming the build is broken.
- A failing test is a result, not an obstacle. Read the actual stack trace before changing
  anything — a `BaseApplication.getBaseInstance()` NPE means the test needs Robolectric or
  the logic needs extracting, not that your production change was wrong.
- If the only failing test is the known flake named in `AGENTS.md`, prove which it is before
  blaming your change: run it in isolation, run it alongside your new tests, and run the
  suite on a stashed tree. Report the evidence, not the assumption.

## Investigate, do not theorise

When a bug report says "X should work but doesn't", get the ground truth first:

- Reproduce against the real thing — the actual HTTP endpoint, the actual stored preference
  — before editing code. `curl` the API yourself and read the raw bytes.
- Read the upstream server's source when its behaviour is in question. Knowing *why* a
  response is shaped a certain way beats guessing from the shape alone.
- Check the premise. A report can be wrong about the expected value; verify the number
  before you "fix" the code to produce it.
- Add temporary `log.debug` along the whole path, run it, then remove the scaffolding you do
  not want to ship.

### Bisect the chain before editing anything

A value that arrives wrong at the end of a pipeline is not evidence that the first stage is
broken. Instrument every hop — request, raw response, parse result, what gets stored, what
gets read back — and find the first hop where the value is already wrong. The fix belongs
there, not where you noticed the symptom.

The corollary is that a stage you proved correct stays off the table. Do not "improve" the
parser because the displayed number is wrong once logging shows the parser returned the
right value.

### When the premise and reality disagree, say so

If the ground truth contradicts what the request assumes — a different number, a different
model id, a feature that never existed — report the discrepancy before writing code.
Silently "fixing" the code to satisfy a wrong premise produces a change that is wrong in a
harder-to-find way.

## Beware the change that compiles and still breaks something

Kotlin will happily compile a registration that disables a feature at runtime. Before adding
something to a registry, dispatch table, or `when` branch, ask what it now takes precedence
over. The editor's language resolution is the live example: registering a tree-sitter
grammar for an extension silently detaches the lexer-based language and its language server,
with a green build.

When a change touches resources, assets or packaging, compiling is not the check — assemble
and look inside the artifact.

## Context discipline

Long sessions degrade as context fills. In a repository this size:

- Prefer `rg` with a narrow pattern over reading whole files. `aiPrefExts.kt` alone is over
  1000 lines and reading it repeatedly is expensive.
- Grep for a symbol's call sites before changing its signature; the Gradle error message
  arrives a minute later, a grep arrives instantly.
- Do not re-read a file you just wrote.

## Making changes

- Match the surrounding file. The conventions in `AGENTS.md` are descriptive of code that
  already exists — open a neighbouring file and copy its shape.
- Check `file <path>` before a scripted edit. This repository mixes CRLF and LF with no
  `.gitattributes`, and rewriting a file's endings turns a small diff into a whole-file
  rewrite that hides the real change.
- Re-read the exact region before replacing it, and re-read it after. A search-and-replace
  that swallows a closing brace still looks plausible in the tool output and only surfaces
  as a confusing syntax error a minute later.
- Make the smallest change that fixes the confirmed cause. Resist refactoring adjacent code
  that happens to be in view.
- Deleting means deleting to the root: symbol, imports, tests, string resources, and the
  file itself when nothing meaningful is left. Rename a file whose remaining contents no
  longer match its name.
- Never hardcode a credential, private base URL, or user prompt into the repository, and
  never log one. Use an environment variable for a one-off live probe and remove it after.

## Changing course is cheaper than finishing the wrong change

If evidence mid-task shows the approach is wrong — a dependency that does not exist, a
registration that would disable a working feature — stop and say so. Abandoning a
half-finished direction costs one message. Shipping it costs a regression that compiles.

Equally, do not widen the task because something adjacent looks improvable. Fix what was
asked, note the rest.

## Secrets and safety

- API keys given to you in chat are real credentials. Use them for the task at hand, keep
  them out of every file you write, and do not echo them into logs or commit messages.
- Before committing, scan what is actually staged — not the whole sandbox — for keys, tokens
  and private hostnames, and check the history too. Scratch files and tool logs outside the
  repository are not a leak; anything reachable from a commit is.
- `local.properties` and keystores are never committed.
- Do not push, force-push, rewrite history, or open a PR unless asked. When the task says
  verify locally, stop at local verification and report.

## Reporting back

- State exactly which commands you ran and what they returned.
- Separate what you confirmed from what you assume. If you could not verify something, say
  so plainly instead of implying success.
- Paste real output — the actual JSON, the actual log line — rather than describing it.
- Surface the decisions a reviewer cannot infer from the diff: what you deliberately did
  *not* do, and why. "I did not register the C/C++ grammar because it would disable the
  language server" is worth more than a list of files changed.
- Say it when your own earlier claim turns out to be wrong. A quiet correction buried in new
  work is worse than naming the mistake.
- The user writes in Indonesian; reply in Indonesian. Keep code, identifiers, commit
  messages, and file contents in English, matching the repository.
