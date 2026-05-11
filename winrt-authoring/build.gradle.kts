import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(projects.winrtRuntime)
    implementation(projects.winrtMetadata)
    testImplementation(libs.junit)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.S01)
    signAllPublications()

    pom {
        name.set("winrt-authoring")
        description.set("WinRT and WinUI authoring support for the Kotlin projection")
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
