plugins {
    `java-library`
    antlr
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    api("org.antlr:antlr4-runtime:4.13.2")
}
