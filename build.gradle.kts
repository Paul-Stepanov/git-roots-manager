import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.stepanov"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // download.jetbrains.com отдаёт 451 (региональная блокировка) — собираем
        // против локально установленного PhpStorm целевой сборки PS-262
        local(providers.provider { idePath() })
    }
    testImplementation("junit:junit:4.13.2")
}

fun idePath(): String =
    localProperties().getProperty("idePath")
        ?: System.getenv("PHPSTORM_IDE_PATH")
        ?: error("Укажите локальный PhpStorm: local.properties (idePath=..., см. local.properties.example) или переменную PHPSTORM_IDE_PATH")

fun localProperties(): Properties =
    Properties().apply {
        rootProject.file("local.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }

intellijPlatform {
    // чисто-Kotlin плагин без Java/UI-форм: инструментация байткода не нужна,
    // иначе тянет java-compiler-ant-tasks с cache-redirector.jetbrains.com
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
        }
    }
}

tasks {
    test {
        useJUnit()
    }
}
