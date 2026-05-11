import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.mavenPublish)
    application
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(projects.winrtRuntime)
    implementation(projects.winrtMetadata)
    implementation(libs.kotlinpoet)
    testImplementation(libs.junit)
}

application {
    mainClass.set("io.github.composefluent.winrt.projections.generator.KotlinProjectionGeneratorCliKt")
}

tasks.withType<Test>().configureEach {
    minHeapSize = "128m"
    maxHeapSize = "768m"
    jvmArgs(
        "-XX:+UseSerialGC",
        "-XX:TieredStopAtLevel=1",
        "-XX:CICompilerCount=1",
        "-XX:HeapBaseMinAddress=8g",
    )
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.S01)
    signAllPublications()

    pom {
        name.set("winrt-generator")
        description.set("Kotlin source generator for WinRT and WinUI projection bindings")
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
