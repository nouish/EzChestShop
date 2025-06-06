plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.6"
    id("com.gorylenko.gradle-git-properties") version "2.5.0"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":internal:v1_21_R1"))
    implementation(project(":internal:v1_21_R2"))
    implementation(project(":internal:v1_21_R3"))
}

gitProperties {
    gitPropertiesName = "version.properties"
    dotGitDirectory = project.rootProject.layout.projectDirectory.dir(".git")
    keys = listOf("git.branch", "git.build.version", "git.commit.id.abbrev")
}

tasks {
    shadowJar {
        archiveFileName = "${rootProject.name}-${version}.jar"

        dependencies {
            exclude(dependency("com.google.code.gson:gson"))
            exclude(dependency("org.slf4j:slf4j-api"))
        }

        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }

        exclude("META-INF/**")
        exclude("net/kyori/**")

        relocate("com.github.Anon8281.universalScheduler", "me.deadlight.ezchestshop.internal.scheduler")
        relocate("dev.triumphteam.gui", "me.deadlight.ezchestshop.internal.triumphgui")
        relocate("de.themoep.minedown", "me.deadlight.ezchestshop.internal.minedown")
        relocate("com.zaxxer.hikari", "me.deadlight.ezchestshop.internal.hikari")
        relocate("org.bstats", "me.deadlight.ezchestshop.internal.bstats")
    }

    assemble {
        dependsOn(shadowJar)
    }
}
