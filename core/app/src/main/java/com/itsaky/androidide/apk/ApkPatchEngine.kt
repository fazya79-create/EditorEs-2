package com.itsaky.androidide.apk

import android.content.Context
import android.content.res.AssetManager
import android.content.res.XmlResourceParser
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.builder.MutableMethodImplementation
import org.jf.dexlib2.builder.instruction.BuilderInstruction12x
import org.jf.dexlib2.builder.instruction.BuilderInstruction21c
import org.jf.dexlib2.builder.instruction.BuilderInstruction35c
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.immutable.ImmutableClassDef
import org.jf.dexlib2.immutable.ImmutableDexFile
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.jf.dexlib2.immutable.reference.ImmutableStringReference
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkPatchEngine(private val context: Context) {

  data class NativeLibrary(val abi: String, val file: File)

  data class PatchResult(val output: File, val launcherActivity: String)

  fun patch(
    input: File,
    output: File,
    libraries: Collection<NativeLibrary>,
    onProgress: (String) -> Unit = {},
  ): PatchResult {
    require(input.isFile) { "APK does not exist: $input" }
    require(libraries.isNotEmpty()) { "Select at least one native library" }
    libraries.forEach {
      require(it.abi in supportedAbis) { "Unsupported ABI: ${it.abi}" }
      require(it.file.isFile && it.file.extension == "so") { "Invalid native library: ${it.file}" }
    }
    val work = Files.createTempDirectory(context.cacheDir.toPath(), "apk-patch-").toFile()
    try {
      val source = File(work, "input.apk")
      input.copyTo(source)
      onProgress("Resolving launcher activity…")
      val launcher = launcherActivity(source)
      val classType = "L${launcher.replace('.', '/')};"
      onProgress("Rewriting launcher dex…")
      val replacements = patchLauncherDex(source, work, classType, libraries.map { libraryName(it.file) }.distinct())
      require(replacements.isNotEmpty()) { "Launcher activity $launcher was not found in any dex file" }
      val unsigned = File(work, "unsigned.apk")
      onProgress("Adding native libraries…")
      rebuild(source, unsigned, replacements, libraries)
      val signed = File(work, "signed.apk")
      onProgress("Signing APK…")
      sign(unsigned, signed)
      onProgress("Verifying v1, v2, and v3 signatures…")
      val verification = ApkVerifier.Builder(signed).build().verify()
      val v1Verification = ApkVerifier.Builder(signed)
        .setMinCheckedPlatformVersion(23).setMaxCheckedPlatformVersion(23).build().verify()
      check(verification.isVerified && v1Verification.isVerified && v1Verification.isVerifiedUsingV1Scheme && verification.isVerifiedUsingV2Scheme && verification.isVerifiedUsingV3Scheme) {
        "APK signature verification failed for one or more signing schemes"
      }
      output.parentFile?.mkdirs()
      signed.copyTo(output, overwrite = true)
      onProgress("Cleaning temporary files…")
      return PatchResult(output, launcher)
    } finally {
      work.deleteRecursively()
    }
  }

  private fun patchLauncherDex(apk: File, work: File, classType: String, libraries: List<String>): Map<String, File> {
    val result = linkedMapOf<String, File>()
    ZipFile(apk).use { zip ->
      zip.entries().asSequence().filter { it.name.matches(Regex("classes(\\d+)?\\.dex")) }.forEach { entry ->
        val dex = File.createTempFile("dex-", ".dex", work)
        zip.getInputStream(entry).use { input -> dex.outputStream().use(input::copyTo) }
        val dexFile = DexFileFactory.loadDexFile(dex, Opcodes.getDefault())
        val target = dexFile.classes.firstOrNull { it.type == classType }
        if (target != null) {
          require(target.virtualMethods.any(::isOnCreate)) { "Launcher activity $classType has no onCreate(Bundle) method" }
          val patched = File.createTempFile("patched-", ".dex", work)
          DexFileFactory.writeDexFile(patched.absolutePath, ImmutableDexFile(dexFile.opcodes, dexFile.classes.map {
            if (it.type == classType) patchedClass(it, libraries) else ImmutableClassDef.of(it)
          }))
          result[entry.name] = patched
        }
        dex.delete()
      }
    }
    return result
  }

  private fun patchedClass(classDef: ClassDef, libraries: List<String>): ImmutableClassDef {
    val direct = classDef.directMethods.filterNot(::isOurHelper).map(ImmutableMethod::of)
    val virtual = classDef.virtualMethods.map { method ->
      if (isOnCreate(method)) patchedOnCreate(method, classDef.type, libraries) else ImmutableMethod.of(method)
    }
    return ImmutableClassDef(
      classDef.type,
      classDef.accessFlags,
      classDef.superclass,
      classDef.interfaces,
      classDef.sourceFile,
      classDef.annotations,
      classDef.staticFields,
      classDef.instanceFields,
      direct,
      virtual,
    )
  }

  private fun patchedOnCreate(method: Method, classType: String, libraries: List<String>): ImmutableMethod {
    val implementation = method.implementation ?: return ImmutableMethod.of(method)
    val existing = MutableMethodImplementation(implementation)
    if (existing.instructions.firstOrNull()?.let { isHelperInvocation(it, classType) } == true) {
      existing.removeInstruction(0)
    }
    val inlinePrefix = inlinePrefixSize(existing, method.parameterTypes.size + 1)
    if (inlinePrefix != null) repeat(inlinePrefix) { existing.removeInstruction(0) }
    val registerCount = existing.registerCount + if (inlinePrefix == null) 1 else 0
    val patched = MutableMethodImplementation(ImmutableMethodImplementation(
      registerCount, existing.instructions, existing.tryBlocks, existing.debugItems,
    ))
    val parameterStart = registerCount - method.parameterTypes.size - 2
    (0..method.parameterTypes.size).forEach { index ->
      patched.addInstruction(index, BuilderInstruction12x(Opcode.MOVE_OBJECT, parameterStart + index, parameterStart + index + 1))
    }
    val scratch = registerCount - 1
    val injection = buildList {
      add(BuilderInstruction21c(Opcode.CONST_STRING, scratch, ImmutableStringReference(marker)))
      libraries.forEach { name ->
        add(BuilderInstruction21c(Opcode.CONST_STRING, scratch, ImmutableStringReference(name)))
        add(BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, scratch, 0, 0, 0, 0, systemLoadLibrary))
      }
    }
    injection.forEachIndexed { index, instruction ->
      patched.addInstruction(method.parameterTypes.size + 1 + index, instruction)
    }
    return ImmutableMethod(
      method.definingClass, method.name, method.parameters, method.returnType, method.accessFlags,
      method.annotations, method.hiddenApiRestrictions, patched,
    )
  }

  private fun isOurHelper(method: Method): Boolean =
    method.name == helperName && method.parameterTypes.isEmpty() && method.returnType == "V" &&
      method.implementation?.instructions?.firstOrNull()?.let { instruction ->
        instruction.opcode == Opcode.CONST_STRING && instruction is org.jf.dexlib2.iface.instruction.ReferenceInstruction &&
          instruction.reference == ImmutableStringReference(marker)
      } == true

  private fun isOnCreate(method: Method): Boolean =
    method.name == "onCreate" && method.returnType == "V" && method.parameterTypes == listOf("Landroid/os/Bundle;")

  private fun isHelperInvocation(instruction: org.jf.dexlib2.iface.instruction.Instruction, classType: String): Boolean =
    instruction.opcode == Opcode.INVOKE_STATIC && instruction is org.jf.dexlib2.iface.instruction.ReferenceInstruction &&
      instruction.reference == helperReference(classType)

  private fun inlinePrefixSize(implementation: MutableMethodImplementation, parameterCount: Int): Int? {
    val instructions = implementation.instructions
    val parameterStart = implementation.registerCount - parameterCount - 1
    if (instructions.size < parameterCount + 2) return null
    if ((0 until parameterCount).any { index ->
        val instruction = instructions[index]
        instruction.opcode != Opcode.MOVE_OBJECT || instruction !is org.jf.dexlib2.iface.instruction.formats.Instruction12x ||
          instruction.registerA != parameterStart + index || instruction.registerB != parameterStart + index + 1
      }) return null
    val scratch = implementation.registerCount - 1
    val markerInstruction = instructions[parameterCount]
    if (markerInstruction.opcode != Opcode.CONST_STRING || markerInstruction !is org.jf.dexlib2.iface.instruction.formats.Instruction21c ||
      markerInstruction.registerA != scratch || markerInstruction.reference != ImmutableStringReference(marker)) return null
    var index = parameterCount + 1
    var loads = 0
    while (index + 1 < instructions.size && isInlineLoadPair(instructions[index], instructions[index + 1], scratch)) {
      loads++
      index += 2
    }
    return if (loads > 0) index else null
  }

  private fun isInlineLoadPair(
    string: org.jf.dexlib2.iface.instruction.Instruction,
    invocation: org.jf.dexlib2.iface.instruction.Instruction,
    scratch: Int,
  ): Boolean =
    string.opcode == Opcode.CONST_STRING && string is org.jf.dexlib2.iface.instruction.formats.Instruction21c &&
      string.registerA == scratch && invocation.opcode == Opcode.INVOKE_STATIC &&
      invocation is org.jf.dexlib2.iface.instruction.formats.Instruction35c && invocation.registerCount == 1 &&
      invocation.registerC == scratch && invocation.reference == systemLoadLibrary

  private fun rebuild(source: File, destination: File, dexes: Map<String, File>, libraries: Collection<NativeLibrary>) {
    ZipFile(source).use { input ->
      val bytesWritten = CountingOutputStream(FileOutputStream(destination))
      ZipOutputStream(bytesWritten).use { output ->
        input.entries().asSequence().forEach { entry ->
          if (!entry.name.startsWith("META-INF/") && entry.name !in dexes && entry.name !in libraries.map { "lib/${it.abi}/${it.file.name}" }) {
            if (entry.name == "resources.arsc") {
              writeResourceTable(output, bytesWritten.count, entry, input)
            } else {
              output.putNextEntry(ZipEntry(entry.name))
              input.getInputStream(entry).use { it.copyTo(output) }
              output.closeEntry()
            }
          }
        }
        dexes.forEach { (name, file) -> writeFile(output, name, file, false) }
        libraries.forEach { writeFile(output, "lib/${it.abi}/${it.file.name}", it.file, true) }
      }
    }
  }

  private fun writeResourceTable(output: ZipOutputStream, offset: Long, source: ZipEntry, input: ZipFile) {
    val entry = ZipEntry(source.name)
    entry.method = ZipEntry.STORED
    entry.size = source.size
    entry.compressedSize = source.size
    entry.crc = source.crc
    entry.extra = alignmentExtra(offset, entry.name)
    output.putNextEntry(entry)
    input.getInputStream(source).use { it.copyTo(output) }
    output.closeEntry()
  }

  private fun alignmentExtra(offset: Long, name: String): ByteArray {
    val padding = ((4 - (offset + 30 + name.toByteArray(Charsets.UTF_8).size) % 4) % 4).toInt()
    return ByteArray(4 + padding).also {
      it[0] = 0x35
      it[1] = 0xd9.toByte()
      it[2] = padding.toByte()
    }
  }

  private fun writeFile(output: ZipOutputStream, name: String, file: File, stored: Boolean) {
    val entry = ZipEntry(name)
    if (stored) {
      val crc = CRC32()
      FileInputStream(file).use { input -> input.copyTo(object : java.io.OutputStream() {
        override fun write(value: Int) = crc.update(value)
      }) }
      entry.method = ZipEntry.STORED
      entry.size = file.length()
      entry.compressedSize = file.length()
      entry.crc = crc.value
    }
    output.putNextEntry(entry)
    FileInputStream(file).use { it.copyTo(output) }
    output.closeEntry()
  }

  private fun sign(input: File, output: File) {
    val keyStore = keystore()
    val key = keyStore.getKey(keyAlias, keyPassword) as PrivateKey
    val certificate = keyStore.getCertificate(keyAlias) as X509Certificate
    val signer = ApkSigner.SignerConfig.Builder(keyAlias, key, listOf(certificate)).build()
    ApkSigner.Builder(listOf(signer)).setInputApk(input).setOutputApk(output)
      .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).build().sign()
  }

  private fun keystore(): KeyStore {
    val file = File(context.filesDir, "apk-patch-signing.p12")
    val keyStore = KeyStore.getInstance("PKCS12")
    if (file.exists()) {
      file.inputStream().use { keyStore.load(it, keyPassword) }
      return keyStore
    }
    keyStore.load(null, keyPassword)
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val now = Date()
    val certificate = JcaX509v3CertificateBuilder(X500Name("CN=AndroidIDE APK Patcher"), BigInteger(160, SecureRandom()), now, Date(now.time + 315360000000L), X500Name("CN=AndroidIDE APK Patcher"), pair.public)
      .build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
    keyStore.setKeyEntry(keyAlias, pair.private, keyPassword, arrayOf(JcaX509CertificateConverter().getCertificate(certificate)))
    file.outputStream().use { keyStore.store(it, keyPassword) }
    return keyStore
  }

  private fun launcherActivity(apk: File): String {
    val assets = context.assets
    val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
    check((addAssetPath.invoke(assets, apk.absolutePath) as Int) != 0) { "Unable to read APK resources" }
    val parser = assets.openXmlResourceParser("AndroidManifest.xml")
    parser.use {
      var packageName = ""
      var activity: String? = null
      var target: String? = null
      var activityLauncher = false
      var filterMain = false
      var filterLauncher = false
      while (parser.next() != XmlResourceParser.END_DOCUMENT) {
        when (parser.eventType) {
          XmlResourceParser.START_TAG -> when (parser.name) {
            "manifest" -> packageName = parser.getAttributeValue(null, "package") ?: ""
            "activity", "activity-alias" -> { activity = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name"); target = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "targetActivity"); activityLauncher = false }
            "intent-filter" -> { filterMain = false; filterLauncher = false }
            "action" -> filterMain = filterMain || parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name") == "android.intent.action.MAIN"
            "category" -> filterLauncher = filterLauncher || parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name") == "android.intent.category.LAUNCHER"
          }
          XmlResourceParser.END_TAG -> when (parser.name) {
            "intent-filter" -> activityLauncher = activityLauncher || (filterMain && filterLauncher)
            "activity", "activity-alias" -> if (activityLauncher) {
              return qualify(target ?: activity.orEmpty(), packageName)
            }
          }
        }
      }
    }
    error("Launcher activity not found")
  }

  private fun qualify(name: String, packageName: String): String = when {
    name.startsWith(".") -> packageName + name
    '.' !in name -> "$packageName.$name"
    else -> name
  }

  private fun libraryName(file: File): String = file.name.removePrefix("lib").removeSuffix(".so")

  private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
    var count = 0L
      private set

    override fun write(value: Int) {
      out.write(value)
      count++
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
      out.write(buffer, offset, length)
      count += length
    }
  }

  companion object {
    private const val helperName = "androidide\$loadNativeLibraries"
    private const val marker = "androidide:apk-patch:load-native-libraries"
    private val supportedAbis = setOf("armeabi-v7a", "arm64-v8a")
    private val keyPassword = "androidide-apk-patch".toCharArray()
    private const val keyAlias = "androidide-apk-patch"
    private val systemLoadLibrary = ImmutableMethodReference("Ljava/lang/System;", "loadLibrary", listOf("Ljava/lang/String;"), "V")
    private fun helperReference(classType: String) = ImmutableMethodReference(classType, helperName, emptyList<String>(), "V")
  }
}
