plugins {
    `java-library`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    // Provided dependencies
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.apache.logging.log4j:log4j-core:2.24.1")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    // Shaded dependencies
    api("com.github.Anon8281:UniversalScheduler:0.1.6")
    implementation("de.themoep:minedown:1.7.1-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("dev.triumphteam:triumph-gui:3.1.11")
    implementation("org.bstats:bstats-bukkit:3.1.0")

    // Optional integrations
    compileOnly("com.palmergames.bukkit.towny:towny:0.101.1.7")
    compileOnly("net.coreprotect:coreprotect:22.4")
    compileOnly("com.github.Slimefun:Slimefun4:RC-37")
    compileOnly("net.alex9849.advancedregionmarket:advancedregionmarket:3.5.5")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13")
}

tasks {
    processResources {
        filesMatching("plugin.yml") {
            filter {
                it.replace("\${project.version}", version.toString())
            }
        }
    }
}
