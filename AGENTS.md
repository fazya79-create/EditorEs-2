# AGENTS.md

AndroidIDE is an Android application, written in Kotlin and Java, that builds **native
C/C++ projects on-device with CMake and the Android NDK**. It ships an embedded Termux
runtime, a PRoot-based Ubuntu guest that hosts the toolchain, a code editor, a C/C++
language server, and an AI chat assistant.

This file is the entry point for coding agents. `CONTRIBUTING.md` remains the reference for
human contributors; where the two overlap, `CONTRIBUTING.md` wins.

## Scope: this is not an Android app builder

Read this before planning any feature work, because it is the single most common wrong
assumption about this project.

**AndroidIDE does not build Android applications and is not heading that way.** It is not a
mobile Android Studio. There is no Gradle-based APK pipeline, no `assembleDebug` of a user
project, no AGP integration, no app signing, no emulator or `installDebug` flow, and none of
these are planned. The Gradle Tooling API client that older revisions carried has been
removed from `core/backend` entirely.

What it actually does:

- Builds **native C/C++ targets** through CMake presets driven by Ninja and the NDK
  toolchain (`CmakePresets.kt`, `BuildRunner.kt`).
- Runs the toolchain inside a **PRoot Ubuntu 24.04 guest** (`ProotConfig.kt`,
  `UbuntuInstaller.kt`), with CMake at `/opt/cmake` and the NDK at `/opt/ndk`.
- Offers exactly two project templates: **C++ executable** and **C++ shared library**
  (`utilities/templates-impl/.../cppExecutable`, `.../cppSharedLibrary`).
- Ships one language server: **C/C++** (`core/app/.../lsp/cpp/CppLanguageServer.kt`).
- Uses APK handling only for **patching and native-library injection into an existing APK**
  (`ApkPatchEngine.kt`, `NativeLibraryInjectionDialogFragment.kt`) — that is post-processing
  a prebuilt APK, not compiling an Android app from source.

Practical consequences when you work here:

- Do not propose, scaffold, or "restore" Gradle/AGP project-build support, Java/Kotlin
  Android compilation, APK assembly from source, or an Android app template.
- Do not reintroduce the Gradle Tooling API into `core/backend`.
- A user's "project" means a CMake project with a `CMakeLists.txt` and presets, not a Gradle
  project with modules and variants.
- The Gradle commands in this file build **AndroidIDE itself**. They have nothing to do with
  how the IDE builds a user's project.

## Project layout

Gradle multi-module build. Modules are declared in `settings.gradle.kts`.

| Path | Purpose |
| --- | --- |
| `core/app` | The Android application. Activities, fragments, preference screens, DI wiring. |
| `core/ai` | AI chat assistant: providers, model catalog, agent loop, tools, chat UI. |
| `core/actions` | Registry for editor and toolbar actions, including the CMake build actions. |
| `core/backend` | On-device build backend: CMake presets, Ninja/NDK runner, toolchain installer, PRoot Ubuntu guest. |
| `core/common` | Shared utilities used across modules. |
| `core/lsp-api`, `core/lsp-models` | Language server contracts and data models. |
| `core/projects` | Project model, module discovery, indexing, `ClangFormat`. |
| `core/resources` | Shared strings, themes and drawables. `string.*` ids resolve here. |
| `editor/api`, `editor/impl` | Editor surface and its implementation. |
| `editor/lexers`, `editor/treesitter` | Syntax highlighting grammars. Tree-sitter covers Kotlin, JSON, log, Python, XML and properties; C/C++ uses the ANTLR lexer so its language server keeps working, and shell uses a pattern-based analyzer because no tree-sitter grammar is published for it. |
| `event/*` | EventBus abstraction plus the Android and event-model artifacts. |
| `logging/logger` | Logback/SLF4J setup, `ILogger`, logcat and stderr appenders. |
| `termux/*` | Vendored Termux: application, terminal emulator, view, shared code. |
| `utilities/*` | Preferences, templates (C++ only), tree view, build info, framework stubs. |
| `annotation/processors` | kapt processors used by `core/app`. |
| `composite-builds/build-logic` | Convention plugins. SDK and Java versions live here. |
| `composite-builds/build-deps` | Vendored third-party dependencies built from source. |
| `testing/commonTest`, `testing/unitTest` | Shared test helpers. |

Do not edit anything under `termux/` or `composite-builds/build-deps/` unless the task is
explicitly about the vendored code. These track upstream sources.

