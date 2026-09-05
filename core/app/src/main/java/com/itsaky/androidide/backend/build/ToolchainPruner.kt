package com.itsaky.androidide.backend.build

object ToolchainPruner {

    private const val LlvmPrefix = "toolchains/llvm/prebuilt/linux-arm64/"

    private val DroppedTools = setOf(
        "clang-check",
        "clang-tidy",
        "clang-format",
        "clang-scan-deps",
        "clang-repl",
        "clang-refactor",
        "clang-apply-replacements",
        "clang-doc",
        "clang-include-fixer",
        "clang-move",
        "clang-query",
        "clang-reorder-fields",
        "llvm-bolt",
        "perf2bolt",
        "merge-fdata",
        "dsymutil",
        "llvm-dwp",
        "llvm-dwarfdump",
        "llvm-cfi-verify",
        "llvm-ml",
        "llvm-profdata",
        "llvm-cov",
        "llvm-ifs",
        "llvm-modextract",
        "llvm-dis",
        "llvm-rc",
        "llvm-windres",
        "llvm-strings",
        "llvm-lib",
        "llvm-dlltool",
        "llvm-mca",
        "llvm-xray",
        "llvm-reduce",
        "llvm-stress",
        "llvm-exegesis",
        "llvm-pdbutil",
        "llvm-remarkutil",
        "llvm-undname",
        "llvm-cvtres",
        "llvm-diff",
        "llvm-jitlink",
        "llvm-gsymutil",
        "llvm-tli-checker",
        "llvm-debuginfod-find",
        "sancov",
        "sanstats",
        "diagtool",
        "hmaptool",
        "c-index-test",
        "obj2yaml",
        "yaml2obj",
        "verify-uselistorder",
        "lld-link",
        "ld64.lld",
        "wasm-ld",
        "lldb",
        "lldb-server",
        "lldb-argdumper",
        "lldb-dap",
        "lldb-vscode",
        "modularize",
        "pp-trace",
        "find-all-symbols",
        "git-clang-format",
        "run-clang-tidy",
        "scan-build",
        "scan-view",
        "analyze-build",
        "intercept-build",
        "bisect_driver.py"
    )

    private val ForeignTargetPrefixes = listOf(
        "i686-linux-android",
        "x86_64-linux-android",
        "riscv64-linux-android"
    )

    private val ForeignRuntimeTokens = listOf(
        "riscv64",
        "x86_64",
        "i686",
        "i386"
    )

    private val ForeignRuntimeDirs = setOf(
        "riscv64",
        "x86_64",
        "i686",
        "i386"
    )

    fun keep(kind: ToolchainKind, relative: String): Boolean = when (kind) {
        ToolchainKind.CMake -> true
        ToolchainKind.Ndk -> keepNdk(relative)
    }

    private fun keepNdk(relative: String): Boolean {
        if (relative.startsWith("shader-tools/")) return false
        if (relative.startsWith("simpleperf/")) return false
        if (!relative.startsWith(LlvmPrefix)) return true

        val rest = relative.removePrefix(LlvmPrefix)
        if (rest.startsWith("python3/")) return false
        if (rest.startsWith("bin/")) return keepBin(rest.removePrefix("bin/"))
        if (rest.startsWith("sysroot/usr/lib/")) return keepSysrootLib(rest.removePrefix("sysroot/usr/lib/"))
        if (rest.startsWith("lib/clang/")) return keepClangLib(rest)
        return true
    }

    private fun keepBin(name: String): Boolean {
        if (name.isEmpty()) return true
        if (name in DroppedTools) return false
        if (ForeignTargetPrefixes.any { name.startsWith(it) }) return false
        return true
    }

    private fun keepSysrootLib(rest: String): Boolean {
        val head = rest.substringBefore('/')
        if (head.contains("-linux-android") && !head.startsWith("aarch64") && !head.startsWith("arm")) return false
        return true
    }

    private fun keepClangLib(rest: String): Boolean {
        val marker = "/lib/linux/"
        val index = rest.indexOf(marker)
        if (index < 0) return true
        val name = rest.substring(index + marker.length)
        if (name.isEmpty()) return true
        val head = name.substringBefore('/')
        if (head == "aarch64" || head == "arm") return true
        if (head in ForeignRuntimeDirs) return false
        if (name.contains("aarch64") || name.contains("-arm-android")) return true
        if (ForeignRuntimeTokens.any { name.contains(it) }) return false
        return true
    }
}
