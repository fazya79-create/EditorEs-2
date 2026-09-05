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

package com.itsaky.androidide.lsp.cpp

import androidx.annotation.RestrictTo
import com.itsaky.androidide.backend.build.ToolchainKind
import com.itsaky.androidide.backend.build.ToolchainPaths
import com.itsaky.androidide.backend.proot.ProotConfig
import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.api.IServerSettings
import com.itsaky.androidide.lsp.models.CodeFormatResult
import com.itsaky.androidide.lsp.models.CompletionItem
import com.itsaky.androidide.lsp.models.CompletionItemKind
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import com.itsaky.androidide.lsp.models.DefinitionParams
import com.itsaky.androidide.lsp.models.DefinitionResult
import com.itsaky.androidide.lsp.models.DiagnosticItem
import com.itsaky.androidide.lsp.models.DiagnosticResult
import com.itsaky.androidide.lsp.models.DiagnosticSeverity
import com.itsaky.androidide.lsp.models.ExpandSelectionParams
import com.itsaky.androidide.lsp.models.FormatCodeParams
import com.itsaky.androidide.lsp.models.InsertTextFormat
import com.itsaky.androidide.lsp.models.LSPFailure
import com.itsaky.androidide.lsp.models.MatchLevel
import com.itsaky.androidide.lsp.models.ReferenceParams
import com.itsaky.androidide.lsp.models.ReferenceResult
import com.itsaky.androidide.lsp.models.SignatureHelp
import com.itsaky.androidide.lsp.models.SignatureHelpParams
import com.itsaky.androidide.models.Location
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.projects.IWorkspace
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.LanguageClient
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.launch.LSPLauncher

import org.eclipse.lsp4j.services.LanguageServer
import org.slf4j.LoggerFactory

