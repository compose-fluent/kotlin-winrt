package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

abstract class StageWinRtApplicationPackageTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeAssetsDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val generateProjectPri: Property<Boolean>

    @get:Input
    abstract val projectPriIndexName: Property<String>

    @get:Input
    abstract val projectPriFallbackIndexName: Property<String>

    @get:Input
    abstract val projectPriInitialPath: Property<String>

    @get:Input
    abstract val projectPriDefaultLanguage: Property<String>

    @get:Input
    abstract val projectPriDefaultQualifiers: ListProperty<String>

    @get:Input
    abstract val enableDefaultProjectPriResources: Property<Boolean>

    @get:Input
    abstract val makePriExecutable: Property<String>

    @get:Input
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val runtimeIdentifier: Property<String>

    @get:Input
    abstract val projectPriTargetPaths: MapProperty<String, String>

    @get:Input
    abstract val projectPriExcludedFromBuildPaths: SetProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appxManifestFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriResourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriLayoutFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriContentFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectPriEmbedFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultProjectPriResourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultProjectPriLayoutFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val defaultProjectPriContentFiles: ConfigurableFileCollection

    @get:Internal
    abstract val defaultProjectPriResourceRoot: DirectoryProperty

    init {
        generateProjectPri.convention(true)
        projectPriIndexName.convention("")
        projectPriFallbackIndexName.convention("Application")
        projectPriInitialPath.convention("")
        projectPriDefaultLanguage.convention("")
        projectPriDefaultQualifiers.convention(listOf("scale-200", "contrast-standard"))
        enableDefaultProjectPriResources.convention(true)
        makePriExecutable.convention("")
        windowsSdkVersion.convention("")
        projectPriTargetPaths.convention(emptyMap())
        projectPriExcludedFromBuildPaths.convention(emptySet())
    }

    @TaskAction
    fun stage() {
        val runtimeAssetsRoot = runtimeAssetsDirectory.get().asFile.toPath()
        val outputRoot = outputDirectory.get().asFile.toPath()
        cleanDirectory(outputRoot)
        Files.createDirectories(outputRoot)
        if (runtimeAssetsRoot.isDirectory()) {
            Files.walk(runtimeAssetsRoot).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() }
                    .forEach { source -> copyFile(source, outputRoot.resolve(source.relativeTo(runtimeAssetsRoot))) }
            }
        }
        generateProjectPri(outputRoot)
    }

    private fun generateProjectPri(outputRoot: Path) {
        if (!generateProjectPri.get() || !isWindowsHost()) {
            return
        }
        val inputPris = Files.walk(outputRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".pri", ignoreCase = true) }
                .filterNot { it.name.equals("resources.pri", ignoreCase = true) }
                .filterNot { it.name.startsWith("resources.language-", ignoreCase = true) }
                .sorted()
                .toList()
        }
        val makePri = discoverMakePriExecutable() ?: run {
            logger.warn("Skipping application PRI generation because makepri.exe was not found.")
            return
        }
        val projectPriRoot = temporaryDir.toPath().resolve("project-pri")
        cleanDirectory(projectPriRoot)
        Files.createDirectories(projectPriRoot)
        var hasProjectPriInputs = false
        val copiedProjectPriItems = linkedSetOf<ApplicationPackageItem>()
        inputPris.forEach { source ->
            if (copyProjectPriInput(ApplicationPackageItemKind.ComponentPri, source, projectPriRoot.resolve(source.relativeTo(outputRoot)), copiedProjectPriItems)) {
                hasProjectPriInputs = true
            }
        }
        stageProjectPriResources(projectPriRoot, copiedProjectPriItems).also { copied ->
            hasProjectPriInputs = hasProjectPriInputs || copied
        }
        stageProjectPriLayoutResources(projectPriRoot, copiedProjectPriItems).also { copied ->
            hasProjectPriInputs = hasProjectPriInputs || copied
        }
        stageProjectPriContentResources(projectPriRoot, copiedProjectPriItems).also { copied ->
            hasProjectPriInputs = hasProjectPriInputs || copied
        }
        stageProjectPriEmbedFiles(projectPriRoot, copiedProjectPriItems).also { copied ->
            hasProjectPriInputs = hasProjectPriInputs || copied
        }
        stageDefaultProjectPriResources(projectPriRoot, copiedProjectPriItems).also { copied ->
            hasProjectPriInputs = hasProjectPriInputs || copied
        }
        stageDefaultProjectPriLayoutResources(projectPriRoot, copiedProjectPriItems).also { copied ->
            hasProjectPriInputs = hasProjectPriInputs || copied
        }
        stageDefaultProjectPriContentResources(projectPriRoot, copiedProjectPriItems).also { copied ->
            hasProjectPriInputs = hasProjectPriInputs || copied
        }
        if (!hasProjectPriInputs) {
            return
        }
        val config = temporaryDir.toPath().resolve("project-pri-config").resolve("priconfig.xml")
        Files.createDirectories(config.parent)
        val output = projectPriRoot.resolve("resources.pri")
        runMakePri(
            makePri,
            listOf("createconfig", "/cf", config.toString(), "/dq", projectPriDefaultQualifier(), "/pv", "10.0.0", "/o"),
            outputRoot,
            "create application PRI config",
        ) ?: return
        runMakePri(
            makePri,
            listOf("new", "/pr", projectPriRoot.toString(), "/cf", config.toString(), "/of", output.toString(), "/in", projectPriIndexName(), "/o"),
            outputRoot,
            "generate application PRI",
        ) ?: return
        Files.walk(projectPriRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() }
                .filter {
                    it.name.equals("resources.pri", ignoreCase = true) ||
                        it.name.startsWith("resources.language-", ignoreCase = true)
                }
                .forEach { source -> copyFile(source, outputRoot.resolve(source.name)) }
        }
        Files.deleteIfExists(config)
    }

    private fun stageProjectPriResources(projectPriRoot: Path, copiedItems: MutableSet<ApplicationPackageItem>): Boolean {
        val initialPath = projectPriInitialPath.get().toSafeRelativePath()
        val root = defaultProjectPriResourceRoot.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
        var copied = false
        projectPriResourceFiles.files.asSequence()
            .map { it.toPath() }
            .filter { Files.exists(it) }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isDirectory() && !source.isProjectPriExcludedFromBuild()) {
                    Files.walk(source).use { stream ->
                        stream.asSequence()
                            .filter { it.isRegularFile() }
                            .filterNot { it.isProjectPriExcludedFromBuild() }
                            .sorted()
                            .forEach { child ->
                                val target = projectPriRoot.resolve(initialPath).resolve(child.toProjectPriRelativePath(root, source))
                                if (copyProjectPriInput(ApplicationPackageItemKind.PriResource, child, target, copiedItems)) copied = true
                            }
                    }
                } else if (source.isRegularFile() && !source.isProjectPriExcludedFromBuild()) {
                    val normalizedSource = source.toAbsolutePath().normalize()
                    val relativeTarget = source.explicitProjectPriTargetPath()
                        ?: if (root != null && normalizedSource.startsWith(root)) normalizedSource.relativeTo(root) else Path.of(source.name)
                    if (copyProjectPriInput(ApplicationPackageItemKind.PriResource, source, projectPriRoot.resolve(initialPath).resolve(relativeTarget), copiedItems)) copied = true
                }
            }
        return copied
    }

    private fun stageProjectPriLayoutResources(projectPriRoot: Path, copiedItems: MutableSet<ApplicationPackageItem>): Boolean {
        val initialPath = projectPriInitialPath.get().toSafeRelativePath()
        val root = defaultProjectPriResourceRoot.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
        val inputs = projectPriLayoutFiles.files.asSequence()
            .map { it.toPath() }
            .filter { Files.exists(it) }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .flatMap { source ->
                if (source.isDirectory() && !source.isProjectPriExcludedFromBuild()) {
                    Files.walk(source).use { stream ->
                        stream.asSequence()
                            .filter { it.isRegularFile() && it.isProjectPriLayoutFile() }
                            .filterNot { it.isProjectPriExcludedFromBuild() }
                            .sorted()
                            .map { child -> ProjectPriLayoutInput(child, projectPriRoot.resolve(initialPath).resolve(child.toProjectPriRelativePath(root, source))) }
                            .toList()
                            .asSequence()
                    }
                } else if (source.isRegularFile() && source.isProjectPriLayoutFile() && !source.isProjectPriExcludedFromBuild()) {
                    val normalizedSource = source.toAbsolutePath().normalize()
                    val relativeTarget = source.explicitProjectPriTargetPath()
                        ?: if (root != null && normalizedSource.startsWith(root)) normalizedSource.relativeTo(root) else Path.of(source.name)
                    sequenceOf(ProjectPriLayoutInput(source, projectPriRoot.resolve(initialPath).resolve(relativeTarget)))
                } else {
                    emptySequence()
                }
            }
            .toList()
        return stageFilteredProjectPriLayoutInputs(inputs, copiedItems)
    }

    private fun stageDefaultProjectPriResources(projectPriRoot: Path, copiedItems: MutableSet<ApplicationPackageItem>): Boolean {
        if (!enableDefaultProjectPriResources.get()) return false
        val root = defaultProjectPriResourceRoot.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize() ?: return false
        val initialPath = projectPriInitialPath.get().toSafeRelativePath()
        var copied = false
        defaultProjectPriResourceFiles.files.asSequence()
            .map { it.toPath() }
            .filter { it.isRegularFile() && it.name.endsWith(".resw", ignoreCase = true) }
            .filterNot { it.isProjectPriExcludedFromBuild() }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                val normalizedSource = source.toAbsolutePath().normalize()
                if (normalizedSource.startsWith(root) && copyProjectPriInput(ApplicationPackageItemKind.PriResource, source, projectPriRoot.resolve(initialPath).resolve(normalizedSource.relativeTo(root)), copiedItems)) {
                    copied = true
                }
            }
        return copied
    }

    private fun stageProjectPriContentResources(projectPriRoot: Path, copiedItems: MutableSet<ApplicationPackageItem>): Boolean {
        val initialPath = projectPriInitialPath.get().toSafeRelativePath()
        val root = defaultProjectPriResourceRoot.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
        var copied = false
        projectPriContentFiles.files.asSequence()
            .map { it.toPath() }
            .filter { Files.exists(it) }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isDirectory() && !source.isProjectPriExcludedFromBuild()) {
                    Files.walk(source).use { stream ->
                        stream.asSequence()
                            .filter { it.isRegularFile() }
                            .filterNot { it.isProjectPriExcludedFromBuild() }
                            .sorted()
                            .forEach { child ->
                                val target = projectPriRoot.resolve(initialPath).resolve(child.toProjectPriRelativePath(root, source))
                                if (copyProjectPriInput(ApplicationPackageItemKind.Content, child, target, copiedItems)) copied = true
                            }
                    }
                } else if (source.isRegularFile() && !source.isProjectPriExcludedFromBuild()) {
                    val normalizedSource = source.toAbsolutePath().normalize()
                    val relativeTarget = source.explicitProjectPriTargetPath()
                        ?: if (root != null && normalizedSource.startsWith(root)) normalizedSource.relativeTo(root) else Path.of(source.name)
                    if (copyProjectPriInput(ApplicationPackageItemKind.Content, source, projectPriRoot.resolve(initialPath).resolve(relativeTarget), copiedItems)) copied = true
                }
            }
        return copied
    }

    private fun stageProjectPriEmbedFiles(projectPriRoot: Path, copiedItems: MutableSet<ApplicationPackageItem>): Boolean {
        val initialPath = projectPriInitialPath.get().toSafeRelativePath()
        val root = defaultProjectPriResourceRoot.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize()
        var copied = false
        projectPriEmbedFiles.files.asSequence()
            .map { it.toPath() }
            .filter { Files.exists(it) }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                if (source.isDirectory() && !source.isProjectPriExcludedFromBuild()) {
                    Files.walk(source).use { stream ->
                        stream.asSequence()
                            .filter { it.isRegularFile() }
                            .filterNot { it.isProjectPriExcludedFromBuild() }
                            .sorted()
                            .forEach { child ->
                                val target = projectPriRoot.resolve(initialPath).resolve(child.toProjectPriRelativePath(root, source))
                                if (copyProjectPriInput(ApplicationPackageItemKind.Embed, child, target, copiedItems)) copied = true
                            }
                    }
                } else if (source.isRegularFile() && !source.isProjectPriExcludedFromBuild()) {
                    val normalizedSource = source.toAbsolutePath().normalize()
                    val relativeTarget = source.explicitProjectPriTargetPath()
                        ?: if (root != null && normalizedSource.startsWith(root)) normalizedSource.relativeTo(root) else Path.of(source.name)
                    if (copyProjectPriInput(ApplicationPackageItemKind.Embed, source, projectPriRoot.resolve(initialPath).resolve(relativeTarget), copiedItems)) copied = true
                }
            }
        return copied
    }

    private fun stageDefaultProjectPriLayoutResources(projectPriRoot: Path, copiedItems: MutableSet<ApplicationPackageItem>): Boolean {
        if (!enableDefaultProjectPriResources.get()) return false
        val root = defaultProjectPriResourceRoot.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize() ?: return false
        val initialPath = projectPriInitialPath.get().toSafeRelativePath()
        val inputs = defaultProjectPriLayoutFiles.files.asSequence()
            .map { it.toPath() }
            .filter { it.isRegularFile() && it.isProjectPriLayoutFile() }
            .filterNot { it.isProjectPriExcludedFromBuild() }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .mapNotNull { source ->
                val normalizedSource = source.toAbsolutePath().normalize()
                if (normalizedSource.startsWith(root)) ProjectPriLayoutInput(source, projectPriRoot.resolve(initialPath).resolve(normalizedSource.relativeTo(root))) else null
            }
            .toList()
        return stageFilteredProjectPriLayoutInputs(inputs, copiedItems)
    }

    private fun stageDefaultProjectPriContentResources(projectPriRoot: Path, copiedItems: MutableSet<ApplicationPackageItem>): Boolean {
        if (!enableDefaultProjectPriResources.get()) return false
        val root = defaultProjectPriResourceRoot.orNull?.asFile?.toPath()?.toAbsolutePath()?.normalize() ?: return false
        val initialPath = projectPriInitialPath.get().toSafeRelativePath()
        var copied = false
        defaultProjectPriContentFiles.files.asSequence()
            .map { it.toPath() }
            .filter { it.isRegularFile() && it.isProjectPriContentFile() }
            .filterNot { it.isProjectPriExcludedFromBuild() }
            .sortedBy { it.toAbsolutePath().normalize().toString().lowercase() }
            .forEach { source ->
                val normalizedSource = source.toAbsolutePath().normalize()
                if (normalizedSource.startsWith(root) && copyProjectPriInput(ApplicationPackageItemKind.Content, source, projectPriRoot.resolve(initialPath).resolve(normalizedSource.relativeTo(root)), copiedItems)) {
                    copied = true
                }
            }
        return copied
    }

    private fun stageFilteredProjectPriLayoutInputs(inputs: List<ProjectPriLayoutInput>, copiedItems: MutableSet<ApplicationPackageItem>): Boolean {
        val xbfTargets = inputs.asSequence()
            .filter { it.source.name.endsWith(".xbf", ignoreCase = true) }
            .map { it.target.toNormalizedPathKey() }
            .toSet()
        var copied = false
        inputs.forEach { input ->
            if (input.source.name.endsWith(".xaml", ignoreCase = true) && input.target.toXbfTargetKey() in xbfTargets) return@forEach
            if (copyProjectPriInput(ApplicationPackageItemKind.Layout, input.source, input.target, copiedItems)) copied = true
        }
        return copied
    }

    private fun copyProjectPriInput(
        kind: ApplicationPackageItemKind,
        source: Path,
        target: Path,
        copiedItems: MutableSet<ApplicationPackageItem>,
    ): Boolean {
        val item = ApplicationPackageItem(
            kind = kind,
            source = source.toAbsolutePath().normalize(),
            target = target.toAbsolutePath().normalize(),
            targetKey = target.toNormalizedPathKey(),
        )
        if (!copiedItems.add(item)) return false
        copyFile(source, target)
        return true
    }

    private fun String.toSafeRelativePath(): Path {
        val normalized = replace('\\', '/').trim('/')
        if (normalized.isBlank()) return Path.of("")
        val path = Path.of(normalized).normalize()
        require(!path.isAbsolute && !path.startsWith("..")) {
            "AppxPriInitialPath must be a relative path inside the PRI input root: $this"
        }
        return path
    }

    private fun Path.explicitProjectPriTargetPath(): Path? {
        val configured = projectPriTargetPaths.get()[toAbsolutePath().normalize().toString()] ?: return null
        return configured.toSafeRelativePath()
    }

    private fun Path.isProjectPriExcludedFromBuild(): Boolean =
        toAbsolutePath().normalize().toString() in projectPriExcludedFromBuildPaths.get()

    private fun Path.isProjectPriLayoutFile(): Boolean =
        name.endsWith(".xaml", ignoreCase = true) || name.endsWith(".xbf", ignoreCase = true)

    private fun Path.isProjectPriContentFile(): Boolean {
        val fileName = name
        return listOf(".png", ".bmp", ".jpg", ".dds", ".tif", ".tga", ".gif")
            .any { extension -> fileName.endsWith(extension, ignoreCase = true) }
    }

    private fun Path.toNormalizedPathKey(): String =
        toAbsolutePath().normalize().toString().lowercase()

    private fun Path.toXbfTargetKey(): String {
        val xbfFileName = fileName.toString().replaceAfterLast('.', "xbf")
        return parent.resolve(xbfFileName).toNormalizedPathKey()
    }

    private fun Path.toProjectPriRelativePath(projectRoot: Path?, fallbackRoot: Path): Path {
        val normalizedSource = toAbsolutePath().normalize()
        return if (projectRoot != null && normalizedSource.startsWith(projectRoot)) normalizedSource.relativeTo(projectRoot) else relativeTo(fallbackRoot)
    }

    private data class ProjectPriLayoutInput(val source: Path, val target: Path)

    private data class ApplicationPackageItem(
        val kind: ApplicationPackageItemKind,
        val source: Path,
        val target: Path,
        val targetKey: String,
    ) {
        override fun equals(other: Any?): Boolean =
            other is ApplicationPackageItem && targetKey == other.targetKey

        override fun hashCode(): Int =
            targetKey.hashCode()
    }

    private enum class ApplicationPackageItemKind {
        ComponentPri,
        PriResource,
        Layout,
        Content,
        Embed,
    }

    private fun projectPriDefaultQualifier(): String {
        val language = projectPriDefaultLanguage.get().ifBlank {
            appxManifestFiles.files.asSequence()
                .mapNotNull { file -> readManifestDefaultLanguage(file.toPath()) }
                .firstOrNull()
                ?: "en-US"
        }
        return (listOf("lang-$language") + projectPriDefaultQualifiers.get().filter(String::isNotBlank)).joinToString("_")
    }

    private fun readManifestDefaultLanguage(manifest: Path): String? {
        if (!manifest.isRegularFile()) return null
        val document = readXmlDocument(manifest) ?: return null
        val resources = document.getElementsByTagNameNS("*", "Resource")
        return (0 until resources.length).asSequence()
            .mapNotNull { index -> resources.item(index)?.attributes?.getNamedItem("Language")?.nodeValue?.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals("x-generate", ignoreCase = true) }
    }

    private fun projectPriIndexName(): String =
        projectPriIndexName.get().ifBlank {
            appxManifestFiles.files.asSequence()
                .mapNotNull { file -> readManifestIdentityName(file.toPath()) }
                .firstOrNull()
                ?: projectPriFallbackIndexName.get().ifBlank { "Application" }
        }

    private fun readManifestIdentityName(manifest: Path): String? {
        if (!manifest.isRegularFile()) return null
        val document = readXmlDocument(manifest) ?: return null
        val identities = document.getElementsByTagNameNS("*", "Identity")
        return (0 until identities.length).asSequence()
            .mapNotNull { index -> identities.item(index)?.attributes?.getNamedItem("Name")?.nodeValue?.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun readXmlDocument(path: Path) = runCatching {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        }.newDocumentBuilder().parse(path.toFile())
    }.getOrNull()

    private fun discoverMakePriExecutable(): Path? {
        makePriExecutable.get().takeIf { it.isNotBlank() }?.let { configured ->
            return Path.of(configured).takeIf { it.isRegularFile() }
        }
        val sdk = findWindowsSdk(windowsSdkVersion.get().takeIf { it.isNotBlank() }) ?: return null
        return sdk.tool("makepri.exe", windowsSdkArchitecture(runtimeIdentifier.get()))
    }

    private fun runMakePri(makePri: Path, arguments: List<String>, workingDirectory: Path, description: String): String? {
        val process = ProcessBuilder(listOf(makePri.toString()) + arguments)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = decodeProcessOutput(process.inputStream.readBytes())
        val exitCode = process.waitFor()
        return if (exitCode == 0) {
            output
        } else {
            logger.warn("Skipping application PRI generation after makepri failed to $description with exit code $exitCode:\n$output")
            null
        }
    }

    private fun decodeProcessOutput(bytes: ByteArray): String {
        if (bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte()) {
            return bytes.toString(Charsets.UTF_16LE)
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun copyFile(source: Path, target: Path) {
        Files.createDirectories(target.parent)
        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    private fun cleanDirectory(directory: Path) {
        if (!directory.isDirectory()) return
        Files.walk(directory).use { stream ->
            stream.sorted(Comparator.reverseOrder())
                .filter { it != directory }
                .forEach(Files::deleteIfExists)
        }
    }
}
