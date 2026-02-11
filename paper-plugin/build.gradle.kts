plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.1"
    id("com.gorylenko.gradle-git-properties") version "2.5.7"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":internal:v1_21_R1"))
    implementation(project(":internal:v1_21_R2"))
    implementation(project(":internal:v1_21_R3"))
    implementation(project(":internal:v1_21_R6"))
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

        relocate("com.github.Anon8281.universalScheduler", "me.deadlight.ezchestshop.thirdparty.scheduler")
        relocate("dev.triumphteam.gui", "me.deadlight.ezchestshop.thirdparty.triumphgui")
        relocate("de.themoep.minedown", "me.deadlight.ezchestshop.thirdparty.minedown")
        relocate("com.zaxxer.hikari", "me.deadlight.ezchestshop.thirdparty.hikari")
        relocate("org.bstats", "me.deadlight.ezchestshop.thirdparty.bstats")
    }

    assemble {
        dependsOn(shadowJar)
    }
}