## Dev environment

- JDK 17. CI pins Temurin 17; `CONTRIBUTING.md` recommends the JDK bundled with Android Studio.
- Android SDK path goes in `local.properties` as `sdk.dir=...`. This file is not committed.
- `compileSdk = 36`, `minSdk = 26`, `targetSdk = 28`, Java source/target 11. These are
  defined once in `composite-builds/build-logic/common/.../BuildConfig.kt`. Change them
  there, never per-module.
- The build resolves dependencies from Maven Central, Sonatype snapshots and JitPack, so
  `--offline` fails on a cold cache. Run online at least once.

### Mixed line endings

There is no `.gitattributes`, and roughly 26 of the ~400 Kotlin/Java sources use CRLF while
the rest use LF — `EditorActivityActions.kt` and `FormatCodeAction.kt` are CRLF,
`CodeEditorView.kt` is LF. Check with `file <path>` before a scripted edit, and preserve
whatever the file already uses. Do not normalise a file's endings as a side effect of an
unrelated change: it turns a two-line diff into a whole-file rewrite and buries the real
change from reviewers.

## Build and test commands

These build **AndroidIDE itself**. They are unrelated to the CMake/NDK pipeline the IDE runs
for a user's project. Run everything through the wrapper from the repository root.

```bash
./gradlew :core:ai:testDebugUnitTest          # unit tests for one module
./gradlew :core:app:compileDebugKotlin        # fastest check that the app still compiles
./gradlew :core:app:assembleDebug             # full debug APK, same target CI builds
./gradlew :core:ai:testDebugUnitTest --tests '*ModelCatalogTest*'   # one test class
```

Prefer the narrowest task that proves your change. A full `assembleDebug` takes far longer
than compiling the modules you touched, and module-scoped test tasks are usually enough.

CI (`.github/workflows/build.yml`) runs `./gradlew :core:app:assemble{Debug,Release}` and
ignores changes to `**.md`. It does not currently run the unit tests, so a green CI badge is
not evidence your tests pass. Run them locally.

### Known flaky test

`AiChatSessionDeletionTest > starting a new chat right after a delete does not resurrect the
deleted conversation` fails intermittently (roughly 1 run in 4) and does so on a pristine
checkout. If it is the only failure, re-run before assuming your change caused it. Do not
"fix" it by weakening the assertion.

## Code style

From `CONTRIBUTING.md`:

- 2-space indentation everywhere.
- Kotlin: `ktfmt`, Google (internal) style.
- Java: Google Java Style (`google-java-format`).
- XML: Android Studio default formatter, 2-space indents.

House conventions that the formatter cannot enforce:

- Every source file carries the GPLv3 header block. Copy it from a neighbouring file when
  creating a new one.
- Prefer a `private val log = LoggerFactory.getLogger(Foo::class.java)` in the companion
  object, matching `SecretStore`, `ChatHistoryStore` and `ToolGate`.
- Diagnostic logging belongs at `log.debug`. Never log an API key, bearer token, request
  authorization header, or full user prompt.
- Test names are backtick-quoted sentences describing the behaviour, not the method:
  `` fun `a malformed body yields no models instead of failing`() ``.
- Assertions use Truth (`assertThat(...)`), tests use JUnit 4, and tests that need a
  `Context` or `prefManager` use `@RunWith(RobolectricTestRunner::class)` with
  `@Config(application = BaseApplication::class)`.

### Comments

Comment intent, not mechanics: a non-obvious constraint, an invariant, a workaround, or a
decision that would otherwise look wrong. Obvious code needs no comment. Do not leave
`TODO`/`FIXME` markers. Do not restate the code in prose.

## Architecture notes for the editor

`IDEEditor.createLanguage` resolves a file's language in a fixed order, and the order is
load-bearing:

1. Not a real file → `EmptyLanguage`.
2. A tree-sitter language registered for the extension → that language.
3. Otherwise the lexer-based table: C/C++ (`CppLanguage`), shell (`ShellLanguage`).
4. Otherwise `EmptyLanguage`.

Because step 2 wins over step 3, **registering a tree-sitter grammar for an extension
silently detaches the lexer-based language for it.** C and C++ grammars exist upstream but
are deliberately not registered: doing so would replace `CppLanguage` and disable the C/C++
language server — completions, diagnostics and formatting — with no compile error to warn
you. Adding a language for an extension that already has a lexer-based implementation is a
behaviour change, not an addition.

Other things that bite here:

