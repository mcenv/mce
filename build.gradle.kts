plugins {
    kotlin("jvm") version "1.6.10"
}

version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-RC3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
