pluginManagement {
    plugins {
        kotlin("jvm") version "2.4.10"
        kotlin("multiplatform") version "2.4.10"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "Luak"

include(
    "luak-core",
    "luak-jvm"
)
