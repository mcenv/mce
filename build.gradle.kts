import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform") version "1.6.21"
    kotlin("plugin.allopen") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
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
        browser {
            @Suppress("OPT_IN_USAGE")
            distribution {
                directory = file("$projectDir/app/distributions")
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.2")
                implementation("io.ktor:ktor-serialization-kotlinx:2.0.0-eap-363")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val serverMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.4")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.3.5")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.3.2")
                implementation("io.ktor:ktor-server-core:2.0.0-eap-363")
                implementation("io.ktor:ktor-server-netty:2.0.0-eap-363")
                implementation("io.ktor:ktor-server-websockets:2.0.0-eap-363")
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
                implementation("org.jetbrains.kotlin-wrappers:kotlin-css:1.0.0-pre.333")
                implementation("io.ktor:ktor-client-core:2.0.2-eap-388")
                implementation("io.ktor:ktor-client-js:2.0.2-eap-388")
                implementation("io.ktor:ktor-client-websockets:2.0.2-eap-389")
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
            this as JvmBenchmarkTarget
            jmhVersion = "1.35"
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

tasks.getByName<Sync>("installDist") {
    dependsOn(tasks.getByName("clientBrowserWebpack"))
    destinationDir = file("$projectDir/app/install")
}
