plugins {
    kotlin("multiplatform")
    `maven-publish`
}

// The shared runtime, compiled for a web page: one self-contained script that
// puts `LuakWeb` on `globalThis`. Published next to the klibs so a site pins
// the same Luak version its server runs, without a Kotlin build of its own.
val generatedVersion = layout.buildDirectory.dir("generated-src/web-version/jsMain/kotlin")

val generateWebVersion = tasks.register("generateWebVersion") {
    group = "build"
    val output = generatedVersion.map { it.file("net/blueva/luak/web/WebVersion.kt") }
    val version = project.version.toString()
    inputs.property("version", version)
    outputs.file(output)
    doLast {
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText("package net.blueva.luak.web\n\ninternal const val LUAK_WEB_VERSION: String = \"$version\"\n")
        }
    }
}

kotlin {
    js {
        browser {
            webpackTask {
                mainOutputFileName.set("luak-web.js")
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain {
            kotlin.srcDir(generateWebVersion.map { generatedVersion.get() })
            dependencies {
                implementation(project(":luak-core"))
            }
        }
    }
}

val webBundle = tasks.named("jsBrowserProductionWebpack")
val bundleFile = layout.buildDirectory.file("kotlin-webpack/js/productionExecutable/luak-web.js")

// Loads the bundle the way a page would (plain script, no module system) and
// checks it answers like the JVM runtime does.
val verifyWebBundle = tasks.register<Exec>("verifyWebBundle") {
    group = "verification"
    dependsOn(webBundle)
    val script = layout.projectDirectory.file("verify.mjs")
    inputs.file(script)
    inputs.file(bundleFile)
    commandLine("node", script.asFile.absolutePath, bundleFile.get().asFile.absolutePath)
}

tasks.named("check") {
    dependsOn(verifyWebBundle)
}

publishing {
    publications {
        create<MavenPublication>("web") {
            artifactId = "luak-web"
            artifact(bundleFile) {
                extension = "js"
                builtBy(webBundle)
            }
            pom {
                name.set("Luak Web")
                description.set("The Luak runtime as a single browser script (globalThis.LuakWeb).")
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

// Only the bundle is this module's product. The Kotlin plugin's own
// publications (a klib nobody should depend on) would claim the same
// coordinates and overwrite the pom.
tasks.withType<AbstractPublishToMaven>().configureEach {
    onlyIf { publication.name == "web" }
}
