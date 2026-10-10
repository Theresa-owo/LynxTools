plugins {
    kotlin("jvm") version "2.3.20"
    id("net.neoforged.moddev") version "2.0.148"
}
group = "net.ciyuan.lynxtools"
version = "1.0.0"
repositories { mavenCentral() }
kotlin { jvmToolchain(25) }
neoForge {
    version = "26.1.2.115"
    runs { create("server") { server(); programArgument("--nogui") } }
    mods { create("lynxtools") { sourceSet(sourceSets.main.get()) } }
}
dependencies {
    implementation(kotlin("stdlib"))
    jarJar(kotlin("stdlib"))
    implementation("org.xerial:sqlite-jdbc:3.51.3.0")
    jarJar("org.xerial:sqlite-jdbc:3.51.3.0")
    compileOnly("net.luckperms:api:5.5")
    for (artifact in listOf("adventure-text-minimessage", "adventure-api", "adventure-key")) {
        implementation("net.kyori:$artifact:4.26.1")
        jarJar("net.kyori:$artifact:4.26.1")
    }
    for (artifact in listOf("examination-api", "examination-string")) {
        implementation("net.kyori:$artifact:1.3.0")
        jarJar("net.kyori:$artifact:1.3.0")
    }
    runtimeOnly(files("dev-libs/LuckPerms-NeoForge-5.5.52.jar"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("com.electronwill.night-config:toml:3.8.3")
    testRuntimeOnly("org.slf4j:slf4j-api:2.0.17")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
tasks.jar { archiveBaseName.set("LynxTools"); from(listOf("LICENSE.md", "NOTICE.md")) { into("META-INF") } }

// Optional, separate probe mod. Never included in the production JAR or normal build.
val verification by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
}
tasks.register<Jar>("verificationJar") {
    dependsOn(tasks.named("verificationClasses"))
    archiveBaseName.set("LynxTools-verification")
    from(verification.output)
}
tasks.register<Zip>("serverDistribution") {
    dependsOn(tasks.named("build"))
    archiveFileName.set("LynxTools-${project.version}-server.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(tasks.jar) { into("mods") }
    from("src/main/resources/defaults/Config.toml") { into("plugins/LynxTools") }
    from("src/main/resources/defaults/zh_cn.toml") { into("plugins/LynxTools/Lang") }
    from("README.md", "LICENSE.md", "NOTICE.md")
    from("docs") { into("docs"); exclude("plans/**") }
}
tasks.register<Zip>("sourceArchive") {
    archiveFileName.set("LynxTools-${project.version}-sources.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(projectDir) {
        include("src/**", "docs/**", "gradle/wrapper/**", "*.gradle.kts", "gradle.properties", "gradlew.ps1", ".gitignore", ".gitattributes", "README.md", "LICENSE.md", "NOTICE.md")
    }
}
