package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.composefluent.windows.toolkit.gradle.toSafeRelativePath
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.resources.WinRTResourceEntry
import io.github.composefluent.winrt.ide.resources.WinRTResourceIndex
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Source documents (including unsaved edits) are the designer's resource input. */
internal class WinRTXamlPreviewSources(private val project: Project, private val module: WinRTModuleData,
    private val files: List<String>, source: String) {
    private val lookup = project.service<WinRTResourceIndex>().forFile(source)
    private val roots = (module.xamlCompilations.flatMap { it.sourceRoots } + module.sourceSets.flatMap { it.kotlinRoots })
        .map { Path.of(it).toAbsolutePath().normalize() }.distinct().sortedByDescending { it.nameCount }
    fun read(path: String): String = ReadAction.compute<String?, RuntimeException> {
        LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/'))?.let { FileDocumentManager.getInstance().getCachedDocument(it)?.text }
    } ?: Files.readString(Path.of(path))

    fun target(path: String): String = lookup?.entries?.firstOrNull { it.source.replace('\\', '/').equals(path.replace('\\', '/'), true) }?.target
        ?: Path.of(path).toAbsolutePath().normalize().let { file ->
            roots.firstOrNull { file.startsWith(it) }?.relativize(file)?.toString()?.replace('\\', '/') ?: file.fileName.toString()
        }

    private val documents by lazy {
        (files.map { WinRTResourceEntry(target(it), it, module.projectPath) } + lookup?.entries.orEmpty())
            .filter { it.target.endsWith(".xaml", true) }.associateBy { it.target.replace('\\', '/').lowercase() }
    }
    private val dictionariesByClass by lazy {
        documents.values.mapNotNull { entry -> runCatching {
            val markup = io.github.composefluent.winrt.ide.hotreload.WinRTHotReloadMarkup.parse(read(entry.source))
            if (markup.isResourceDictionary && markup.className.isNotEmpty()) markup.className to entry else null
        }.getOrNull() }.toMap()
    }

    fun resolve(uri: String, origin: String): WinRTXamlDesignResource? {
        if (uri.startsWith("using:")) {
            val entry = dictionariesByClass[uri.removePrefix("using:")] ?: return null
            return WinRTXamlDesignResource(read(entry.source), entry.target)
        }
        val base = URI("ms-appx", "", "/" + origin.replace('\\', '/'), null)
        val resolved = base.resolve(uri.replace('\\', '/').replace(" ", "%20")).normalize()
        if (resolved.scheme != "ms-appx" || !resolved.authority.isNullOrEmpty()) return null
        val entry = documents[resolved.path.trimStart('/').lowercase()] ?: return null
        return WinRTXamlDesignResource(read(entry.source), entry.target)
    }

    fun application(): String? = files.firstOrNull { path ->
        // An application resource document is usable before XAMLC has exported
        // page declarations or the user's Application subclass has compiled.
        runCatching { io.github.composefluent.winrt.ide.hotreload.WinRTHotReloadMarkup.parse(read(path)).isApplication }
            .getOrDefault(false)
    }

    /** Native image/font loading retains package URIs; only passive assets are copied. */
    fun stageAssets() {
        val host = module.staticPreview?.workingDirectory?.let(Path::of)?.toAbsolutePath()?.normalize() ?: return
        val expected = Path.of(module.buildDirectory).toAbsolutePath().normalize().resolve("kotlin-winrt/xaml-sdk-preview/host")
        require(host == expected) { "The design host must use this module's owned preview directory." }
        lookup?.entries.orEmpty().filter { entry -> entry.target.substringAfterLast('.').lowercase() in
            setOf("png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "ico", "ttf", "otf") }.forEach { entry ->
            val input = Path.of(entry.source)
            if (!Files.isRegularFile(input)) return@forEach
            val output = host.resolve(entry.target.toSafeRelativePath("Design asset")).normalize()
            require(output.startsWith(host))
            if (!Files.isRegularFile(output) || Files.size(input) != Files.size(output) ||
                Files.getLastModifiedTime(input) != Files.getLastModifiedTime(output)) {
                Files.createDirectories(output.parent)
                Files.copy(input, output, REPLACE_EXISTING)
                Files.setLastModifiedTime(output, Files.getLastModifiedTime(input))
            }
        }
    }
}
