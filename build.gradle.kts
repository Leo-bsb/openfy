import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.2"
}

group = "com.openfy"
version = "0.4.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // motor de extração: mesmo artefato usado pelo Metrolist, target JVM desktop
    implementation("com.github.MetrolistGroup.innertubex:innertubex-desktop:v0.11.0")
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    testImplementation("junit:junit:4.13.2")
}

tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "Busca + extracao + stream local, sem UI"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.openfy.dev.SmokeKt")
}

tasks.register<JavaExec>("playerSmoke") {
    group = "verification"
    description = "Player real com mpv e volume zero"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.openfy.dev.player.PlayerSmokeKt")
}

tasks.register<JavaExec>("probe") {
    group = "verification"
    description = "Salva respostas cruas do YouTube Music para inspecao"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.openfy.dev.ProbeKt")
}

tasks.register<JavaExec>("featureSmoke") {
    group = "verification"
    description = "Valida busca filtrada, artista, playlists, explorar e radio"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.openfy.dev.FeatureSmokeKt")
}

tasks.register<JavaExec>("librarySmoke") {
    group = "verification"
    description = "Valida importador (URL/CSV/match) e backup JSON, sem UI"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.openfy.dev.LibrarySmokeKt")
}

tasks.register<JavaExec>("syncSmoke") {
    group = "verification"
    description = "Valida pull de playlists do Supabase no desktop"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.openfy.dev.SyncSmokeKt")
}

tasks.register<JavaExec>("recommendationSmoke") {
    group = "verification"
    description = "Valida ranking ponderado, no-repeat e diversidade da rádio"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.openfy.dev.RecommendationSmokeKt")
}

tasks.register<JavaExec>("recapSmoke") {
    group = "verification"
    description = "Valida o recap mensal local"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.openfy.dev.RecapSmokeKt")
}

tasks.register<JavaExec>("extract") {
    group = "verification"
    description = "Mede a extracao de um videoId: gradle extract -Pid=..."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.openfy.dev.ExtractSmokeKt")
    args(
        providers.gradleProperty("id").getOrElse("rA8xvaObO3c"),
        providers.gradleProperty("prewarm").getOrElse("false"),
    )
}

compose.desktop {
    application {
        mainClass = "com.openfy.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Openfy"
            packageVersion = "0.4.0"
            description = "Tocador pessoal de musica"
            vendor = "openfy"
            modules("java.sql", "java.naming", "jdk.unsupported", "java.net.http", "java.management", "jdk.httpserver")
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources-dist"))
            windows {
                iconFile.set(project.file("src/main/resources/logo.ico"))
                menu = true
                shortcut = true
                dirChooser = false
                perUserInstall = true
                upgradeUuid = "A7C2F4D8-1B3E-4F6A-9C2D-8E5B7A1F3C40"
            }
        }
    }
}
