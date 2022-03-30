import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

kotlin {
    jvm("server") {
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js("client") {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.2")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val serverMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.4")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
                implementation("io.ktor:ktor-server-core:2.0.0-beta-1")
                implementation("io.ktor:ktor-server-netty:2.0.0-ide-debug-eap-225")
                implementation("io.ktor:ktor-server-websockets:2.0.0-beta-1")
                implementation("io.ktor:ktor-serialization-kotlinx:2.0.0-beta-1")
            }
        }

        val serverTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.2")
            }
        }

        val clientMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.7.5")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-css:1.0.0-pre.323-kotlin-1.6.10")
            }
        }

        val clientTest by getting {
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
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

application {
    mainClass.set("mce.cli.MainKt")
}

distributions {
    main {
        contents {
            from("$buildDir/libs") {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                rename("${rootProject.name}-server", rootProject.name)
                into("lib")
            }
        }
    }
}
