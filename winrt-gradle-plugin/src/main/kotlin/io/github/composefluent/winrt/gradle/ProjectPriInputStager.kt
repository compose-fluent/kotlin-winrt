package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

internal class ProjectPriInputStager(
    private val projectPriRoot: Path,
    projectPriInitialPath: String,
    defaultProjectResourceRoot: Path?,
    private val targetPaths: Map<String, String>,
    private val excludedFromBuildPaths: Set<String>,
) {
    private val initialPath = projectPriInitialPath.toSafeRelativePath("projectPriInitialPath")
    private val projectResourceRoot = defaultProjectResourceRoot?.toAbsolutePath()?.normalize()
    private val normalizedTargetPaths = targetPaths.entries.associate { (source, target) ->
        Path.of(source).toNormalizedInputPathKey() to target
    }
    private val normalizedExcludedFromBuildPaths = excludedFromBuildPaths
        .map { Path.of(it).toNormalizedInputPathKey() }
        .toSet()

    fun stage(
        componentPriFiles: Collection<Path>,
        componentPriBaseRoot: Path,
        appxResourceFiles: Collection<AppxResourceInput>,
        explicitResourceFiles: Collection<Path>,
        explicitLayoutFiles: Collection<Path>,
        explicitContentFiles: Collection<Path>,
        explicitEmbedFiles: Collection<Path>,
        defaultResourceFiles: Collection<Path>,
        defaultLayoutFiles: Collection<Path>,
        defaultContentFiles: Collection<Path>,
        includeDefaultProjectResources: Boolean,
    ): Set<ApplicationPackageItem> {
        val items = linkedSetOf<ApplicationPackageItem>()
        val priorities = mutableMapOf<String, Int>()
        stageComponentPris(componentPriFiles, componentPriBaseRoot, items, priorities)
        stageAppxResources(appxResourceFiles, items, priorities)
        stageExplicitResources(explicitResourceFiles, items, priorities)
        stageExplicitLayoutResources(explicitLayoutFiles, items, priorities)
        stageExplicitContentResources(explicitContentFiles, items, priorities)
        stageExplicitEmbedFiles(explicitEmbedFiles, items, priorities)
        if (includeDefaultProjectResources) {
            stageDefaultResources(defaultResourceFiles, items, priorities)
            stageDefaultLayoutResources(defaultLayoutFiles, items, priorities)
            stageDefaultContentResources(defaultContentFiles, items, priorities)
        }
        return items
    }

    private fun stageAppxResources(
        sources: Collection<AppxResourceInput>,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
    ) {
        sources.asSequence()
            .filterNot { input ->
                input.relativePath.parent == null && input.relativePath.name.equals("AppxManifest.xml", ignoreCase = true)
            }
            .filter { input -> input.source.isRegularFile() }
            .filter { input -> input.source.name.endsWith(".resw", ignoreCase = true) }
            .sortedBy { it.relativePathString.lowercase() }
            .forEach { input ->
                copyInput(
                    ApplicationPackageItemKind.PriResource,
                    input.source,
                    projectPriRoot.resolve(initialPath).resolve(input.relativePath),
                    items,
                    priorities,
                    APPX_RESOURCE_PRIORITY,
                )
            }
        val layoutInputs = sources.asSequence()
            .filterNot { input ->
                input.relativePath.parent == null && input.relativePath.name.equals("AppxManifest.xml", ignoreCase = true)
            }
            .filter { input -> input.source.isRegularFile() && isProjectPriLayoutFile(input.source) }
            .sortedBy { it.relativePathString.lowercase() }
            .map { input ->
                ProjectPriLayoutInput(
                    input.source,
                    projectPriRoot.resolve(initialPath).resolve(input.relativePath),
                )
            }
            .toList()
        stageFilteredLayoutInputs(layoutInputs, items, priorities, APPX_RESOURCE_PRIORITY)
        sources.asSequence()
            .filterNot { input ->
                input.relativePath.parent == null && input.relativePath.name.equals("AppxManifest.xml", ignoreCase = true)
            }
            .filter { input -> input.source.isRegularFile() && isProjectPriContentFile(input.source) }
            .sortedBy { it.relativePathString.lowercase() }
            .forEach { input ->
                copyInput(
                    ApplicationPackageItemKind.Content,
                    input.source,
                    projectPriRoot.resolve(initialPath).resolve(input.relativePath),
                    items,
                    priorities,
                    APPX_RESOURCE_PRIORITY,
                )
            }
    }

    private fun stageComponentPris(
        sources: Collection<Path>,
        baseRoot: Path,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
    ) {
        val normalizedBaseRoot = baseRoot.toAbsolutePath().normalize()
        sources.asSequence()
            .filter { it.isRegularFile() }
            .sorted()
            .forEach { source ->
                val normalizedSource = source.toAbsolutePath().normalize()
                val relativeTarget = if (normalizedSource.startsWith(normalizedBaseRoot)) normalizedSource.relativeTo(normalizedBaseRoot) else source.fileName
                copyInput(
                    ApplicationPackageItemKind.ComponentPri,
                    source,
                    projectPriRoot.resolve(relativeTarget),
                    items,
                    priorities,
                    COMPONENT_PRI_PRIORITY,
                )
            }
    }

    private fun stageExplicitResources(
        sources: Collection<Path>,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
    ) {
        stageExplicitFileTree(sources, ApplicationPackageItemKind.PriResource, projectPriRoot, items, priorities)
    }

    private fun stageExplicitLayoutResources(
        sources: Collection<Path>,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
    ) {
        val inputs = explicitFileTree(sources, ::isProjectPriLayoutFile)
            .map { ProjectPriLayoutInput(it.source, projectPriRoot.resolve(initialPath).resolve(it.relativeTarget)) }
            .toList()
        stageFilteredLayoutInputs(inputs, items, priorities, EXPLICIT_RESOURCE_PRIORITY)
    }

    private fun stageExplicitContentResources(
        sources: Collection<Path>,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
    ) {
        stageExplicitFileTree(sources, ApplicationPackageItemKind.Content, projectPriRoot, items, priorities)
    }

    private fun stageExplicitEmbedFiles(
        sources: Collection<Path>,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
    ) {
        stageExplicitFileTree(sources, ApplicationPackageItemKind.Embed, projectPriRoot.resolve("embed"), items, priorities)
    }

    private fun stageDefaultResources(
        sources: Collection<Path>,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
    ) {
        val root = projectResourceRoot ?: return
        sources.asSequence()
            .filter { it.isRegularFile() && it.name.endsWith(".resw", ignoreCase = true) }
            .filterNot { it.isExcludedFromBuild() }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                val normalizedSource = source.toAbsolutePath().normalize()
                if (normalizedSource.startsWith(root)) {
                    copyInput(
                        ApplicationPackageItemKind.PriResource,
                        source,
                        projectPriRoot.resolve(initialPath).resolve(normalizedSource.relativeTo(root)),
                        items,
                        priorities,
                        DEFAULT_RESOURCE_PRIORITY,
                    )
                }
            }
    }

    private fun stageDefaultLayoutResources(
        sources: Collection<Path>,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
    ) {
        val root = projectResourceRoot ?: return
        val inputs = sources.asSequence()
            .filter { it.isRegularFile() && isProjectPriLayoutFile(it) }
            .filterNot { it.isExcludedFromBuild() }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .mapNotNull { source ->
                val normalizedSource = source.toAbsolutePath().normalize()
                if (normalizedSource.startsWith(root)) ProjectPriLayoutInput(source, projectPriRoot.resolve(initialPath).resolve(normalizedSource.relativeTo(root))) else null
            }
            .toList()
        stageFilteredLayoutInputs(inputs, items, priorities, DEFAULT_RESOURCE_PRIORITY)
    }

    private fun stageDefaultContentResources(
        sources: Collection<Path>,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
    ) {
        val root = projectResourceRoot ?: return
        sources.asSequence()
            .filter { it.isRegularFile() && isProjectPriContentFile(it) }
            .filterNot { it.isExcludedFromBuild() }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                val normalizedSource = source.toAbsolutePath().normalize()
                if (normalizedSource.startsWith(root)) {
                    copyInput(
                        ApplicationPackageItemKind.Content,
                        source,
                        projectPriRoot.resolve(initialPath).resolve(normalizedSource.relativeTo(root)),
                        items,
                        priorities,
                        DEFAULT_RESOURCE_PRIORITY,
                    )
                }
            }
    }

    private fun stageExplicitFileTree(
        sources: Collection<Path>,
        kind: ApplicationPackageItemKind,
        targetRoot: Path,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
    ) {
        explicitFileTree(sources) { true }
            .forEach { input ->
                copyInput(
                    kind,
                    input.source,
                    targetRoot.resolve(initialPath).resolve(input.relativeTarget),
                    items,
                    priorities,
                    EXPLICIT_RESOURCE_PRIORITY,
                )
            }
    }

    private fun explicitFileTree(
        sources: Collection<Path>,
        includeFile: (Path) -> Boolean = { true },
    ): Sequence<ProjectPriFileInput> =
        sources.asSequence()
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .flatMap { source ->
                if (source.isExcludedFromBuild()) {
                    emptySequence()
                } else if (!Files.exists(source)) {
                    throw GradleException("Declared project PRI input does not exist: ${source.toAbsolutePath().normalize()}")
                } else if (source.isDirectory()) {
                    val explicitRootTarget = source.explicitTargetPath()
                    Files.walk(source).use { stream ->
                        stream.asSequence()
                            .filter { it.isRegularFile() && includeFile(it) }
                            .filterNot { it.isExcludedFromBuild() }
                            .sorted()
                            .map { child -> ProjectPriFileInput(child, child.toProjectPriRelativePath(source, explicitRootTarget)) }
                            .toList()
                            .asSequence()
                    }
                } else if (source.isRegularFile() && includeFile(source)) {
                    sequenceOf(ProjectPriFileInput(source, source.toSingleFileRelativeTarget()))
                } else {
                    emptySequence()
                }
            }

    private fun stageFilteredLayoutInputs(
        inputs: List<ProjectPriLayoutInput>,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
        priority: Int,
    ) {
        val xbfTargets = inputs.asSequence()
            .filter { it.source.name.endsWith(".xbf", ignoreCase = true) }
            .map { it.target.toNormalizedPackagePathKey() }
            .toSet()
        val embedRoot = projectPriRoot.resolve("embed")
        inputs.forEach { input ->
            if (input.source.name.endsWith(".xaml", ignoreCase = true) && input.target.toXbfTargetKey() in xbfTargets) {
                recordInput(ApplicationPackageItemKind.ExcludedLayout, input.source, input.target, items, priorities, priority)
                return@forEach
            }
            if (input.source.name.endsWith(".xbf", ignoreCase = true)) {
                val embedTarget = embedRoot.resolve(input.target.relativeTo(embedRoot.parent))
                copyInput(ApplicationPackageItemKind.Embed, input.source, embedTarget, items, priorities, priority)
                return@forEach
            }
            copyInput(ApplicationPackageItemKind.Layout, input.source, input.target, items, priorities, priority)
        }
    }

    private fun copyInput(
        kind: ApplicationPackageItemKind,
        source: Path,
        target: Path,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
        priority: Int,
    ): Boolean {
        val item = applicationPackageItem(kind, source, target)
        val existing = items.firstOrNull { it.targetKey == item.targetKey }
        if (existing != null) {
            val existingPriority = priorities[existing.targetKey] ?: DEFAULT_RESOURCE_PRIORITY
            if (priority < existingPriority) return false
            if (priority == existingPriority && existing.source != item.source) {
                throw GradleException(
                    "Conflicting project PRI inputs target the same package path " +
                        "${item.target}: ${existing.source} and ${item.source}.",
                )
            }
            items.remove(existing)
        }
        items.add(item)
        priorities[item.targetKey] = priority
        GradleFileOperations.copyFile(source, target)
        return true
    }

    private fun recordInput(
        kind: ApplicationPackageItemKind,
        source: Path,
        target: Path,
        items: MutableSet<ApplicationPackageItem>,
        priorities: MutableMap<String, Int>,
        priority: Int,
    ): Boolean {
        val item = applicationPackageItem(kind, source, target)
        val existing = items.firstOrNull { it.targetKey == item.targetKey }
        if (existing != null) {
            val existingPriority = priorities[existing.targetKey] ?: DEFAULT_RESOURCE_PRIORITY
            if (priority <= existingPriority) return false
            items.remove(existing)
        }
        priorities[item.targetKey] = priority
        return items.add(item)
    }

    private fun Path.explicitTargetPath(): Path? {
        val configured = normalizedTargetPaths[toNormalizedInputPathKey()] ?: return null
        return configured.toSafeRelativePath("projectPriTargetPaths")
    }

    private fun Path.isExcludedFromBuild(): Boolean =
        toNormalizedInputPathKey() in normalizedExcludedFromBuildPaths

    private fun Path.toSingleFileRelativeTarget(): Path {
        val normalizedSource = toAbsolutePath().normalize()
        val explicitTarget = explicitTargetPath()
        return explicitTarget ?: if (projectResourceRoot != null && normalizedSource.startsWith(projectResourceRoot)) {
            normalizedSource.relativeTo(projectResourceRoot)
        } else {
            Path.of(name)
        }
    }

    private fun Path.toProjectPriRelativePath(fallbackRoot: Path, explicitRootTarget: Path?): Path {
        val normalizedSource = toAbsolutePath().normalize()
        if (explicitRootTarget != null) return explicitRootTarget.resolve(relativeTo(fallbackRoot))
        return if (projectResourceRoot != null && normalizedSource.startsWith(projectResourceRoot)) normalizedSource.relativeTo(projectResourceRoot) else relativeTo(fallbackRoot)
    }

    private fun Path.toXbfTargetKey(): String {
        val xbfFileName = fileName.toString().replaceAfterLast('.', "xbf")
        return parent.resolve(xbfFileName).toNormalizedPackagePathKey()
    }

    private data class ProjectPriFileInput(val source: Path, val relativeTarget: Path)

    private data class ProjectPriLayoutInput(val source: Path, val target: Path)
}

private const val DEFAULT_RESOURCE_PRIORITY = 10
private const val APPX_RESOURCE_PRIORITY = 20
private const val EXPLICIT_RESOURCE_PRIORITY = 30
private const val COMPONENT_PRI_PRIORITY = 100

internal fun String.toSafeRelativePath(label: String): Path {
    val normalized = trim().replace('\\', '/')
    if (normalized.isBlank()) return Path.of("")
    val path = Path.of(normalized).normalize()
    require(!normalized.startsWith("/") && !WINDOWS_DRIVE_PATH.matches(normalized) && !path.isAbsolute && !path.startsWith("..")) {
        "$label must be a relative path inside the package root: $this"
    }
    return path
}

private val WINDOWS_DRIVE_PATH = Regex("""^[A-Za-z]:($|/.*)""")

private fun isProjectPriLayoutFile(path: Path): Boolean =
    path.name.endsWith(".xaml", ignoreCase = true) || path.name.endsWith(".xbf", ignoreCase = true)

private fun isProjectPriContentFile(path: Path): Boolean {
    val fileName = path.name
    return listOf(".png", ".bmp", ".jpg", ".dds", ".tif", ".tga", ".gif")
        .any { extension -> fileName.endsWith(extension, ignoreCase = true) }
}
