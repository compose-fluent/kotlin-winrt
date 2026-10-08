package io.github.composefluent.winrt.ide.resources

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path

internal object WinRTManifestAssets {
    fun choose(project: Project, manifest: VirtualFile, onChosen: (VirtualFile) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle("Choose manifest image")
                    .withFileFilter { it.extension?.lowercase() in listOf("png", "jpg", "jpeg") },
                project, manifest.parent, onChosen,
            )
        }
    }

    fun import(project: Project, manifest: VirtualFile, source: VirtualFile): String {
        val root = manifest.parent
        val rootPath = root.toNioPath()
        if (source.toNioPath().startsWith(rootPath)) return rootPath.relativize(source.toNioPath()).toString().replace('/', '\\')
        var imported: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project, "Import manifest image", null, Runnable {
            val assets = root.findChild("Assets") ?: root.createChildDirectory(this, "Assets")
            require(assets.findChild(source.name) == null) { "Assets/${source.name} already exists. Choose that image or rename the new file." }
            imported = source.copy(this, assets, source.name)
        })
        return rootPath.relativize(requireNotNull(imported).toNioPath()).toString().replace('/', '\\')
    }

    /** Base package paths can refer to a scale-qualified asset instead of a literal file. */
    fun resolve(root: Path, value: String): Path? {
        val path = runCatching { root.resolve(value.replace('\\', '/')).normalize() }.getOrNull() ?: return null
        if (value.isBlank() || !path.startsWith(root)) return null
        if (Files.isRegularFile(path)) return path
        val parent = path.parent ?: return null
        if (!Files.isDirectory(parent)) return null
        val name = path.fileName.toString()
        val stem = name.substringBeforeLast('.')
        val suffix = name.substringAfterLast('.')
        return Files.list(parent).use { files -> files.filter { candidate ->
            Files.isRegularFile(candidate) && candidate.fileName.toString().let { it.startsWith("$stem.scale-") || it.startsWith("$stem.targetsize-") } && candidate.fileName.toString().endsWith(".$suffix", true)
        }.sorted().findFirst().orElse(null) }
    }
}
