package io.github.composefluent.winrt.ide.templates

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Files
import java.nio.file.Path

object WinRTTemplateWriter {
    fun create(project: Project, target: Path, files: Map<String, ByteArray>, buildRoot: Path? = null) {
        val directory = target.toAbsolutePath().normalize()
        val settings = buildRoot?.toAbsolutePath()?.normalize()?.let { root ->
            require(directory.startsWith(root) && directory != root) { "Create the module inside the selected Gradle build." }
            require(directory.parent == root) { "Create the module directly under the Gradle build root." }
            root.resolve("settings.gradle.kts").also { require(Files.isRegularFile(it)) { "Select a Gradle build with settings.gradle.kts." } }
        }
        val paths = files.keys.map { relative ->
            directory.resolve(relative).normalize().also {
                require(it.startsWith(directory) && it != directory) { "Invalid template path: $relative" }
                require(!Files.exists(it)) { "A file already exists: $it" }
            }
        }
        // Resolve symlinks before allowing the new directory to be created inside an existing build.
        if (settings != null) {
            require(!Files.exists(directory)) { "The module directory already exists: $directory" }
            require(directory.parent.toRealPath() == settings.parent.toRealPath()) { "Invalid module location." }
        }
        WriteCommandAction.runWriteCommandAction(project, "Create Kotlin WinRT ${if (settings == null) "project" else "module"}", null, Runnable {
            val settingsDocument = settings?.let {
                val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it))
                requireNotNull(FileDocumentManager.getInstance().getDocument(file))
            }
            val moduleName = directory.fileName.toString()
            require(settingsDocument == null || !settingsDocument.text.contains("\":$moduleName\"")) { "The module is already included: :$moduleName" }
            paths.forEach { require(!Files.exists(it)) { "A file already exists: $it" } }
            val created = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
            try {
                files.forEach { (relative, bytes) ->
                    val path = directory.resolve(relative)
                    val parent = VfsUtil.createDirectoryIfMissing(path.parent.toString()) ?: error("Cannot create ${path.parent}")
                    val file = parent.createChildData(this, path.fileName.toString())
                    created += file
                    file.setBinaryContent(bytes)
                }
                settingsDocument?.insertString(settingsDocument.textLength, "\ninclude(${WinRTTemplates.kotlinString(":$moduleName")})\n")
            } catch (failure: Throwable) {
                created.asReversed().forEach { if (it.isValid) it.delete(this) }
                throw failure
            }
        })
    }
}
