package io.github.composefluent.winrt.ide.templates

import java.util.zip.ZipInputStream

/** Portable Maven publications and wrapper files shipped with the IDE plugin. */
internal object WinRTBundledToolchain {
    const val VERSION = "0.1.0-SNAPSHOT"
    const val DIRECTORY = ".kotlin-winrt/toolchain"
    fun available() = javaClass.getResource("/templates/toolchain.zip") != null

    fun files(): Map<String, ByteArray> = buildMap {
        val stream = requireNotNull(javaClass.getResourceAsStream("/templates/toolchain.zip")) { "The IDE plugin's bundled Kotlin WinRT toolchain is missing. Reinstall the plugin." }
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
    }
}
