plugins {
    kotlin("jvm")
    id("io.papermc.paperweight.userdev")
    id("com.github.johnrengelman.shadow")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    maven("https://maven.mineclay.com/repository/zhuagroup") {
        credentials {
            username = findProperty("clayUsername").toString()
            password = findProperty("clayPassword").toString()
        }
    }
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("1.18.2-R0.1-SNAPSHOT")
    implementation(project(":"))

    compileOnly("com.mineclay:circle-link-bukkit:1.15.17-SNAPSHOT")
    compileOnly("com.mineclay:libmineclay:1.15.0-SNAPSHOT")
    compileOnly("com.trychen.clay:ClayCoreSpigot:3.0.5")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    expand("version" to version)
}

tasks.shadowJar {
    dependencies {
        exclude(dependency("org.jetbrains:"))
        exclude(dependency("org.jetbrains.kotlin:"))
        exclude(dependency("org.jooq:"))
        exclude(dependency("xyz.jpenilla:"))
        exclude(dependency("net.fabricmc:"))
    }
    relocate("com.mineclay.tclite", "org.totemcraft.camera.tclite")
}
tasks.assemble {
    dependsOn(tasks.reobfJar)
}
tasks.reobfJar {
    outputJar.set(layout.buildDirectory.file("libs/TotemCamera-${version}.jar"))
}
