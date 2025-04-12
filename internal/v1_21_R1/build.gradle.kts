plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-SNAPSHOT"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    compileOnly(project(":core"))
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
}

repositories {
    maven("https://jitpack.io/") {
        content {
            includeModule("com.github.Anon8281", "UniversalScheduler")
        }
    }
}
