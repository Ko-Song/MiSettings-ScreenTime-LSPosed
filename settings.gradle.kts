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
        exclusiveContent {
            forRepository {
                maven("https://api.xposed.info/")
            }
            filter {
                includeGroup("de.robv.android.xposed")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "MiSettings-ScreenTime-LSPosed"
include(":app")
