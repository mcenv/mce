plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.6.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.3.3")
}
