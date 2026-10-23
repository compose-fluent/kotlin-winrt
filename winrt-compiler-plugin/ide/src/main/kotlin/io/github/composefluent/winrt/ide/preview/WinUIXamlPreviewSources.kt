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
import kotlinx.serialization.json.*
import io.github.composefluent.winrt.metadata.WinRTMetadataModelCodec
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Source documents (including unsaved edits) are the designer's resource input. */
internal class WinUIXamlPreviewSources(private val project: Project, private val module: WinRTModuleData,
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
            val markup = io.github.composefluent.winrt.ide.hotreload.WinUIXamlHotReloadMarkup.parse(read(entry.source))
            if (markup.isResourceDictionary && markup.className.isNotEmpty()) markup.className to entry else null
        }.getOrNull() }.toMap()
    }

    fun resolve(uri: String, origin: String): WinUIXamlDesignResource? {
        if (uri.startsWith("using:")) {
            val entry = dictionariesByClass[uri.removePrefix("using:")] ?: return null
            return WinUIXamlDesignResource(read(entry.source), entry.target)
        }
        val base = URI("ms-appx", "", "/" + origin.replace('\\', '/'), null)
        val resolved = base.resolve(uri.replace('\\', '/').replace(" ", "%20")).normalize()
        if (resolved.scheme != "ms-appx" || !resolved.authority.isNullOrEmpty()) return null
        val entry = documents[resolved.path.trimStart('/').lowercase()] ?: return null
        return WinUIXamlDesignResource(read(entry.source), entry.target)
    }

    fun application(): String? = files.firstOrNull { path ->
        // An application resource document is usable before XAMLC has exported
        // page declarations or the user's Application subclass has compiled.
        runCatching { io.github.composefluent.winrt.ide.hotreload.WinUIXamlHotReloadMarkup.parse(read(path)).isApplication }
            .getOrDefault(false)
    }

    /** Include every design-data/dictionary document and actual WinMD bytes, including
     * unsaved editor text. Resource-index readiness prevents caching a partial lookup.
     */
    fun projectDocumentKey(): String? = runCatching {
        val index = lookup ?: return null
        val references = module.xamlCompilations.map { it.inputFile }.distinct().sorted()
        if (references.isEmpty()) return null
        val manifests = references.map { Files.readString(Path.of(it)) }
        val metadata = manifests.flatMap { text ->
            Json.parseToJsonElement(text).jsonObject["ReferenceAssemblies"]?.jsonArray.orEmpty().mapNotNull {
                val item = it.jsonObject
                (item["FullPath"] ?: item["ItemSpec"])?.jsonPrimitive?.content?.let(Path::of)
            }
        }.filter { it.toString().endsWith(".winmd", true) }.distinct()
        if (metadata.isEmpty()) return null
        WinUIXamlDesignDocumentCache.key(buildList {
            add(module.toString())
            addAll(manifests)
            add(WinRTMetadataModelCodec.cacheKey(metadata))
            documents.values.sortedBy { it.target }.forEach { add(it.target); add(it.source); add(read(it.source)) }
            add(index.entries.toString())
        })
    }.getOrNull()

    /** Native image/font loading retains package URIs; only passive assets are copied. */
    fun stageAssets() {
        val host = module.staticPreview?.workingDirectory?.let(Path::of)?.toAbsolutePath()?.normalize() ?: return
        val previewRoot = Path.of(module.buildDirectory).toAbsolutePath().normalize().resolve("kotlin-winrt/xaml-sdk-preview")
        require(host == previewRoot.resolve("host") || host == previewRoot.resolve("AppX")) {
            "The design host must use this module's owned preview directory."
        }
        lookup?.entries.orEmpty().filter { entry -> entry.target.substringAfterLast('.').lowercase() in
            setOf("png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "ico", "ttf", "otf") }.forEach { entry ->
            val input = Path.of(entry.source)
            if (!Files.isRegularFile(input)) return@forEach
            val output = host.resolve(entry.target.toSafeRelativePath("Design asset")).normalize()
            require(output.startsWith(host))
            // Gradle may deploy identical assets with different timestamps. The
            // running WinUI host keeps package icons open, so even rewriting the
            // same bytes can fail with a sharing violation. Compare content before
            // touching the destination; actual asset changes still surface errors.
            if (Files.isRegularFile(output) && Files.mismatch(input, output) == -1L) return@forEach
            Files.createDirectories(output.parent)
            Files.copy(input, output, REPLACE_EXISTING)
            Files.setLastModifiedTime(output, Files.getLastModifiedTime(input))
        }
    }
}
