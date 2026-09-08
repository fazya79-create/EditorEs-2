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
import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.builder.MutableMethodImplementation
import org.jf.dexlib2.builder.instruction.BuilderInstruction10x
import org.jf.dexlib2.builder.instruction.BuilderInstruction21c
import org.jf.dexlib2.builder.instruction.BuilderInstruction35c
import org.jf.dexlib2.HiddenApiRestriction
import org.jf.dexlib2.iface.Annotation
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.iface.Method
import org.jf.dexlib2.iface.MethodParameter
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.immutable.ImmutableClassDef
import org.jf.dexlib2.immutable.ImmutableDexFile
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.jf.dexlib2.immutable.reference.ImmutableStringReference
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
      check(verification.isVerified && verification.isVerifiedUsingV1Scheme && verification.isVerifiedUsingV2Scheme && verification.isVerifiedUsingV3Scheme) {
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
    val existingHelper = classDef.directMethods.firstOrNull(::isOurHelper)
    val helper = helperMethod(classDef.type, libraries)
    val direct = classDef.directMethods.filterNot(::isOurHelper).map(ImmutableMethod::of) + helper
    val virtual = classDef.virtualMethods.map { method ->
      if (isOnCreate(method)) patchedOnCreate(method, classDef.type, existingHelper != null) else ImmutableMethod.of(method)
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

  private fun patchedOnCreate(method: Method, classType: String, hadHelper: Boolean): ImmutableMethod {
    val implementation = method.implementation ?: return ImmutableMethod.of(method)
    val patched = MutableMethodImplementation(implementation)
    if (hadHelper && patched.instructions.firstOrNull()?.let(::isHelperInvocation) == true) patched.removeInstruction(0)
    patched.addInstruction(0, BuilderInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0, helperReference(classType)))
    return ImmutableMethod(
      method.definingClass, method.name, method.parameters, method.returnType, method.accessFlags,
      method.annotations, method.hiddenApiRestrictions, patched,
    )
  }

  private fun helperMethod(classType: String, libraries: List<String>): ImmutableMethod {
    val implementation = MutableMethodImplementation(1)
    implementation.addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(marker)))
    libraries.forEach { name ->
      implementation.addInstruction(BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(name)))
      implementation.addInstruction(BuilderInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0, systemLoadLibrary))
    }
    implementation.addInstruction(BuilderInstruction10x(Opcode.RETURN_VOID))
    return ImmutableMethod(
      classType, helperName, emptyList<MethodParameter>(), "V", AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
      emptySet<Annotation>(), emptySet<HiddenApiRestriction>(), implementation,
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

  private fun isHelperInvocation(instruction: org.jf.dexlib2.iface.instruction.Instruction): Boolean =
    instruction.opcode == Opcode.INVOKE_STATIC && instruction is org.jf.dexlib2.iface.instruction.ReferenceInstruction &&
      (instruction.reference as? MethodReference)?.name == helperName

  private fun rebuild(source: File, destination: File, dexes: Map<String, File>, libraries: Collection<NativeLibrary>) {
    ZipFile(source).use { input ->
      ZipOutputStream(FileOutputStream(destination)).use { output ->
        input.entries().asSequence().forEach { entry ->
          if (!entry.name.startsWith("META-INF/") && entry.name !in dexes && entry.name !in libraries.map { "lib/${it.abi}/${it.file.name}" }) {
            output.putNextEntry(ZipEntry(entry.name))
            input.getInputStream(entry).use { it.copyTo(output) }
            output.closeEntry()
          }
        }
        dexes.forEach { (name, file) -> writeFile(output, name, file, false) }
        libraries.forEach { writeFile(output, "lib/${it.abi}/${it.file.name}", it.file, true) }
      }
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
