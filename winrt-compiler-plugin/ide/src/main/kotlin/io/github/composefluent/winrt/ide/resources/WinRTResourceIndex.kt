package io.github.composefluent.winrt.ide.resources

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.limits.FileSizeLimit
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

internal data class WinRTResourceLookup(val module: WinRTModuleData, val entries: List<WinRTResourceEntry>,
    val xamlFiles: List<String>, val pri: List<WinRTPriCandidate>, val frameworkDictionaries: List<String> = emptyList(),
    val frameworkKeys: List<WinRTFrameworkResourceKey> = emptyList())

/** File enumeration/staging-report IO never runs in PSI reference or completion requests. */
@Service(Service.Level.PROJECT)
class WinRTResourceIndex(private val project: Project, scope: CoroutineScope) : Disposable {
    @Volatile private var lookups = emptyList<WinRTResourceLookup>()
    private data class FrameworkKeys(val modified: Long, val size: Long, val keys: List<WinRTFrameworkResourceKey>)
    private val frameworkKeys = ConcurrentHashMap<String, FrameworkKeys>()
    init {
        scope.launch(Dispatchers.IO) {
            val projects = project.service<WinRTProjectService>()
            projects.refreshFromGradleCache()
            val changes = project.service<WinRTResourceChanges>()
            combine(projects.modules, changes.revision, projects.dependencyRevision) { modules, revision, dependencyRevision ->
                Triple(modules, revision, dependencyRevision)
            }.collectLatest { (modules, revision, dependencyRevision) ->
                delay(250)
                val next = modules.mapNotNull { module ->
                    coroutineContext.ensureActive()
                    runCatching { read(module, modules) }.getOrNull()
                }
                coroutineContext.ensureActive()
                if (modules == projects.modules.value && revision == changes.revision.value &&
                    dependencyRevision == projects.dependencyRevision.value) publish(next)
            }
        }
    }

    internal fun forFile(path: String): WinRTResourceLookup? = lookups.filter { lookup ->
        val roots = listOf(lookup.module.projectDirectory) + lookup.module.xamlCompilations.flatMap { it.sourceRoots }
        roots.any { runCatching { Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(it).toAbsolutePath().normalize()) }.getOrDefault(false) }
    }.maxByOrNull { it.module.projectDirectory.length }

    internal fun publish(next: List<WinRTResourceLookup>) {
        if (lookups == next) return
        lookups = next.toList()
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart("WinRT resource candidates changed")
        }
    }

    internal fun read(module: WinRTModuleData, modules: List<WinRTModuleData>): WinRTResourceLookup {
        val staged = module.packageLayouts.map { WinRTResourceCatalog.staged(module, it, modules) }.filter { it.error == null }
        val inputs = module.xamlCompilations.mapNotNull { compilation -> runCatching {
            Json.parseToJsonElement(Files.readString(Path.of(compilation.inputFile))).jsonObject
        }.getOrNull() }
        // XAMLC owns package-relative XAML paths (MSBuild_Link), including
        // dictionaries outside a Kotlin source root and referenced libraries.
        val markup = inputs.flatMap { input -> listOf("XamlPages", "XamlApplication", "XamlResources").flatMap { name ->
            input[name]?.jsonArray.orEmpty().mapNotNull { item ->
                val source = item.jsonObject["FullPath"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val target = item.jsonObject["MSBuild_Link"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: Path.of(source).fileName.toString()
                WinRTResourceEntry(target.replace('\\', '/'), source, module.projectPath)
            }
        } }
        val frameworks = inputs.flatMap { input -> input["ReferenceAssemblies"]?.jsonArray.orEmpty().flatMap { item ->
            val reference = item.jsonObject["FullPath"]?.jsonPrimitive?.content?.let(Path::of) ?: return@flatMap emptyList()
            frameworkDictionaries(reference, module.windowsSdkVersion)
        } }.distinct()
        val entries = (module.sourceSets.flatMap { WinRTResourceCatalog.sourceSet(module, it).entries } +
            staged.flatMap { it.entries } + markup)
            .distinctBy { it.target.lowercase() to it.source.lowercase() }
        val files = module.xamlCompilations.flatMap { it.sourceRoots }.distinct().flatMap { root ->
            val directory = Path.of(root)
            if (!Files.isDirectory(directory)) emptyList() else Files.walk(directory).use { paths -> paths.filter {
                Files.isRegularFile(it) && it.fileName.toString().endsWith(".xaml", true)
            }.limit(4096).map { it.toAbsolutePath().normalize().toString() }.toList() }
        }.plus(markup.map { it.source }).distinct()
        val vfs = LocalFileSystem.getInstance()
        (entries.map { it.source } + files + frameworks).distinct().forEach { vfs.refreshAndFindFileByNioFile(Path.of(it)) }
        val oversizedKeys = frameworks.flatMap { source ->
            val path = Path.of(source)
            val size = Files.size(path)
            if (size <= FileSizeLimit.getIntellisenseLimit("xaml")) return@flatMap emptyList()
            val modified = Files.getLastModifiedTime(path).toMillis()
            frameworkKeys.compute(source) { _, old ->
                if (old?.modified == modified && old.size == size) old
                else FrameworkKeys(modified, size, WinRTFrameworkResourceKey.read(path))
            }!!.keys
        }
        return WinRTResourceLookup(module, entries, files, staged.flatMap { it.candidates }, frameworks, oversizedKeys)
    }

    private fun frameworkDictionaries(reference: Path, sdkVersion: String): List<String> {
        val candidates = mutableListOf<Path>()
        if (reference.fileName.toString().equals("Microsoft.UI.Xaml.winmd", true)) {
            // The restored WinUI package's native design-time dictionary. Never
            // scan another installed version or select an unrelated NuGet SDK.
            val directory = reference.parent ?: return emptyList()
            candidates.add(directory.resolve("Microsoft.UI/Themes/generic.xaml"))
            if (directory.fileName.toString().equals("metadata", true))
                candidates.add(directory.parent.resolve("lib/native/Microsoft.UI/Themes/generic.xaml"))
        }
        val references = generateSequence(reference.parent) { it.parent }.firstOrNull {
            it.fileName?.toString().equals("References", true) || it.fileName?.toString().equals("UnionMetadata", true)
        }
        if (references != null) {
            val version = references.relativize(reference).getName(0).toString().takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) }
                ?: sdkVersion.takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) }
            if (version != null) candidates.add(references.parent.resolve(
                "DesignTime/CommonConfiguration/Neutral/UAP/$version/Generic/themeresources.xaml"))
        }
        return candidates.filter(Files::isRegularFile).map { it.toAbsolutePath().normalize().toString() }
    }
    override fun dispose() = Unit
}
