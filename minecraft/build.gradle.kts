plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.7.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.3.3")
}
