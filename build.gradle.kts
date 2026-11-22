import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
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
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.sqldelight.driver.sqlite)
    implementation(libs.sqldelight.coroutines)

    testImplementation(kotlin("test"))
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
        }
    }
}