class CppLanguageServer(
  private val appContext: android.content.Context
) : ILanguageServer {

  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  override var client: ILanguageClient? = null
    private set

  override val serverId: String = SERVER_ID

  private val lock = Any()
  private var process: Process? = null
  private var server: LanguageServer? = null
  private var root: File? = null

  private val diagnostics = ConcurrentHashMap<String, List<DiagnosticItem>>()
  private val versions = ConcurrentHashMap<String, AtomicInteger>()
  private val opened = ConcurrentHashMap.newKeySet<String>()

  companion object {
    const val SERVER_ID = "ide.lsp.cpp"

    private val log = LoggerFactory.getLogger(CppLanguageServer::class.java)

    private val ClangdFlags = listOf(
      "--background-index",
      "--background-index-priority=low",
      "--pch-storage=disk",
      "--malloc-trim",
      "-j=2",
      "--clang-tidy",
      "--all-scopes-completion",
      "--completion-style=detailed",
      "--function-arg-placeholders",
      "--header-insertion=iwyu",
      "--header-insertion-decorators",
      "--limit-results=100",
      "--limit-references=1000",
      "--log=error"
    )

    private const val REQUEST_TIMEOUT_SECONDS = 20L
    private const val DIAGNOSTICS_WAIT_MS = 3000L
  }

  override fun shutdown() {
    synchronized(lock) {
      runCatching { server?.shutdown()?.get(5, TimeUnit.SECONDS) }
      runCatching { server?.exit() }
      runCatching { process?.destroy() }
      server = null
      process = null
      root = null
      opened.clear()
      versions.clear()
    }
  }

  override fun connectClient(client: ILanguageClient?) {
    this.client = client
  }

  override fun applySettings(settings: IServerSettings?) {}

  override fun setupWorkspace(workspace: IWorkspace) {
    val dir = workspace.projectDir
    synchronized(lock) {
      if (root == null || root?.absolutePath != dir.absolutePath) {
        shutdown()
        root = dir
      }
    }
  }

  override fun complete(params: CompletionParams?): CompletionResult {
    if (params == null) {
      return CompletionResult.EMPTY
    }
    val file = params.file.toFile()
    val server = ensureStarted(rootFor(file)) ?: return CompletionResult.EMPTY
    return try {
      val uri = file.toURI().toString()
      syncDocument(file, uri, params.content?.toString())
      val lspParams = org.eclipse.lsp4j.CompletionParams(
        TextDocumentIdentifier(uri),
        org.eclipse.lsp4j.Position(params.position.line, params.position.column)
      )
      val result = server.completion(lspParams).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      val items = when {
        result == null -> emptyList()
        result.isLeft -> result.left ?: emptyList()
        else -> result.right?.items ?: emptyList()
      }
      CompletionResult(items.map { mapCompletion(it) })
    } catch (error: Throwable) {
      log.error("clangd completion failed", error)
      CompletionResult.EMPTY
    }
  }

  override suspend fun findReferences(params: ReferenceParams): ReferenceResult {
    val file = params.file.toFile()
    val server = ensureStarted(rootFor(file)) ?: return ReferenceResult(emptyList())
    return try {
      val uri = file.toURI().toString()
      syncDocument(file, uri, null)
      val lspParams = org.eclipse.lsp4j.ReferenceParams(
        TextDocumentIdentifier(uri),
        org.eclipse.lsp4j.Position(params.position.line, params.position.column),
        ReferenceContext(params.includeDeclaration)
      )
      val locations = server.references(lspParams).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      ReferenceResult((locations ?: emptyList()).mapNotNull { mapLocation(it) })
    } catch (error: Throwable) {
      log.error("clangd references failed", error)
      ReferenceResult(emptyList())
    }
  }

  override suspend fun findDefinition(params: DefinitionParams): DefinitionResult {
    val file = params.file.toFile()
    val server = ensureStarted(rootFor(file)) ?: return DefinitionResult(emptyList())
    return try {
      val uri = file.toURI().toString()
      syncDocument(file, uri, null)
      val lspParams = org.eclipse.lsp4j.DefinitionParams(
        TextDocumentIdentifier(uri),
        org.eclipse.lsp4j.Position(params.position.line, params.position.column)
      )
      val result = server.definition(lspParams).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      val locations = when {
        result == null -> emptyList()
        result.isLeft -> result.left ?: emptyList()
        else -> (result.right ?: emptyList()).mapNotNull { it.targetUri?.let { uri ->
          org.eclipse.lsp4j.Location(uri, it.targetSelectionRange)
        } }
      }
      DefinitionResult(locations.mapNotNull { mapLocation(it) })
    } catch (error: Throwable) {
      log.error("clangd definition failed", error)
      DefinitionResult(emptyList())
    }
  }

  override suspend fun expandSelection(params: ExpandSelectionParams): Range {
    return params.selection
  }

  override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp {
    return SignatureHelp(emptyList(), -1, -1)
  }

  override suspend fun analyze(file: Path): DiagnosticResult {
    val ioFile = file.toFile()
    ensureStarted(rootFor(ioFile)) ?: return DiagnosticResult.NO_UPDATE
    return try {
      val uri = ioFile.toURI().toString()
      syncDocument(ioFile, uri, runCatching { ioFile.readText() }.getOrNull())
      val deadline = System.currentTimeMillis() + DIAGNOSTICS_WAIT_MS
      while (!diagnostics.containsKey(uri) && System.currentTimeMillis() < deadline) {
        kotlinx.coroutines.delay(150)
      }
      DiagnosticResult(file, diagnostics[uri] ?: emptyList())
    } catch (error: Throwable) {
      log.error("clangd analyze failed", error)
      DiagnosticResult.NO_UPDATE
    }
  }

  override fun formatCode(params: FormatCodeParams?): CodeFormatResult {
    return CodeFormatResult(false, mutableListOf())
  }

  override fun handleFailure(failure: LSPFailure?): Boolean {
    return super<ILanguageServer>.handleFailure(failure)
  }

  private fun rootFor(file: File): File {
    synchronized(lock) {
      root?.let { return it }
    }
    var current = file.parentFile
    while (current != null) {
      if (File(current, "CMakeLists.txt").isFile) {
        return current
      }
      current = current.parentFile
    }
    return file.parentFile ?: File("/")
  }

  private fun isBackendReady(): Boolean {
    return ProotConfig.isInstalled(appContext) &&
      ToolchainPaths.isInstalled(appContext, ToolchainKind.Ndk) &&
      ToolchainPaths.clangdBinary(appContext).isFile
  }

  private fun ensureStarted(projectRoot: File): LanguageServer? {
    synchronized(lock) {
      val active = server
      if (active != null && process?.isAlive == true) {
        return active
      }
      shutdown()
      if (!isBackendReady()) {
        return null
      }
      return try {
        val guestRoot = projectRoot.absolutePath
        runCatching { File(ProotConfig.rootfsDir(appContext), guestRoot.trimStart('/')).mkdirs() }
        val command = listOf(ToolchainPaths.guestClangd()) + ClangdFlags
        val args = ProotConfig.rawArgs(
          context = appContext,
          command = command,
          guestCwd = guestRoot,
          binds = listOf("$guestRoot:$guestRoot"),
          extraPath = listOf(ToolchainPaths.guestNdkBin(), ToolchainPaths.guestCMakeBin()),
          extraEnv = listOf("ANDROID_NDK_ROOT=${ToolchainPaths.guestDir(ToolchainKind.Ndk)}")
        )
        val builder = ProcessBuilder(args)
        builder.redirectErrorStream(false)
        builder.environment().putAll(ProotConfig.prootEnvMap(appContext))
        val started = builder.start()
        process = started
        val client = CppClient()
        val launcher = LSPLauncher.createClientLauncher(
          client, started.inputStream, started.outputStream)
        launcher.startListening()
        val remote = launcher.remoteProxy
        val init = InitializeParams()
        init.rootUri = projectRoot.toURI().toString()
        init.processId = null
        remote.initialize(init).get(30, TimeUnit.SECONDS)
        remote.initialized(InitializedParams())
        root = projectRoot
        server = remote
        remote
      } catch (error: Throwable) {
        log.error("Failed to start clangd", error)
        runCatching { process?.destroy() }
        process = null
        server = null
        null
      }
    }
  }

  private fun syncDocument(file: File, uri: String, content: CharSequence?) {
    val server = synchronized(lock) { this.server } ?: return
    val text = content?.toString() ?: runCatching { file.readText() }.getOrNull() ?: return
    try {
      if (opened.add(uri)) {
        versions[uri] = AtomicInteger(1)
        server.textDocumentService.didOpen(
          DidOpenTextDocumentParams(TextDocumentItem(uri, "cpp", 1, text)))
      } else {
        val current = versions[uri]?.incrementAndGet() ?: 1
        versions[uri] = AtomicInteger(current)
        server.textDocumentService.didChange(
          DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier().apply { this.uri = uri; this.version = current },
            listOf(TextDocumentContentChangeEvent(text))))
      }
    } catch (error: Throwable) {
      log.error("clangd sync failed for $uri", error)
    }
  }

  private fun mapCompletion(item: org.eclipse.lsp4j.CompletionItem): CompletionItem {
    val insertText = item.textEdit?.let { edit ->
      when {
        edit.isLeft -> edit.left?.newText
        edit.isRight -> edit.right?.newText
        else -> null
      }
    } ?: item.insertText ?: item.label
    return CompletionItem(
      item.label ?: "",
      item.detail ?: "",
      insertText,
      if (item.insertTextFormat == org.eclipse.lsp4j.InsertTextFormat.Snippet) {
        InsertTextFormat.SNIPPET
      } else {
        InsertTextFormat.PLAIN_TEXT
      },
      item.sortText,
      null,
      mapKind(item.kind),
      MatchLevel.NO_MATCH,
      null,
      null
    )
  }

  private fun mapKind(kind: org.eclipse.lsp4j.CompletionItemKind?): CompletionItemKind {
    return when (kind) {
      org.eclipse.lsp4j.CompletionItemKind.Method -> CompletionItemKind.METHOD
      org.eclipse.lsp4j.CompletionItemKind.Function -> CompletionItemKind.FUNCTION
      org.eclipse.lsp4j.CompletionItemKind.Constructor -> CompletionItemKind.CONSTRUCTOR
      org.eclipse.lsp4j.CompletionItemKind.Field -> CompletionItemKind.FIELD
      org.eclipse.lsp4j.CompletionItemKind.Variable -> CompletionItemKind.VARIABLE
      org.eclipse.lsp4j.CompletionItemKind.Class -> CompletionItemKind.CLASS
      org.eclipse.lsp4j.CompletionItemKind.Interface -> CompletionItemKind.INTERFACE
      org.eclipse.lsp4j.CompletionItemKind.Module -> CompletionItemKind.MODULE
      org.eclipse.lsp4j.CompletionItemKind.Property -> CompletionItemKind.PROPERTY
      org.eclipse.lsp4j.CompletionItemKind.Value -> CompletionItemKind.VALUE
      org.eclipse.lsp4j.CompletionItemKind.Enum -> CompletionItemKind.ENUM
      org.eclipse.lsp4j.CompletionItemKind.EnumMember -> CompletionItemKind.ENUM_MEMBER
      org.eclipse.lsp4j.CompletionItemKind.Keyword -> CompletionItemKind.KEYWORD
      org.eclipse.lsp4j.CompletionItemKind.Snippet -> CompletionItemKind.SNIPPET
      org.eclipse.lsp4j.CompletionItemKind.TypeParameter -> CompletionItemKind.TYPE_PARAMETER
      else -> CompletionItemKind.NONE
    }
  }

  private fun mapLocation(location: org.eclipse.lsp4j.Location): Location? {
    return try {
      val path = Paths.get(URI(location.uri))
      val start = location.range.start
      val end = location.range.end
      Location(
        path,
        Range(Position(start.line, start.character), Position(end.line, end.character))
      )
    } catch (error: Throwable) {
      log.error("Failed to map location ${location.uri}", error)
      null
    }
  }

  private inner class CppClient : LanguageClient {
    override fun telemetryEvent(`object`: Any) {}
    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
      try {
        val items = (diagnostics.diagnostics ?: emptyList()).map { diagnostic ->
          val start = diagnostic.range.start
          val end = diagnostic.range.end
          DiagnosticItem(
            diagnostic.message ?: "",
            diagnostic.code?.let { if (it.isLeft) it.left else it.right?.toString() } ?: "",
            Range(Position(start.line, start.character), Position(end.line, end.character)),
            diagnostic.source ?: "clangd",
            when (diagnostic.severity) {
              org.eclipse.lsp4j.DiagnosticSeverity.Error -> DiagnosticSeverity.ERROR
              org.eclipse.lsp4j.DiagnosticSeverity.Warning -> DiagnosticSeverity.WARNING
              org.eclipse.lsp4j.DiagnosticSeverity.Information -> DiagnosticSeverity.INFO
              else -> DiagnosticSeverity.HINT
            }
          )
        }
        this@CppLanguageServer.diagnostics[diagnostics.uri] = items
      } catch (error: Throwable) {
        log.error("Failed to map diagnostics", error)
      }
    }

    override fun showMessage(messageParams: MessageParams) {
      log.info("clangd: ${messageParams.message}")
    }

    override fun showMessageRequest(
      requestParams: org.eclipse.lsp4j.ShowMessageRequestParams
    ): CompletableFuture<MessageActionItem> {
      return CompletableFuture.completedFuture<MessageActionItem>(null)
    }

    override fun logMessage(message: MessageParams) {}
    override fun logTrace(params: org.eclipse.lsp4j.LogTraceParams) {}
  }
}
