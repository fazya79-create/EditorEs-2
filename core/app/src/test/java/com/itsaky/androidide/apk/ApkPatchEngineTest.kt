package com.itsaky.androidide.apk

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import com.android.apksig.ApkVerifier
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.instruction.formats.Instruction35c
import org.jf.dexlib2.iface.reference.MethodReference
import org.jf.dexlib2.iface.reference.StringReference
import org.jf.dexlib2.immutable.ImmutableClassDef
import org.jf.dexlib2.immutable.ImmutableDexFile
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation
import org.jf.dexlib2.immutable.ImmutableMethodParameter
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ApkPatchEngineTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private lateinit var context: Context
  private lateinit var assets: AssetManager

  @Before
  fun setUp() {
    val files = temporaryFolder.newFolder("files")
    val cache = temporaryFolder.newFolder("cache")
    cache.resolve("keep.txt").writeText("unrelated cache entry")
    assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
    context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
      override fun getFilesDir(): File = files
      override fun getCacheDir(): File = cache
      override fun getAssets(): AssetManager = this@ApkPatchEngineTest.assets
      override fun getApplicationContext(): Context = this
    }
  }

  @After
  fun tearDown() {
    if (::assets.isInitialized) assets.close()
  }

  @Test
  fun `patches a single ARM library and signs all three schemes`() {
    val library = library("armeabi-v7a")
    val input = apk(extraEntries = mapOf(
      "lib/armeabi-v7a/libfixture.so" to byteArrayOf(1, 2, 3),
      "META-INF/OLD.SF" to "obsolete signature".toByteArray(),
      "META-INF/OLD.RSA" to "obsolete certificate".toByteArray(),
    ))
    val original = input.readBytes()
    val output = temporaryFolder.root.resolve("output/arm.apk")

    val result = ApkPatchEngine(context).patch(input, output, listOf(library))

    assertThat(result).isEqualTo(ApkPatchEngine.PatchResult(output, launcherActivity))
    verifySignatures(output)
    assertPatchedDex(output)
    assertLibraries(output, listOf(library))
    assertPreservedEntries(input, output)
    assertThat(entries(output).keys).containsNoneOf("META-INF/OLD.SF", "META-INF/OLD.RSA")
    assertThat(input.readBytes()).isEqualTo(original)
    assertThat(context.filesDir.resolve("apk-patch-signing.p12").isFile).isTrue()
    assertCacheClean()
    retainArtifact(output, "arm-api21.apk")
    retainArtifact(input, "input-api21.apk")
  }

  @Test
  fun `patches a single ARM64 library when the APK minimum SDK is 26`() {
    val library = library("arm64-v8a")
    val input = apk(minSdk = 26)
    val original = input.readBytes()
    val output = temporaryFolder.root.resolve("output/arm64.apk")

    val result = ApkPatchEngine(context).patch(input, output, listOf(library))

    assertThat(result.launcherActivity).isEqualTo(launcherActivity)
    verifySignatures(output)
    assertPatchedDex(output)
    assertLibraries(output, listOf(library))
    assertPreservedEntries(input, output)
    assertThat(input.readBytes()).isEqualTo(original)
    assertCacheClean()
    retainArtifact(output, "arm64-api26.apk")
  }

  @Test
  fun `patches a secondary dex and deduplicates the load call for both ABIs`() {
    val primaryDex = dex(otherActivityType)
    val input = apk(dexes = mapOf(
      "classes.dex" to primaryDex,
      "classes2.dex" to dex(launcherType),
    ))
    val original = input.readBytes()
    val libraries = listOf(library("armeabi-v7a"), library("arm64-v8a"))
    val output = temporaryFolder.root.resolve("output/multidex.apk")

    ApkPatchEngine(context).patch(input, output, libraries)

    verifySignatures(output)
    assertPatchedDex(output, "classes2.dex")
    assertThat(entries(output)["classes.dex"]).isEqualTo(primaryDex)
    assertLibraries(output, libraries)
    assertPreservedEntries(input, output)
    assertThat(input.readBytes()).isEqualTo(original)
    assertCacheClean()
    retainArtifact(output, "both-abis-multidex.apk")
  }

  @Test
  fun `patching again replaces signatures and reuses the persisted signing key`() {
    val input = apk()
    val original = input.readBytes()
    val arm = library("armeabi-v7a")
    val arm64 = library("arm64-v8a")
    val first = temporaryFolder.root.resolve("output/first.apk")
    ApkPatchEngine(context).patch(input, first, listOf(arm))
    val firstVerification = verifySignatures(first)
    val firstBytes = first.readBytes()
    val firstEntries = entries(first)
    val key = context.filesDir.resolve("apk-patch-signing.p12")
    val keyBytes = key.readBytes()
    assertCacheClean()
    val second = temporaryFolder.root.resolve("output/second.apk")

    ApkPatchEngine(context).patch(first, second, listOf(arm, arm64))

    val secondVerification = verifySignatures(second)
    assertThat(secondVerification.signerCertificates.single().encoded)
      .isEqualTo(firstVerification.signerCertificates.single().encoded)
    assertThat(key.readBytes()).isEqualTo(keyBytes)
    val secondEntries = entries(second)
    val signatures = secondEntries.keys.filter { it.endsWith(".SF") || it.endsWith(".RSA") }
    assertThat(signatures).hasSize(2)
    assertThat(signatures).containsExactlyElementsIn(
      firstEntries.keys.filter { it.endsWith(".SF") || it.endsWith(".RSA") }
    )
    signatures.forEach { name ->
      assertThat(secondEntries[name]).isNotEqualTo(firstEntries[name])
    }
    assertThat(secondEntries["META-INF/MANIFEST.MF"])
      .isNotEqualTo(firstEntries["META-INF/MANIFEST.MF"])
    assertPatchedDex(second)
    assertLibraries(second, listOf(arm, arm64))
    assertPreservedEntries(first, second)
    assertThat(first.readBytes()).isEqualTo(firstBytes)
    assertThat(input.readBytes()).isEqualTo(original)
    assertCacheClean()
  }

  @Test
  fun `a missing launcher class leaves no output or temporary patch files`() {
    val input = apk(dexes = mapOf("classes.dex" to dex(otherActivityType)))
    val original = input.readBytes()
    val output = temporaryFolder.root.resolve("output/failed.apk")
    val library = library("armeabi-v7a")

    val failure = assertThrows(IllegalArgumentException::class.java) {
      ApkPatchEngine(context).patch(input, output, listOf(library))
    }

    assertThat(failure).hasMessageThat()
      .isEqualTo("Launcher activity $launcherActivity was not found in any dex file")
    assertThat(output.exists()).isFalse()
    assertThat(output.parentFile!!.exists()).isFalse()
    assertThat(input.readBytes()).isEqualTo(original)
    assertThat(context.filesDir.listFiles()!!.toList()).isEmpty()
    assertCacheClean()
  }

  private fun verifySignatures(apk: File): ApkVerifier.Result {
    val result = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(21).build().verify()
    assertThat(result.allErrors).isEmpty()
    assertThat(result.isVerified).isTrue()
    assertThat(result.isVerifiedUsingV1Scheme).isTrue()
    assertThat(result.isVerifiedUsingV2Scheme).isTrue()
    assertThat(result.isVerifiedUsingV3Scheme).isTrue()
    assertThat(result.signerCertificates).hasSize(1)
    assertThat(result.v1SchemeSigners).hasSize(1)
    assertThat(result.v1SchemeIgnoredSigners).isEmpty()
    assertThat(result.v2SchemeSigners).hasSize(1)
    assertThat(result.v3SchemeSigners).hasSize(1)
    return result
  }

  private fun assertPatchedDex(apk: File, entry: String = "classes.dex") {
    val dex = DexBackedDexFile(Opcodes.forApi(28), checkNotNull(entries(apk)[entry]))
    val launcher = dex.classes.single { it.type == launcherType }
    val onCreate = launcher.virtualMethods.single { it.name == "onCreate" }
    val implementation = checkNotNull(onCreate.implementation)
    val instructions = implementation.instructions.toList()
    assertThat(implementation.registerCount).isEqualTo(2)
    assertThat(instructions.map { it.opcode }).containsExactly(
      Opcode.INVOKE_STATIC, Opcode.INVOKE_SUPER, Opcode.RETURN_VOID
    ).inOrder()
    val invocation = instructions[0] as Instruction35c
    assertThat(invocation.registerCount).isEqualTo(0)
    assertThat(invocation.reference)
      .isEqualTo(ImmutableMethodReference(launcherType, helperName, emptyList<String>(), "V"))
    val superCall = instructions[1] as Instruction35c
    assertThat(superCall.reference).isEqualTo(superOnCreate)
    assertThat(superCall.registerCount).isEqualTo(2)
    assertThat(superCall.registerC).isEqualTo(0)
    assertThat(superCall.registerD).isEqualTo(1)

    assertThat(launcher.directMethods.map { it.name }).containsExactly("<init>", helperName)
    val constructor = launcher.directMethods.single { it.name == "<init>" }
    val constructorInstructions = checkNotNull(constructor.implementation).instructions.toList()
    assertThat(constructorInstructions.map { it.opcode })
      .containsExactly(Opcode.INVOKE_DIRECT, Opcode.RETURN_VOID).inOrder()
    assertThat((constructorInstructions[0] as ReferenceInstruction).reference)
      .isEqualTo(superConstructor)
    val helper = launcher.directMethods.single { it.name == helperName }
    assertThat(helper.accessFlags).isEqualTo(AccessFlags.PRIVATE.value or AccessFlags.STATIC.value)
    assertThat(helper.parameterTypes).isEmpty()
    assertThat(helper.returnType).isEqualTo("V")
    val helperInstructions = checkNotNull(helper.implementation).instructions.toList()
    assertThat(helperInstructions.map { it.opcode }).containsExactly(
      Opcode.CONST_STRING, Opcode.CONST_STRING, Opcode.INVOKE_STATIC, Opcode.RETURN_VOID
    ).inOrder()
    val references = helperInstructions.filterIsInstance<ReferenceInstruction>().map { it.reference }
    assertThat(references.filterIsInstance<StringReference>().map { it.string })
      .containsExactly("androidide:apk-patch:load-native-libraries", "fixture").inOrder()
    assertThat(references.filterIsInstance<MethodReference>()).containsExactly(
      ImmutableMethodReference("Ljava/lang/System;", "loadLibrary", listOf("Ljava/lang/String;"), "V")
    )
    val loadCall = helperInstructions[2] as Instruction35c
    assertThat(loadCall.registerCount).isEqualTo(1)
    assertThat(loadCall.registerC).isEqualTo(0)
  }

  private fun assertLibraries(apk: File, libraries: List<ApkPatchEngine.NativeLibrary>) {
    ZipFile(apk).use { zip ->
      val expected = libraries.map { "lib/${it.abi}/${it.file.name}" }
      val actual = zip.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") }.toList()
      assertThat(actual).containsExactlyElementsIn(expected)
      libraries.forEach { library ->
        val entry = checkNotNull(zip.getEntry("lib/${library.abi}/${library.file.name}"))
        assertThat(entry.method).isEqualTo(ZipEntry.STORED)
        assertThat(zip.getInputStream(entry).use { it.readBytes() }).isEqualTo(library.file.readBytes())
      }
    }
  }

  private fun assertPreservedEntries(input: File, output: File) {
    val before = entries(input)
    val after = entries(output)
    before.filterKeys { it !in setOf("classes.dex", "classes2.dex") &&
      !it.startsWith("META-INF/") && !it.startsWith("lib/")
    }.forEach { (name, bytes) -> assertThat(after[name]).isEqualTo(bytes) }
  }

  private fun assertCacheClean() {
    assertThat(context.cacheDir.list()!!.toList()).containsExactly("keep.txt")
    assertThat(context.cacheDir.resolve("keep.txt").readText()).isEqualTo("unrelated cache entry")
  }

  private fun library(abi: String): ApkPatchEngine.NativeLibrary {
    val bytes = fixture("$abi/libfixture.so.bin")
    assertThat(bytes.size).isGreaterThan(64)
    assertThat(bytes.take(4)).containsExactly(0x7f.toByte(), 0x45.toByte(), 0x4c.toByte(), 0x46.toByte()).inOrder()
    assertThat(bytes[4].toInt()).isEqualTo(if (abi == "armeabi-v7a") 1 else 2)
    val file = temporaryFolder.root.resolve("libraries/$abi/libfixture.so")
    file.parentFile!!.mkdirs()
    file.writeBytes(bytes)
    return ApkPatchEngine.NativeLibrary(abi, file)
  }

  private fun apk(
    minSdk: Int = 21,
    dexes: Map<String, ByteArray> = mapOf("classes.dex" to dex(launcherType)),
    extraEntries: Map<String, ByteArray> = emptyMap(),
  ): File {
    val file = temporaryFolder.newFolder().resolve("input.apk")
    ZipOutputStream(file.outputStream()).use { output ->
      ZipInputStream(fixture("manifest-$minSdk.apk").inputStream()).use { input ->
        while (true) {
          val entry = input.nextEntry ?: break
          output.putNextEntry(ZipEntry(entry.name))
          input.copyTo(output)
          output.closeEntry()
        }
      }
      (dexes + mapOf("assets/keep.txt" to "untouched fixture asset".toByteArray()) + extraEntries)
        .forEach { (name, bytes) ->
          output.putNextEntry(ZipEntry(name))
          output.write(bytes)
          output.closeEntry()
        }
    }
    return file
  }

  private fun dex(type: String): ByteArray {
    val constructor = ImmutableMethod(
      type, "<init>", emptyList(), "V", AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value,
      emptySet(), emptySet(), ImmutableMethodImplementation(1, listOf(
        ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 1, 0, 0, 0, 0, 0, superConstructor),
        ImmutableInstruction10x(Opcode.RETURN_VOID),
      ), emptyList(), emptyList()),
    )
    val onCreate = ImmutableMethod(
      type, "onCreate", listOf(ImmutableMethodParameter("Landroid/os/Bundle;", emptySet(), "savedInstanceState")),
      "V", AccessFlags.PROTECTED.value, emptySet(), emptySet(), ImmutableMethodImplementation(2, listOf(
        ImmutableInstruction35c(Opcode.INVOKE_SUPER, 2, 0, 1, 0, 0, 0, superOnCreate),
        ImmutableInstruction10x(Opcode.RETURN_VOID),
      ), emptyList(), emptyList()),
    )
    val activity = ImmutableClassDef(
      type, AccessFlags.PUBLIC.value, activityType, emptyList(), "MainActivity.java", emptySet(),
      emptyList(), emptyList(), listOf(constructor), listOf(onCreate),
    )
    val file = temporaryFolder.newFile()
    DexFileFactory.writeDexFile(file.absolutePath, ImmutableDexFile(Opcodes.forApi(21), listOf(activity)))
    return file.readBytes()
  }

  private fun fixture(path: String): ByteArray = checkNotNull(
    javaClass.getResourceAsStream("/native-injection/$path")
  ) { "Missing generated native injection fixture: $path" }.use { it.readBytes() }

  private fun entries(apk: File): Map<String, ByteArray> = ZipFile(apk).use { zip ->
    zip.entries().asSequence().associate { entry ->
      entry.name to zip.getInputStream(entry).use { it.readBytes() }
    }
  }

  private fun retainArtifact(apk: File, name: String) {
    val output = File("build/test-artifacts/native-injection", name)
    output.parentFile!!.mkdirs()
    apk.copyTo(output, overwrite = true)
  }

  companion object {
    private const val launcherActivity = "com.itsaky.androidide.injectionfixture.MainActivity"
    private const val launcherType = "Lcom/itsaky/androidide/injectionfixture/MainActivity;"
    private const val otherActivityType = "Lcom/itsaky/androidide/injectionfixture/OtherActivity;"
    private const val activityType = "Landroid/app/Activity;"
    private const val helperName = "androidide\$loadNativeLibraries"
    private val superConstructor = ImmutableMethodReference(activityType, "<init>", emptyList<String>(), "V")
    private val superOnCreate = ImmutableMethodReference(activityType, "onCreate", listOf("Landroid/os/Bundle;"), "V")
  }
}
