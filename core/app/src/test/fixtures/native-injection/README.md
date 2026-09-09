# Native injection regression fixtures

`generateInjectionFixtures` builds binary Android manifests (minimum SDK 21 and 26) with the configured Android build tools and compiles `library.c` into real ARM and ARM64 ELF shared libraries with the configured NDK. The generated libraries use a `.bin` resource suffix because AGP excludes `.so` files from Java test resources. Tests copy them back to `libfixture.so` before injection.

`ApkPatchEngineTest` adds executable Activity dex code to these manifests and calls the complete production patch pipeline. It checks all three signature schemes, exact library bytes and ABI paths, launcher bytecode, multidex, repeated patches, key reuse, unchanged inputs, and cleanup. Signed results and an unsigned input are retained in `core/app/build/test-artifacts/native-injection/` after successful tests.

`NativeLibraryInjectionDialogFragmentTest` uses the actual dialog and Material widgets under Robolectric. It checks the folder icon's document-picker intent and result, cancellation, URI copying, install intent, one/two-library selection, deduplication, empty state, quiet discovery, and controls during patching. Its patch-engine mock is limited to dialog state/dispatch tests; actual APK rewriting/signing is exercised separately by `ApkPatchEngineTest`.

## Local checks

```sh
ANDROID_HOME=/opt/android-sdk ./gradlew --no-daemon --max-workers=2 \
  :core:app:assembleDebug :core:app:testDebugUnitTest \
  --tests '*ApkPatchEngineTest' \
  --tests '*NativeLibraryInjectionDialogFragmentTest' -x lint

/opt/android-sdk/build-tools/34.0.0/apksigner verify --verbose \
  --min-sdk-version 21 core/app/build/test-artifacts/native-injection/arm-api21.apk
/opt/android-sdk/build-tools/34.0.0/apksigner verify --verbose \
  --min-sdk-version 21 core/app/build/test-artifacts/native-injection/arm64-api26.apk
/opt/android-sdk/build-tools/34.0.0/apksigner verify --verbose \
  --min-sdk-version 21 core/app/build/test-artifacts/native-injection/both-abis-multidex.apk

git diff --check
```

Use your local Android SDK location if it differs. Explicitly checking an older platform makes the external verifier inspect v1 even when the APK's declared minimum SDK normally makes v1 unnecessary. Production verification still validates the APK's declared platform range and separately verifies v1 at API 23.

## Signing failure

The existing PKCS12 key, alias, and signer configuration work without shrinking. The reported `Failed to sign using signer "ANDROIDI"` was reproduced with apksig 8.5.0, the R8 embedded in AGP 8.5.0, and this app's release/default shrinker rules. R8 removed reflectively accessed ASN.1 model fields and annotation members; the nested error was `Failed to encode signature block`. Keeping both the ASN.1 annotations and their annotated model classes made the same-key signing probe pass v1/v2/v3. The signer name is apksig's normal eight-character normalization, not an invalid keystore alias. No key replacement or rotation is required by this fix.

## Verification boundary

These JVM tests do not establish physical-device picker rendering, appearance, installation, or native loading. Those need a working Android device/emulator. During this change, a software-only API 30 emulator ran the original signing configuration successfully, but its Android system/package services subsequently crashed during app installation, including after a higher-memory retry. No successful full-app emulator interaction or visual verification is claimed.

## Verification performed on 2026-09-09

The final debug APK build, minified release APK build, and all 13 focused tests passed. The three retained patched APKs also passed the independent `apksigner` commands above with v1, v2, and v3 all reported as `true`. Release APKs used only the sandbox's generated debug key for local verification, not a production key.

There were six Gradle invocations: two successful baseline builds followed by four post-edit verification iterations. The first three post-edit iterations exposed fixture task wiring/resource-packaging issues, a constructor-time nested-scrolling crash in the new layout, and a test assumption about OpenDocument categories. The scrolling property is now set after inflation. The category assertion was corrected against the installed AndroidX 1.8.0 implementation, which does not add `CATEGORY_OPENABLE`; action, MIME types, result delivery, and cancellation remain tested.

Baseline commands:

```sh
ANDROID_HOME=/opt/android-sdk ./gradlew --no-daemon :core:app:assembleDebug -x lint

RELEASE_KEYSTORE=/root/.android/debug.keystore RELEASE_STORE_PASSWORD=android \
RELEASE_KEY_ALIAS=androiddebugkey RELEASE_KEY_PASSWORD=android \
ANDROID_HOME=/opt/android-sdk ./gradlew --no-daemon --max-workers=2 \
  :core:app:assembleRelease -x lint
```

Post-edit iterations 1 and 2 used the debug/test command in Local checks. Iterations 3 and 4 used the following command; iteration 4 succeeded:

```sh
RELEASE_KEYSTORE=/root/.android/debug.keystore RELEASE_STORE_PASSWORD=android \
RELEASE_KEY_ALIAS=androiddebugkey RELEASE_KEY_PASSWORD=android \
ANDROID_HOME=/opt/android-sdk ./gradlew --no-daemon --max-workers=2 \
  :core:app:assembleDebug :core:app:testDebugUnitTest \
  --tests '*ApkPatchEngineTest' \
  --tests '*NativeLibraryInjectionDialogFragmentTest' \
  :core:app:assembleRelease -x lint
```

The standalone shrinker/signing comparison used `com.android.tools.r8.R8 --release --classfile`, the AGP 8.5.0 bundled R8, apksig 8.5.0, the generated `proguard-android-optimize.txt-8.5.0`, and `core/app/proguard-rules.pro`. Its entry point loaded the same PKCS12 alias/password as `ApkPatchEngine`, signed a real binary-manifest APK, and verified all three schemes. The original rules reproduced the exact signing exception; retaining both the annotations and annotated models passed. Private probes, generated keys, and complete build logs remain excluded from Git under `.hoplite/artifacts/injection/`.

Existing toolchain/Kotlin/R8 warnings remain; none of the successful build or signature checks used CI. No commits or remote publication were performed.

## Changed repository files

- `core/app/build.gradle.kts`
- `core/app/proguard-rules.pro`
- `core/app/src/main/java/com/itsaky/androidide/apk/ApkPatchEngine.kt`
- `core/app/src/main/java/com/itsaky/androidide/fragments/dialogs/NativeLibraryInjectionDialogFragment.kt`
- `core/app/src/main/res/layout/layout_native_library_injection.xml`
- `core/resources/src/main/res/values/strings.xml`
- `core/app/src/test/java/com/itsaky/androidide/apk/ApkPatchEngineTest.kt`
- `core/app/src/test/java/com/itsaky/androidide/fragments/dialogs/NativeLibraryInjectionDialogFragmentTest.kt`
- `core/app/src/test/fixtures/native-injection/AndroidManifest.xml`
- `core/app/src/test/fixtures/native-injection/library.c`
- `core/app/src/test/fixtures/native-injection/README.md`
