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
        // против локально установленного PhpStorm ровно целевой сборки PS-262.10968.76
        local("/home/stepanov/.local/share/JetBrains/Toolbox/apps/phpstorm-2")
    }
    testImplementation("junit:junit:4.13.2")
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
