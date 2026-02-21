plugins {
    kotlin("jvm") version "2.3.10"
    application
}

group = "eu.ellerotta"
version = "0.20"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("eu.ellerotta.tdkotlinify.MainKt")
}

kotlin {
    jvmToolchain(25)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "eu.ellerotta.tdkotlinify.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

