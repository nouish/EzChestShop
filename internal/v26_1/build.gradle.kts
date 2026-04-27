plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    compileOnly(project(":core"))
    paperweight.paperDevBundle("26.1.2.build.+")
    tasks["reobfJar"].enabled = false
}

repositories {
    maven("https://jitpack.io/") {
        content {
            includeModule("com.github.Anon8281", "UniversalScheduler")
        }
    }
}
