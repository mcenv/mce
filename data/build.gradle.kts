plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
}

application {
    mainClass.set("mce.data.MainKt")
}
