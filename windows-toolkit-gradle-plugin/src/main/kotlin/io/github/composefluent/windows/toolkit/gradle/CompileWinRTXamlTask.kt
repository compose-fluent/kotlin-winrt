package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import kotlinx.serialization.json.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

/** Both passes use the fork's DOM/harvester; Gradle never interprets XAML syntax. */
@DisableCachingByDefault(because = "The XamlCompiler protocol contains absolute diagnostic and output paths")
abstract class CompileWinRTXamlTask @Inject constructor(
    private val exec: ExecOperations,
    private val fileSystem: FileSystemOperations,
    private val objects: ObjectFactory,
) : DefaultTask() {
    @get:Internal
    abstract val sourceRoots: ConfigurableFileCollection
    // A final pass consumes markup only. Snapshot actual files, not the entire
    // Kotlin roots (which include other targets' generated KSP directories).
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputXamlFiles get() = objects.fileCollection().from(sourceRoots.elements.map { roots ->
        roots.flatMap { root -> root.asFile.takeIf(File::isDirectory)?.walkTopDown()
            ?.filter { it.isFile && it.extension.equals("xaml", true) }?.toList().orEmpty() }
    })
    @get:Input
    val inputXamlRootPaths get() = sourceRoots.files.filter { root -> root.isDirectory &&
        root.walkTopDown().any { it.isFile && it.extension.equals("xaml", true) } }
        .map { it.absolutePath }.sorted()
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val preparedMetadataManifest: RegularFileProperty
    @get:Internal
    abstract val referenceFiles: ConfigurableFileCollection
    @get:Internal
    abstract val windowsSdkFacadeFiles: ConfigurableFileCollection
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerDirectory: DirectoryProperty
    @get:Internal
    abstract val genXbfDirectory: DirectoryProperty
    // Do not store a mapped file collection in task state. Configuration-cache
    // serialization can realize it before the metadata producer updates its manifest.
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    val inputReferenceFiles get() = objects.fileCollection().from(
        preparedMetadataManifest.map { readPreparedMetadataCache(it.asFile.toPath()).files }, referenceFiles)
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    val inputWindowsSdkFacadeFiles get() = objects.fileCollection().from(
        inputReferenceFiles.elements.map { windowsSdkUnionMetadataFiles(it.map { reference -> reference.asFile }) }, windowsSdkFacadeFiles)
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputGenXbfDirectory get() = genXbfDirectory.map { it.asFile }.orElse(inputReferenceFiles.elements.map { references ->
        // Keep the restore producer edge when GenXbf comes from compiler-only
        // package references rather than the projection metadata manifest.
        val winuiReferences = references.map { it.asFile }.filter { it.name.equals("Microsoft.UI.Xaml.winmd", true) }
            .distinctBy { it.toPath().toAbsolutePath().normalize().toString().lowercase() }
        val winui = winuiReferences.singleOrNull()
            ?: error("Kotlin XAML requires one resolved Microsoft.UI.Xaml.winmd reference; found ${winuiReferences.size}: ${winuiReferences.joinToString()}.")
        winui.parentFile.parentFile.resolve("tools").also { tools ->
            require(File(tools, "x64/GenXbf.dll").isFile) {
                "The selected WinUI package has no x64 GenXbf.dll at $tools; configure windows.xaml.genXbfDirectory."
            }
        }
    })
    @get:Input abstract val projectName: Property<String>
    @get:Input abstract val minimumWindowsVersion: Property<String>
    @get:Input @get:Optional abstract val windowsAppSdkVersion: Property<String>
    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE)
    abstract val semanticSymbols: RegularFileProperty
    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE)
    abstract val semanticWinmd: RegularFileProperty

    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE)
    abstract val applicationHeaderWinmd: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:Internal val declarationsFile get() = outputDirectory.file("declarations.json")
    @get:Internal val implementationFile get() = outputDirectory.file("output.json")

    init {
        minimumWindowsVersion.convention("10.0.19041.0")
    }

    @TaskAction fun compile() {
        val output = outputDirectory.get().asFile
        output.mkdirs()
        // Invalid invocations must never leave a consumable previous plan.
        declarationsFile.get().asFile.delete()
        implementationFile.get().asFile.delete()
        fileSystem.delete { it.delete(File(output, "compiled")) }
        File(output, "state.xml").delete()
        val compilerManifest = validateXamlCompilerPackage(compilerDirectory.get().asFile.toPath())
        validateXamlCompilerHost(compilerManifest)
        validateXamlCompilerWinui(compilerManifest, inputReferenceFiles.files,
            inputGenXbfDirectory.get(), windowsAppSdkVersion.orNull)
        val roots = sourceRoots.files.filter { it.isDirectory }.sortedBy { it.absolutePath }
        val sources = linkedMapOf<String, File>()
        roots.forEach { root -> root.walkTopDown().filter { it.isFile && it.extension.equals("xaml", true) }.forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val existing = sources.keys.firstOrNull { it.equals(relative, true) }
            require(existing == null || sources[existing] == file) { "Duplicate XAML resource path: $relative" }
            sources[relative] = file
        } }
        val refs = inputReferenceFiles.files.sortedBy { it.absolutePath }
        GradleFileOperations.writeStringIfChanged(File(output, "references.txt").toPath(),
            refs.map { it.absolutePath }.sorted().joinToString("\n"))
        fun item(file: File, link: String? = null) = buildJsonObject {
            put("ItemSpec", file.absolutePath); put("FullPath", file.absolutePath)
            // Unlike MSBuild's Windows.winmd facade input, prepared metadata contains
            // individual SDK contracts. SortReferenceAssemblies defers system references
            // in pass 1, which would hide Foundation's IReference<T> from the schema.
            // Supply the complete resolved reference set to both compiler passes.
            if (link != null) { put("MSBuild_Link", link); put("MSBuild_TargetPath", link) }
        }
        val finalPass = semanticSymbols.isPresent
        require(finalPass == semanticWinmd.isPresent) { "Final XAML pass requires both semantic symbols and WinMD." }
        require(finalPass || applicationHeaderWinmd.isPresent) {
            "XAML declaration pass requires the Kotlin application header WinMD."
        }
        val input = buildJsonObject {
            put("ProjectPath", File(output, "${projectName.get()}.proj").absolutePath)
            put("ProjectName", projectName.get()); put("RootNamespace", projectName.get().replace('-', '_'))
            put("Language", "Kotlin"); put("LanguageSourceExtension", ".kt"); put("OutputType", "WinExe")
            put("IsPass1", !finalPass); put("OutputPath", File(output, "compiled").absolutePath)
            put("TargetPlatformMinVersion", minimumWindowsVersion.get())
            put("GenXbfPath", inputGenXbfDirectory.get().absolutePath)
            put("SavedStateFile", File(output, "state.xml").absolutePath)
            put("ReferenceAssemblies", JsonArray(refs.map { item(it) }))
            put("ReferenceAssemblyPaths", JsonArray((refs.map { it.parentFile } + inputWindowsSdkFacadeFiles.files.map { it.parentFile } +
                File(System.getenv("WINDIR"), "Microsoft.NET/Framework64/v4.0.30319")).distinct().map { item(it) }))
            put("XamlPages", JsonArray(sources.toSortedMap().map { (path, file) -> item(file, path) }))
            if (finalPass) {
                put("KotlinSymbols", Json.parseToJsonElement(semanticSymbols.get().asFile.readText()))
                put("LocalAssembly", JsonArray(listOf(item(semanticWinmd.get().asFile))))
            } else {
                put("LocalAssembly", JsonArray(listOf(item(applicationHeaderWinmd.get().asFile))))
            }
        }
        val inputFile = File(output, "input.json").apply { writeText(input.toString()) }
        val result = exec.exec { spec ->
            spec.workingDir(output)
            spec.commandLine(File(compilerDirectory.get().asFile, "XamlCompiler.exe").absolutePath,
                inputFile.absolutePath, implementationFile.get().asFile.absolutePath)
            spec.isIgnoreExitValue = true
        }
        val plan = runCatching { WinRTXamlDeclarations.readCompilerOutput(implementationFile.get().asFile.toPath()) }
            .getOrElse { error("XamlCompiler failed (exit ${result.exitValue}): ${it.message}") }
        check(result.exitValue == 0) { "XamlCompiler failed with exit ${result.exitValue}." }
        require(plan.schemaVersion <= compilerManifest.getValue("protocolVersion").jsonPrimitive.int) {
            "The XamlCompiler output exceeds its declared protocol version."
        }
        val advertisedFeatures = compilerManifest["features"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        require(plan.pages.flatMap { it.features }.all { it in advertisedFeatures }) {
            "The XamlCompiler output uses features missing from its package manifest."
        }
        plan.pages.forEach { page ->
            val xaml = requireNotNull(sources[page.resourcePath]) { "Unknown XAML resource ${page.resourcePath}." }
            require(File(xaml.parentFile, "${xaml.nameWithoutExtension}.kt").isFile) {
                "${page.resourcePath} declares ${page.className} but has no same-directory ${xaml.nameWithoutExtension}.kt."
            }
        }
        GradleFileOperations.writeStringIfChanged(declarationsFile.get().asFile.toPath(), WinRTXamlDeclarations.canonicalText(plan))
    }
}
