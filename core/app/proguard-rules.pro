-ignorewarnings

-dontwarn **
-dontnote **
-dontobfuscate

# lsp4j models are deserialized with Gson
-keep class org.eclipse.lsp4j.** { *; }

# keep jgit whole: R8 crashes optimizing its bytecode
-keep class org.eclipse.jgit.** { *; }


# Services
-keep @com.google.auto.service.AutoService class ** {
}
-keepclassmembers class ** {
    @com.google.auto.service.AutoService <methods>;
}

# EventBus
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}

# Accessed reflectively
-keep class io.github.rosemoe.sora.widget.component.EditorAutoCompletion {
    io.github.rosemoe.sora.widget.component.EditorCompletionAdapter adapter;
    int currentSelection;
}
-keep class com.itsaky.androidide.projects.util.StringSearch {
    packageName(java.nio.file.Path);
}
-keep class * implements org.antlr.v4.runtime.Lexer {
    <init>(...);
}
-keep class com.itsaky.androidide.editor.api.IEditor { *; }
-keep class com.itsaky.androidide.utils.DialogUtils {  public <methods>; }

# APK Metadata

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

# Used in preferences

# Lots of native methods in tree-sitter
# There are some fields as well that are accessed from native field
-keepclasseswithmembers class ** {
    native <methods>;
}

-keep class com.itsaky.androidide.treesitter.** { *; }

# Retrofit 2
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp3
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Stat uploader
-keep class com.itsaky.androidide.stats.** { *; }

# Gson
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

## Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

## Themes
-keep enum com.itsaky.androidide.ui.themes.IDETheme {
  *;
}

## Contributor models - deserialized with GSON
-keep class * implements com.itsaky.androidide.contributors.Contributor {
  *;
}

# Suppress wissing class warnings
## These are used in annotation processing process in the Java Compiler
-dontwarn sun.reflect.annotation.AnnotationParser
-dontwarn sun.reflect.annotation.AnnotationType
-dontwarn sun.reflect.annotation.EnumConstantNotPresentExceptionProxy
-dontwarn sun.reflect.annotation.ExceptionProxy

## Used in Logback. We do not need this though.
-dontwarn jakarta.servlet.ServletContainerInitializer

## These are used in JGit
## TODO(itsaky): Verify if it is safe to ignore these warnings
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.ManagementFactory
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid