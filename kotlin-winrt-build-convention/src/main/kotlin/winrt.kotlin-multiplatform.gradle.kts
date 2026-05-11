// Configures the JVM toolchain version for projects that apply the Kotlin Multiplatform plugin.
// Apply this alongside alias(libs.plugins.kotlinMultiplatform) in your module; this plugin does NOT
// apply the Kotlin Multiplatform plugin itself — it only sets the shared jvmToolchain constant.
pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
        jvmToolchain(libs.versions.jvmTarget.get().toInt())
    }
}
