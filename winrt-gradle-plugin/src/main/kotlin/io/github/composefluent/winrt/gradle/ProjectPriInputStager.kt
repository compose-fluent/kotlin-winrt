package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
    private val initialPath = projectPriInitialPath.toSafeRelativePath()
    private val projectResourceRoot = defaultProjectResourceRoot?.toAbsolutePath()?.normalize()

    fun stage(
        componentPriFiles: Collection<Path>,
        componentPriBaseRoot: Path,
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
        stageComponentPris(componentPriFiles, componentPriBaseRoot, items)
        stageExplicitResources(explicitResourceFiles, items)
        stageExplicitLayoutResources(explicitLayoutFiles, items)
        stageExplicitContentResources(explicitContentFiles, items)
        stageExplicitEmbedFiles(explicitEmbedFiles, items)
        if (includeDefaultProjectResources) {
            stageDefaultResources(defaultResourceFiles, items)
            stageDefaultLayoutResources(defaultLayoutFiles, items)
            stageDefaultContentResources(defaultContentFiles, items)
        }
        return items
    }

    private fun stageComponentPris(sources: Collection<Path>, baseRoot: Path, items: MutableSet<ApplicationPackageItem>) {
        val normalizedBaseRoot = baseRoot.toAbsolutePath().normalize()
        sources.asSequence()
            .filter { it.isRegularFile() }
            .sorted()
            .forEach { source ->
                val normalizedSource = source.toAbsolutePath().normalize()
                val relativeTarget = if (normalizedSource.startsWith(normalizedBaseRoot)) normalizedSource.relativeTo(normalizedBaseRoot) else source.fileName
                copyInput(ApplicationPackageItemKind.ComponentPri, source, projectPriRoot.resolve(relativeTarget), items)
            }
    }

    private fun stageExplicitResources(sources: Collection<Path>, items: MutableSet<ApplicationPackageItem>) {
        stageExplicitFileTree(sources, ApplicationPackageItemKind.PriResource, projectPriRoot, items)
    }

    private fun stageExplicitLayoutResources(sources: Collection<Path>, items: MutableSet<ApplicationPackageItem>) {
        val inputs = explicitFileTree(sources, ::isProjectPriLayoutFile)
            .map { ProjectPriLayoutInput(it.source, projectPriRoot.resolve(initialPath).resolve(it.relativeTarget)) }
            .toList()
        stageFilteredLayoutInputs(inputs, items)
    }

    private fun stageExplicitContentResources(sources: Collection<Path>, items: MutableSet<ApplicationPackageItem>) {
        stageExplicitFileTree(sources, ApplicationPackageItemKind.Content, projectPriRoot, items)
    }

    private fun stageExplicitEmbedFiles(sources: Collection<Path>, items: MutableSet<ApplicationPackageItem>) {
        stageExplicitFileTree(sources, ApplicationPackageItemKind.Embed, projectPriRoot.resolve("embed"), items)
    }

    private fun stageDefaultResources(sources: Collection<Path>, items: MutableSet<ApplicationPackageItem>) {
        val root = projectResourceRoot ?: return
        sources.asSequence()
            .filter { it.isRegularFile() && it.name.endsWith(".resw", ignoreCase = true) }
            .filterNot { it.isExcludedFromBuild() }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                val normalizedSource = source.toAbsolutePath().normalize()
                if (normalizedSource.startsWith(root)) {
                    copyInput(ApplicationPackageItemKind.PriResource, source, projectPriRoot.resolve(initialPath).resolve(normalizedSource.relativeTo(root)), items)
                }
            }
    }

    private fun stageDefaultLayoutResources(sources: Collection<Path>, items: MutableSet<ApplicationPackageItem>) {
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
        stageFilteredLayoutInputs(inputs, items)
    }

    private fun stageDefaultContentResources(sources: Collection<Path>, items: MutableSet<ApplicationPackageItem>) {
        val root = projectResourceRoot ?: return
        sources.asSequence()
            .filter { it.isRegularFile() && isProjectPriContentFile(it) }
            .filterNot { it.isExcludedFromBuild() }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                val normalizedSource = source.toAbsolutePath().normalize()
                if (normalizedSource.startsWith(root)) {
                    copyInput(ApplicationPackageItemKind.Content, source, projectPriRoot.resolve(initialPath).resolve(normalizedSource.relativeTo(root)), items)
                }
            }
    }

    private fun stageExplicitFileTree(
        sources: Collection<Path>,
        kind: ApplicationPackageItemKind,
        targetRoot: Path,
        items: MutableSet<ApplicationPackageItem>,
    ) {
        explicitFileTree(sources) { true }
            .forEach { input ->
                copyInput(kind, input.source, targetRoot.resolve(initialPath).resolve(input.relativeTarget), items)
            }
    }

    private fun explicitFileTree(
        sources: Collection<Path>,
        includeFile: (Path) -> Boolean = { true },
    ): Sequence<ProjectPriFileInput> =
        sources.asSequence()
            .filter { Files.exists(it) }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .flatMap { source ->
                if (source.isDirectory() && !source.isExcludedFromBuild()) {
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
                } else if (source.isRegularFile() && includeFile(source) && !source.isExcludedFromBuild()) {
                    sequenceOf(ProjectPriFileInput(source, source.toSingleFileRelativeTarget()))
                } else {
                    emptySequence()
                }
            }

    private fun stageFilteredLayoutInputs(inputs: List<ProjectPriLayoutInput>, items: MutableSet<ApplicationPackageItem>) {
        val xbfTargets = inputs.asSequence()
            .filter { it.source.name.endsWith(".xbf", ignoreCase = true) }
            .map { it.target.toNormalizedPackagePathKey() }
            .toSet()
        val embedRoot = projectPriRoot.resolve("embed")
        inputs.forEach { input ->
            if (input.source.name.endsWith(".xaml", ignoreCase = true) && input.target.toXbfTargetKey() in xbfTargets) {
                recordInput(ApplicationPackageItemKind.ExcludedLayout, input.source, input.target, items)
                return@forEach
            }
            if (input.source.name.endsWith(".xbf", ignoreCase = true)) {
                val embedTarget = embedRoot.resolve(input.target.relativeTo(embedRoot.parent))
                copyInput(ApplicationPackageItemKind.Embed, input.source, embedTarget, items)
                return@forEach
            }
            copyInput(ApplicationPackageItemKind.Layout, input.source, input.target, items)
        }
    }

    private fun copyInput(kind: ApplicationPackageItemKind, source: Path, target: Path, items: MutableSet<ApplicationPackageItem>): Boolean {
        val item = applicationPackageItem(kind, source, target)
        if (!items.add(item)) return false
        copyFile(source, target)
        return true
    }

    private fun recordInput(kind: ApplicationPackageItemKind, source: Path, target: Path, items: MutableSet<ApplicationPackageItem>): Boolean =
        items.add(applicationPackageItem(kind, source, target))

    private fun Path.explicitTargetPath(): Path? {
        val configured = targetPaths[toAbsolutePath().normalize().toString()] ?: return null
        return configured.toSafeRelativePath()
    }

    private fun Path.isExcludedFromBuild(): Boolean =
        toAbsolutePath().normalize().toString() in excludedFromBuildPaths

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

    private fun copyFile(source: Path, target: Path) {
        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    private data class ProjectPriFileInput(val source: Path, val relativeTarget: Path)

    private data class ProjectPriLayoutInput(val source: Path, val target: Path)
}

internal fun String.toSafeRelativePath(): Path {
    val normalized = replace('\\', '/').trim('/')
    if (normalized.isBlank()) return Path.of("")
    val path = Path.of(normalized).normalize()
    require(!path.isAbsolute && !path.startsWith("..")) {
        "AppxPriInitialPath must be a relative path inside the PRI input root: $this"
    }
    return path
}

private fun isProjectPriLayoutFile(path: Path): Boolean =
    path.name.endsWith(".xaml", ignoreCase = true) || path.name.endsWith(".xbf", ignoreCase = true)

private fun isProjectPriContentFile(path: Path): Boolean {
    val fileName = path.name
    return listOf(".png", ".bmp", ".jpg", ".dds", ".tif", ".tga", ".gif")
        .any { extension -> fileName.endsWith(extension, ignoreCase = true) }
}
