import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.time.Duration

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

val generatedBuildInfo = layout.buildDirectory.dir("generated-src/build-info/commonMain/kotlin")

val generateBuildInfo = tasks.register("generateBuildInfo") {
    group = "build"
    description = "Generates multiplatform build metadata."
    val output = generatedBuildInfo.map { it.file("net/blueva/luak/BuildInfo.kt") }
    outputs.file(output)

    doLast {
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                package net.blueva.luak

                internal object BuildInfo {
                    const val VERSION: String = "Luak ${project.version}"
                }
                """.trimIndent() + "\n"
            )
        }
    }
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvm()
    js {
        nodejs()
    }
    wasmJs {
        nodejs()
    }
    wasmWasi {
        nodejs()
    }
    linuxX64()
    linuxArm64()
    mingwX64()
    macosX64()
    macosArm64()

    jvmToolchain(17)
    withSourcesJar(publish = true)

    sourceSets {
        // I/O stream classes with zero platform dependency (no JS interop, no
        // WASI syscalls) - shared by every non-JVM, non-Native target,
        // including WASI.
        jsMain {
            kotlin.srcDir("src/portableIoMain/kotlin")
        }
        wasmJsMain {
            kotlin.srcDir("src/portableIoMain/kotlin")
        }
        wasmWasiMain {
            kotlin.srcDir("src/portableIoMain/kotlin")
        }
        // Platform-neutral actuals specific to a JS-hosted engine's process
        // model (still no JS interop themselves, but only ever used by the
        // two JS-hosted targets) - not shared with wasmWasiMain, which has
        // its own Platform.wasmWasi.kt/WeakReference.wasmWasi.kt.
        jsMain {
            kotlin.srcDir("src/nonJvmMain/kotlin")
        }
        wasmJsMain {
            kotlin.srcDir("src/nonJvmMain/kotlin")
        }
        // JS-engine-specific actuals (process/console/node:fs) - only for the
        // two targets that actually run inside a JS host. Never wired into
        // wasmWasiMain: a WASI host has none of that.
        jsMain {
            kotlin.srcDir("src/jsHostMain/kotlin")
        }
        wasmJsMain {
            kotlin.srcDir("src/jsHostMain/kotlin")
        }
        // 64-bit file offsets: fseek/ftell use C `long`, which is 32 bits on
        // Windows and 64 elsewhere, so the two families get their own actual.
        linuxX64Main {
            kotlin.srcDir("src/nativePosixMain/kotlin")
        }
        linuxArm64Main {
            kotlin.srcDir("src/nativePosixMain/kotlin")
        }
        macosX64Main {
            kotlin.srcDir("src/nativePosixMain/kotlin")
        }
        macosArm64Main {
            kotlin.srcDir("src/nativePosixMain/kotlin")
        }
        mingwX64Main {
            kotlin.srcDir("src/nativeWindowsMain/kotlin")
        }
        commonMain {
            kotlin.srcDir(generatedBuildInfo)
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
    dependsOn(generateBuildInfo)
}

// Bounds every test task so an unresumed coroutine continuation fails
// deterministically instead of hanging CI; kotlin.test has no portable
// per-test timeout, but Task.timeout works on every target uniformly.
tasks.matching { it.name.endsWith("Test") }.configureEach {
    timeout.set(Duration.ofMinutes(10))
}

tasks.matching { it.name.endsWith("SourcesJar", ignoreCase = true) }.configureEach {
    dependsOn(generateBuildInfo)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Luak Core")
            description.set("Kotlin Multiplatform embeddable Lua 5.2 runtime, compiler, and parser.")
            url.set("https://github.com/BluevaDevelopment/Luak")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("blueva")
                    name.set("Blueva Development")
                    url.set("https://github.com/BluevaDevelopment")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/BluevaDevelopment/Luak.git")
                developerConnection.set("scm:git:ssh://git@github.com/BluevaDevelopment/Luak.git")
                url.set("https://github.com/BluevaDevelopment/Luak")
            }
        }
    }
    repositories {
        maven {
            name = "BluevaRepo"
            url = uri("https://repo.blueva.net/releases")
            credentials {
                username = providers.environmentVariable("BLUEVA_REPO_USERNAME").orNull
                password = providers.environmentVariable("BLUEVA_REPO_SECRET").orNull
            }
        }
    }
}
