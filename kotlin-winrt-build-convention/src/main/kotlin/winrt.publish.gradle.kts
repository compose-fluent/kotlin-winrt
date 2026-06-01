plugins {
    id("com.vanniktech.maven.publish")
}

val releaseTagRegex = Regex("v\\d+\\.\\d+\\.\\d+(-.*)?")

fun resolveWinRtVersion() = providers
    .gradleProperty("winrt.baseVersion")
    .orElse("0.1.0")
    .zip(
        providers
            .environmentVariable("GITHUB_REF_TYPE")
            .zip(providers.environmentVariable("GITHUB_REF_NAME")) { refType, refName ->
                if (refType == "tag") refName else ""
            }
            .orElse(providers.gradleProperty("winrt.releaseTag").orElse("")),
    ) { baseVersion, releaseTag ->
        if (releaseTag.isNotBlank() && releaseTag.matches(releaseTagRegex)) {
            releaseTag.removePrefix("v")
        } else {
            "$baseVersion-SNAPSHOT"
        }
    }

group = "io.github.compose-fluent"
version = resolveWinRtVersion().get()

mavenPublishing {
    publishToMavenCentral()
    // Sign only when a real in-memory key and password are present. GitHub Actions maps
    // missing secrets to empty Gradle properties, and enabling signing for those values
    // makes Gradle fail later while evaluating the signing task onlyIf predicate.
    val hasSigningKey = providers.gradleProperty("signingInMemoryKey")
        .map(String::isNotBlank)
        .orElse(false)
    val hasSigningPassword = providers.gradleProperty("signingInMemoryKeyPassword")
        .map(String::isNotBlank)
        .orElse(false)
    if (hasSigningKey.zip(hasSigningPassword, Boolean::and).get()) {
        signAllPublications()
    }

    pom {
        name.set(project.name)
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
