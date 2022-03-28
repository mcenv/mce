import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    kotlin("multiplatform") version "1.6.10"
    kotlin("plugin.allopen") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.2"
    application
}

version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvm("server") {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js("client") {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.4")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.2")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val serverMain by getting {
        }

        val serverTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.2")
            }
        }

        val clientMain by getting {
        }

        val clientTest by getting {
        }
    }
}

application {
    mainClass.set("mce.cli.MainKt")
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("serverTest") {
            if (this is JvmBenchmarkTarget) {
                jmhVersion = "1.34"
            }
        }
    }
}
