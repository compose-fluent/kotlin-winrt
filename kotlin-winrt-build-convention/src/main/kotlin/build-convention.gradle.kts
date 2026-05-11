import org.gradle.api.artifacts.VersionCatalogsExtension

// Generic build convention plugin: centralizes shared constants/config only.
// This plugin does NOT apply Kotlin plugins; each module keeps explicit plugin ownership.
val jvmToolchainVersion =
    extensions.getByType(VersionCatalogsExtension::class.java)
        .named("libs")
        .findVersion("jvmTarget")
        .get()
        .requiredVersion
        .toInt()

pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(jvmToolchainVersion)
    }
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        jvmToolchain(jvmToolchainVersion)
    }
}
