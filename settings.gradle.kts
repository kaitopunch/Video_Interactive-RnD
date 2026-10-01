pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PSRemote"

// One app module on purpose (confirm.md Q4): a single demo screen does not pay for a module graph.
// Layers are packages inside :app — LLM.md §2 states the rules that replace module boundaries.
include(":app")
// Not a layer: a com.android.test module that drives the installed app from outside (Macrobenchmark,
// baseline profile). Nothing in :app depends on it (LLM.md §3, §10).
include(":benchmark")
