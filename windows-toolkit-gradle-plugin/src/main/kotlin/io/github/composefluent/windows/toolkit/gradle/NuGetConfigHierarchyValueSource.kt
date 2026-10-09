package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Track NuGet's configuration files without tracking every sibling in their ancestor directories. */
abstract class NuGetConfigHierarchyValueSource : ValueSource<List<File>, NuGetConfigHierarchyValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val baseDirectory: Property<String>
        val userConfigFile: Property<String>
    }

    override fun obtain(): List<File> {
        val files = linkedSetOf<Path>()
        var current: Path? = Path.of(parameters.baseDirectory.get()).toAbsolutePath().normalize()
        while (current != null) {
            if (Files.isDirectory(current)) {
                Files.list(current).use { entries ->
                    entries.filter { path ->
                        Files.isRegularFile(path) && path.fileName.toString().equals("NuGet.Config", ignoreCase = true)
                    }.forEach { path -> files.add(path.toAbsolutePath().normalize()) }
                }
            }
            current = current.parent
        }
        parameters.userConfigFile.orNull?.let(Path::of)?.let { path ->
            if (Files.isRegularFile(path)) files.add(path.toAbsolutePath().normalize())
        }
        return files.sortedBy { it.toString().lowercase() }.map(Path::toFile)
    }
}
