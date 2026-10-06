pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GifPack"
include(":app")

// Core
include(":core:common")
include(":core:ui")
include(":core:data")
include(":core:domain")
include(":core:network")
include(":core:storage")
include(":core:conversion")

// Features
include(":feature:home")
include(":feature:search")
include(":feature:library")
include(":feature:detail")
include(":feature:convert")
include(":feature:backpack")
