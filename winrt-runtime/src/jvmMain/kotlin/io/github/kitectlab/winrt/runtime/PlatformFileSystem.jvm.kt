package io.github.kitectlab.winrt.runtime

import java.nio.file.Files
import java.nio.file.Path

internal actual object PlatformFileSystem {
    actual fun systemProperty(name: String): String? = System.getProperty(name)

    actual fun environmentVariable(name: String): String? = System.getenv(name)

    actual fun isRegularFile(path: String): Boolean = Files.isRegularFile(Path.of(path))

    actual fun isDirectory(path: String): Boolean = Files.isDirectory(Path.of(path))

    actual fun absolutePath(path: String): String = Path.of(path).toAbsolutePath().toString()

    actual fun fileName(path: String): String = Path.of(path).fileName.toString()

    actual fun parent(path: String): String? = Path.of(path).parent?.toString()

    actual fun resolve(path: String, child: String): String = Path.of(path).resolve(child).toString()

    actual fun readText(path: String): String = Files.readString(Path.of(path))

    actual fun walkFiles(root: String): List<String> =
        Files.walk(Path.of(root)).use { stream ->
            stream.map(Path::toString).toList()
        }
}
