// Configures the JVM toolchain version for projects that apply the Kotlin JVM plugin.
// Apply this alongside alias(libs.plugins.kotlinJvm) in your module; this plugin does NOT
// apply the Kotlin JVM plugin itself — it only sets the shared jvmToolchain constant.
pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(libs.versions.jvmTarget.get().toInt())
    }
}
