plugins {
    id("com.android.library") version "9.1.0"
}

android {
    namespace = "com.editor.es.syntax"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
