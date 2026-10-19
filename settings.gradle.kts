pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://developer.huawei.com/repo/") {
            content { includeGroupByRegex("com\\.huawei\\..*") }
        }
    }
}
rootProject.name = "Wo"
include(":core", ":app")
