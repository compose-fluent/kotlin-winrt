import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "published-runtime-only-consumer-fixture"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "isolatedRuntimePublication"
                    url = uri(providers.gradleProperty("kotlinWinRT.test.repository").get())
                }
            }
            filter {
                includeGroup("io.github.compose-fluent")
            }
        }
        mavenCentral()
    }
}
