package io.github.kitectlab.winrt.runtime

internal actual object PlatformFileSystem {
    actual fun systemProperty(name: String): String? = null

    actual fun environmentVariable(name: String): String? = null

    actual fun isRegularFile(path: String): Boolean = TODO()

    actual fun isDirectory(path: String): Boolean = TODO()

    actual fun absolutePath(path: String): String = TODO()

    actual fun fileName(path: String): String = TODO()

    actual fun parent(path: String): String? = TODO()

    actual fun resolve(path: String, child: String): String = TODO()

    actual fun readText(path: String): String = TODO()

    actual fun walkFiles(root: String): List<String> = TODO()
}
