plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()
    // Sign only when the in-memory key property is present so that local mavenLocal
    // publishing (without credentials) works out of the box.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    pom {
        name.set(provider { project.name })
        // Each module sets its own `description` in its build.gradle.kts.
        description.set(provider { project.description ?: project.name })
        url.set("https://github.com/compose-fluent/kotlin-winrt")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("composefluent")
                name.set("Compose Fluent")
                url.set("https://github.com/compose-fluent")
            }
        }
        scm {
            url.set("https://github.com/compose-fluent/kotlin-winrt")
            connection.set("scm:git:git://github.com/compose-fluent/kotlin-winrt.git")
            developerConnection.set("scm:git:ssh://git@github.com/compose-fluent/kotlin-winrt.git")
        }
    }
}
