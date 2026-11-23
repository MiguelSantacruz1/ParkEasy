import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("ParkEasyDatabase") {
            packageName.set("com.ejemplo.parkeasy.data.db")
            dialect(libs.sqldelight.dialect.sqlite)
        }
    }
}

group = "com.ejemplo.parkeasy"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.sqldelight.driver.sqlite)
    implementation(libs.sqldelight.coroutines)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(compose.desktop.uiTestJUnit4)
}

tasks.test {
    useJUnitPlatform()
}

compose.resources {
    packageOfResClass = "com.ejemplo.parkeasy.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.ejemplo.parkeasy.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "ParkEasy"
            packageVersion = "1.0.0"
            // El runtime recortado del instalador necesita JDBC (SQLite) y nombres/zonas para java.sql y java.naming.
            modules("java.sql", "java.naming", "jdk.unsupported", "java.net.http")
        }
    }
}

// Perfiles de datos de prueba: gradlew run -Pdemo  |  gradlew run -Pperf
tasks.withType<JavaExec>().configureEach {
    when {
        project.hasProperty("perf") -> systemProperty("parkeasy.profile", "perf")
        project.hasProperty("demo") -> systemProperty("parkeasy.profile", "demo")
    }
}
