package io.github.composefluent.winrt.ide.templates

import java.util.zip.ZipInputStream

/** Portable Maven publications and wrapper files shipped with the IDE plugin. */
internal object WinRTBundledToolchain {
    val VERSION: String = requireNotNull(
        WinRTBundledToolchain::class.java.getResourceAsStream("/templates/toolchain-version.txt"),
    ) { "The bundled toolchain version is missing. Reinstall the plugin." }
        .bufferedReader().use { it.readText().trim() }
        .also { require(it.matches(Regex("\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?"))) }
    const val DIRECTORY = ".kotlin-winrt/toolchain"
    fun available() = WinRTBundledToolchain::class.java.getResource("/templates/toolchain.zip") != null

    fun files(): Map<String, ByteArray> = buildMap {
        // buildMap's receiver belongs to the IDE's Kotlin stdlib, not this plugin.
        val stream = requireNotNull(WinRTBundledToolchain::class.java.getResourceAsStream("/templates/toolchain.zip")) { "The IDE plugin's bundled Kotlin WinRT toolchain is missing. Reinstall the plugin." }
        ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                require(!name.startsWith('/') && '\\' !in name && name.split('/').none { it == ".." } && ':' !in name) { "Invalid bundled toolchain path." }
                require(name.startsWith("repository/") || name in listOf("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties"))
                put(if (name.startsWith("repository/")) "$DIRECTORY/$name" else name, zip.readAllBytes())
            }
        }
        require(keys.any { it.contains("windows-toolkit-gradle-plugin/$VERSION/") && it.endsWith(".pom") }) { "The bundled Gradle plugin publication is missing." }
        require(keys.any { it.contains("winrt-runtime-jvm/$VERSION/") && it.endsWith(".jar") }) { "The bundled JVM runtime is missing." }
        require(keys.any { it.contains("winrt-runtime-mingwx64/$VERSION/") && it.endsWith(".klib") }) { "The bundled mingwX64 runtime is missing." }
        require(keys.any { it.contains("winrt-authoring-mingwx64/$VERSION/") && it.endsWith(".klib") }) { "The bundled mingwX64 authoring support is missing." }
    }
}
