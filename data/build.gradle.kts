plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":minecraft"))
}

application {
    mainClass.set("mce.data.MainKt")
}
