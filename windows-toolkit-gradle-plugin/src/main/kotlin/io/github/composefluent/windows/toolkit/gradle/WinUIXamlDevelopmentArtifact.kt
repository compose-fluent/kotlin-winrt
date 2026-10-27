package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

/** Only development run copies receive permission. Published dependencies and build
 * outputs remain disabled, even when the input happens to be a development copy. */
internal fun stampWinUIXamlDevelopmentRuntime(directory: Path, enabled: Boolean) {
    val lib = directory.resolve("lib")
    if (!Files.isDirectory(lib)) return
    Files.list(lib).use { jars -> jars.filter { it.toString().endsWith(".jar", true) }.forEach { jar ->
        stampWinUIXamlDevelopmentRuntimeJar(jar, enabled)
    } }
}

internal fun stampWinUIXamlDevelopmentRuntimeJar(jar: Path, enabled: Boolean) {
    val attribute = Attributes.Name("Kotlin-WinRT-Development")
    var replacement: Path? = null
    try {
        JarFile(jar.toFile()).use { source ->
            if (source.getEntry("io/github/composefluent/winrt/runtime/WinUIXamlDevelopmentBuildPolicy.class") == null) return
            val manifest = source.manifest?.let(::Manifest) ?: Manifest()
            if (enabled && manifest.mainAttributes[attribute] == "true" || !enabled && manifest.mainAttributes[attribute] == null) return
            check(source.entries().asSequence().none { it.name.startsWith("META-INF/") && it.name.endsWith(".SF", true) }) {
                "Cannot modify a signed runtime archive for development: $jar"
            }
            manifest.mainAttributes.putIfAbsent(Attributes.Name.MANIFEST_VERSION, "1.0")
            if (enabled) manifest.mainAttributes[attribute] = "true" else manifest.mainAttributes.remove(attribute)
            val temporary = Files.createTempFile(jar.parent, ".development-runtime-", ".jar")
            replacement = temporary
            JarOutputStream(Files.newOutputStream(temporary), manifest).use { output ->
                source.entries().asSequence().filterNot { it.name.equals("META-INF/MANIFEST.MF", true) }.forEach { entry ->
                    output.putNextEntry(ZipEntry(entry.name).apply { time = 0 })
                    if (!entry.isDirectory) source.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
        replacement?.let { Files.move(it, jar, StandardCopyOption.REPLACE_EXISTING) }
    } finally { replacement?.let(Files::deleteIfExists) }
}
