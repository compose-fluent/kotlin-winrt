package io.github.kitectlab.winrt.runtime

internal expect object PlatformFileSystem {
    fun systemProperty(name: String): String?

    fun environmentVariable(name: String): String?

    fun isRegularFile(path: String): Boolean

    fun isDirectory(path: String): Boolean

    fun absolutePath(path: String): String

    fun fileName(path: String): String

    fun parent(path: String): String?

    fun resolve(path: String, child: String): String

    fun readText(path: String): String

    fun walkFiles(root: String): List<String>
}
