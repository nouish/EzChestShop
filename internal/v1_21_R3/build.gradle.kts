plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    compileOnly(project(":core"))
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
}

repositories {
    maven("https://jitpack.io/") {
        content {
            includeModule("com.github.Anon8281", "UniversalScheduler")
        }
    }
}