- Tree-sitter languages are registered in `EditorHandlerActivity`, and each needs three
  pieces: the grammar dependency, `assets/editor/treesitter/<type>/highlights.scm`, and an
  `assets/editor/schemes/<theme>/<type>.json` referenced from the theme's `languages` array.
  Miss the scheme and highlighting silently does nothing.
- Bump `scheme.version` in `scheme.prop` when you change bundled schemes, otherwise devices
  keep serving the cached copy.
- `BundledSchemesTest` parses every bundled scheme, so a dangling `@file.json` reference
  fails the build rather than the runtime loader. Add new language types to its list.
- Shell has no tree-sitter grammar published in the AndroidIDE tree-sitter repository, which
  is why `ShellAnalyzer` walks the text itself. Check what is actually published before
  planning a language: the repository ships aidl, c, cpp, java, json, kotlin, log,
  properties, python and xml — and nothing else.

## Architecture notes for `core/ai`

Worth knowing before touching the assistant, because the invariants are easy to break:

- `ProviderKind` is `OPENAI`, `ANTHROPIC` or `GOOGLE`. The OpenAI kind doubles as the
  "OpenAI-compatible" kind, so any third-party gateway arrives through it.
- `ModelCatalog.fetch` lists models; `ModelCatalog.detectContextWindow` reads one model.
  Context windows come from the server. `openAiContextWindow` probes a list of known field
  names (`context_length`, `context_window`, `max_model_len`, …) plus nested containers,
  because every gateway spells it differently.
- There is deliberately **no** regex table mapping model names to context windows. It was
  removed: guessing from a name is wrong whenever the same model id is served by different
  gateways with different real limits. When the server reports nothing, fall back to
  `AiPreferences.DEFAULT_CONTEXT_WINDOW` and tell the user, rather than inventing a number.
- A detected context window is keyed by **model *and* endpoint**
  (`ide.ai.<provider>.contextWindow.endpoint`). Changing the base URL invalidates the stored
  value and triggers re-detection. Never reintroduce a model-name-only cache.
- Secrets go through `SecretStore` (AES-GCM via the Android keystore), never `prefManager`.
  `AiPreferences` holds non-secret settings only.

## Testing instructions

Only `core/ai` (~25 test files), `core/app` (~4) and `editor/impl` (~3) have unit tests;
`core/backend` and `core/projects` have no `src/test` at all. If you change those modules,
compiling is the only automated signal you get — say so rather than implying they were
covered.

- Add or update tests for the behaviour you change, even if nobody asked.
- A test must fail before your fix and pass after it. If it passes both ways, it is not
  testing the thing you claim to have fixed. Prove it: stash your change, watch the test
  fail, unstash, watch it pass.
- Never weaken or delete an assertion to get a green run. Fix the cause.
- Tests needing `Context` or `prefManager` must use Robolectric. A plain JUnit test calling
  anything that reaches `prefManager` fails with a `BaseApplication.getBaseInstance()` NPE —
  either add `@RunWith(RobolectricTestRunner::class)` or extract the pure logic into a
  function the test can call without Android.
- Do not put live credentials, private base URLs, or network calls to third-party services
  into committed tests. Gate any manual live probe behind an environment variable and delete
  the scaffolding before you finish.
- When you delete code, delete it to the root: the symbol, its imports, its tests, its
  string resources, and the file itself if nothing is left. Rename the file when its
  remaining contents no longer match its name. Leave no dead code behind.
- Before claiming a resource, asset or drawable ships, confirm it is in the built APK
  (`unzip -l core/app/build/outputs/apk/debug/*.apk | grep ...`). Compilation does not prove
  packaging.

## Commit and PR instructions

- Conventional commits scoped by module: `feat(ai): ...`, `fix(terminal,ai): ...`,
  `revert(ai): ...`. Subject in lowercase imperative mood.
- Explain *why* in the body when the reason is not obvious from the diff. The existing
  history does this; match it.
- `main` is protected: commits must be GPG-signed and history must stay linear.
- Before opening a PR, run the unit tests for every module you touched and compile
  `:core:app`. State in the PR which commands you ran.
- Put crash logs and build output in fenced code blocks.
- Never commit `local.properties`, keystores, or API keys.

## Safety

- Do not run destructive git commands (`push --force`, `reset --hard` on shared branches,
  history rewrites) without being asked.
- Do not push or open a PR when the task says to verify locally.
- Treat anything under `termux/` as vendored: patch it only with a stated reason.
