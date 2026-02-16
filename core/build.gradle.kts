plugins {
    `java-library`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    // Provided dependencies
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.apache.logging.log4j:log4j-core:2.24.1") {
        because("Provided by Minecraft.")
    }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        because("Mandatory plugin-dependency for EzChestShopReborn.")
    }
    compileOnly("com.googlecode.json-simple:json-simple:1.1.1") {
        because("Prior to Paper 1.21.6, json-simple was a transitive dependency. It is still available at runtime.")
        exclude("junit").because("We don't want to bundle JUnit.")
    }

    // Shaded dependencies
    api("com.github.Anon8281:UniversalScheduler:0.1.7")
    implementation("de.themoep:minedown:1.7.1-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("dev.triumphteam:triumph-gui:3.1.13")
    implementation("org.bstats:bstats-bukkit:3.1.0")

    // Optional integrations
    compileOnly("com.palmergames.bukkit.towny:towny:0.102.0.7")
    compileOnly("net.coreprotect:coreprotect:23.1")
    compileOnly("com.github.Slimefun:Slimefun4:RC-37")
    compileOnly("net.alex9849.advancedregionmarket:advancedregionmarket:3.5.5")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.15")
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
