package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testkit.runner.GradleRunner

/** Injected TestKit dependencies pin KGP. Alternate compilers must use the published plugin graph. */
internal fun projectionTestCompilerVersions(): List<String> =
    if (System.getProperty("winrt.test.repository") == null) listOf(KotlinWinRTCompilerVersions.default)
    else KotlinWinRTCompilerVersions.supported

internal fun GradleRunner.withProjectionTestEnvironment(): GradleRunner {
    val repository = System.getProperty("winrt.test.repository") ?: return withPluginClasspath()
    val version = requireNotNull(System.getProperty("winrt.test.publicationVersion"))
    val directory = requireNotNull(projectDir)
    val settings = listOf("settings.gradle", "settings.gradle.kts").map(directory::resolve).first { it.isFile }
    val marker = "// Published projection compiler matrix"
    if (!settings.readText().contains(marker)) {
        settings.writeText(settings.readText().replaceFirst("pluginManagement {", """
            pluginManagement {
                $marker
                repositories { maven { url = uri("$repository") } }
                plugins { id("io.github.compose-fluent.windows-toolkit").version("$version") }
        """.trimIndent() + "\n"))
    }
    return this
}
