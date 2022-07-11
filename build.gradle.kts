import kotlinx.benchmark.gradle.JvmBenchmarkTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform") version "1.7.10"
    kotlin("plugin.allopen") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.4"
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
            webpackTask {
                outputFileName = "${rootProject.name}.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.3")
                implementation("io.ktor:ktor-serialization-kotlinx:2.0.1")
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.3.3")
                implementation("io.ktor:ktor-server-core-jvm:2.0.3")
                implementation("io.ktor:ktor-server-netty-jvm:2.0.3")
                implementation("io.ktor:ktor-server-websockets-jvm:2.0.3")
            }
        }

        val serverTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.4")
            }
        }

        val clientMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.7.5")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-css:1.0.0-pre.354")
                implementation("io.ktor:ktor-client-core-js:2.0.3")
                implementation("io.ktor:ktor-client-js:2.0.3")
                implementation("io.ktor:ktor-client-websockets-js:2.0.3")
            }
        }

        val clientTest by getting {
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "18"
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
    applicationName = rootProject.name
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
