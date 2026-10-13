package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinUiXamlDeclarations
import io.github.composefluent.winrt.metadata.WinUiXamlDeclarationIndex
import io.github.composefluent.winrt.runtime.XamlFramework
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
import java.nio.file.Files
import javax.inject.Inject

/** Both passes use the fork's DOM/harvester; Gradle never interprets XAML syntax. */
@DisableCachingByDefault(because = "The XamlCompiler protocol contains absolute diagnostic and output paths")
abstract class CompileWinUiXamlTask @Inject constructor(
    private val exec: ExecOperations,
    private val fileSystem: FileSystemOperations,
    private val objects: ObjectFactory,
) : DefaultTask() {
    @get:Input abstract val xamlFramework: Property<XamlFramework>
    @get:Input @get:Optional abstract val windowsSdkVersion: Property<String>
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
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val referenceManifests: ConfigurableFileCollection
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
        preparedMetadataManifest.map { readPreparedMetadataCache(it.asFile.toPath()).files }, referenceFiles,
        referenceManifests.elements.map { files -> files.flatMap { it.asFile.readLines().filter(String::isNotBlank).map(::File) } })
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    val inputWindowsSdkFacadeFiles get() = objects.fileCollection().from(
        inputReferenceFiles.elements.map { windowsSdkUnionMetadataFiles(it.map { reference -> reference.asFile }) }, windowsSdkFacadeFiles)
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    val inputGenXbfDirectory get() = genXbfDirectory.map { it.asFile }.orElse(inputReferenceFiles.elements.map { references ->
        if (xamlFramework.get() == XamlFramework.Uwp) {
            return@map findUwpGenXbfDirectory(references.map { it.asFile }, resolvedWindowsSdkVersion())
        }
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
    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE)
    abstract val applicationItemsFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:LocalState abstract val pageCacheDirectory: DirectoryProperty
    @get:Internal val declarationsFile get() = outputDirectory.file("declarations.json")
    /** XAMLC's type/binding contract, without the IDE/runtime source-content fingerprint. */
    @get:Internal val semanticDeclarationsFile get() = outputDirectory.file("semantic-declarations.json")
    @get:Internal val implementationFile get() = outputDirectory.file("output.json")
    @get:Internal val packageResourcesDirectory get() = xamlFramework.flatMap { framework ->
        outputDirectory.dir(if (framework == XamlFramework.Uwp) "resources" else "compiled")
    }

    init {
        xamlFramework.convention(XamlFramework.WinUi)
        minimumWindowsVersion.convention("10.0.19041.0")
        pageCacheDirectory.convention(project.layout.buildDirectory.dir("intermediates/kotlin-winrt/xaml-pages/$name"))
    }

    internal fun resolvedWindowsSdkVersion(): String? {
        if (xamlFramework.get() != XamlFramework.Uwp) return windowsSdkVersion.orNull
        // CsWinRT's resolved references and TargetPlatformVersion describe one SDK.
        // An explicit metadata input may select it without setting the DSL SDK property.
        val versions = readPreparedMetadataCache(preparedMetadataManifest.get().asFile.toPath())
            .windowsSdkSelections.map { it.version }.distinct()
        require(versions.size <= 1) {
            "UWP XAML requires one resolved Windows SDK version; metadata contains ${versions.joinToString()}."
        }
        return versions.singleOrNull() ?: windowsSdkVersion.orNull
    }

    @TaskAction fun compile() {
        val output = outputDirectory.get().asFile
        output.mkdirs()
        // Invalid invocations must never leave a consumable previous plan.
        declarationsFile.get().asFile.delete()
        semanticDeclarationsFile.get().asFile.delete()
        implementationFile.get().asFile.delete()
        File(output, "state.xml").delete()
        val compilerManifest = validateXamlCompilerPackage(compilerDirectory.get().asFile.toPath())
        validateXamlCompilerHost(compilerManifest)
        validateXamlCompilerFramework(compilerManifest, xamlFramework.get(), inputReferenceFiles.files,
            inputGenXbfDirectory.get(), windowsAppSdkVersion.orNull, resolvedWindowsSdkVersion())
        val roots = sourceRoots.files.filter { it.isDirectory }.sortedBy { it.absolutePath }
        val sources = linkedMapOf<String, File>()
        roots.forEach { root -> root.walkTopDown().filter { it.isFile && it.extension.equals("xaml", true) }.forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val existing = sources.keys.firstOrNull { it.equals(relative, true) }
            require(existing == null || sources[existing] == file) { "Duplicate XAML resource path: $relative" }
            sources[relative] = file
        } }
        val sourceHashes = sources.mapValues { WinUiXamlDeclarations.sourceFingerprint(it.value.readText()) }
        val refs = inputReferenceFiles.files.sortedBy { it.absolutePath }
        GradleFileOperations.writeStringIfChanged(File(output, "references.txt").toPath(),
            refs.map { it.absolutePath }.sorted().joinToString("\n"))
        val sourceItems = xamlCompilerSourceItems(sources,
            applicationItemsFile.orNull?.asFile?.readLines()?.filter(String::isNotBlank)?.map(::File).orEmpty())
        val finalPass = semanticSymbols.isPresent
        require(finalPass == semanticWinmd.isPresent) { "Final XAML pass requires both semantic symbols and WinMD." }
        require(finalPass || applicationHeaderWinmd.isPresent) {
            "XAML declaration pass requires the Kotlin application header WinMD."
        }
        val input = buildJsonObject {
            put("ProjectPath", File(output, "${projectName.get()}.proj").absolutePath)
            put("ProjectName", projectName.get()); put("RootNamespace", projectName.get().replace('-', '_'))
            put("Language", "Kotlin"); put("LanguageSourceExtension", ".kt"); put("OutputType", "WinExe")
            put("XamlFramework", xamlFramework.get().name.lowercase())
            put("IsPass1", !finalPass); put("OutputPath", File(output, "compiled").absolutePath)
            put("TargetPlatformMinVersion", minimumWindowsVersion.get())
            put("GenXbfPath", inputGenXbfDirectory.get().absolutePath)
            put("SavedStateFile", File(output, "state.xml").absolutePath)
            // Supply all split SDK contracts in both passes. Marking them as MSBuild
            // system references hides Foundation's IReference<T> from the pass 1 schema.
            put("ReferenceAssemblies", JsonArray(refs.map { xamlCompilerItem(it) }))
            put("ReferenceAssemblyPaths", JsonArray((refs.map { it.parentFile } + inputWindowsSdkFacadeFiles.files.map { it.parentFile } +
                File(System.getenv("WINDIR"), "Microsoft.NET/Framework64/v4.0.30319")).distinct().map { xamlCompilerItem(it) }))
            sourceItems.forEach { (name, items) -> put(name, items) }
            if (finalPass) {
                put("KotlinSymbols", Json.parseToJsonElement(semanticSymbols.get().asFile.readText()))
                put("LocalAssembly", JsonArray(listOf(xamlCompilerItem(semanticWinmd.get().asFile))))
            } else {
                put("LocalAssembly", JsonArray(listOf(xamlCompilerItem(applicationHeaderWinmd.get().asFile))))
            }
        }
        // IDE snapshot compilation needs the complete input, even when this invocation is partial.
        File(output, "input.json").writeText(input.toString())
        val compiled = File(output, "compiled").toPath().toAbsolutePath().normalize()
        val cache = WinUiXamlPageCache(pageCacheDirectory.get().asFile.toPath())
        val compilerFiles = (compilerDirectory.get().asFile.walkTopDown() + inputGenXbfDirectory.get().walkTopDown())
            .filter { it.isFile && it.extension.lowercase() in setOf("dll", "exe", "config", "json") }.toList()
        val context = WinUiXamlPageCache.context(input, refs + inputWindowsSdkFacadeFiles.files + compilerFiles +
            listOfNotNull(semanticWinmd.orNull?.asFile, applicationHeaderWinmd.orNull?.asFile))
        val keys = sources.mapValues { (path, file) -> cache.key(context, path, file, sourceHashes.getValue(path)) }
        // Application resources affect every page. This must be decided before invoking
        // XAMLC, including when an application is first added to a previously isolated batch.
        val cached = if (sourceItems.getValue("XamlApplications").jsonArray.isNotEmpty()) emptyMap()
            else keys.mapNotNull { (path, key) -> cache.read(key, path)?.let { path to it } }.toMap()
        val symbols = input["KotlinSymbols"]?.jsonObject
        fun invoke(resources: Set<String>): JsonObject {
            val selected = JsonObject(input + buildMap {
                sourceItems.forEach { (name, items) ->
                    put(name, JsonArray(items.jsonArray.filter {
                        it.jsonObject.getValue("MSBuild_Link").jsonPrimitive.content in resources
                    }))
                }
                if (symbols != null) put("KotlinSymbols", WinUiXamlPageCache.symbolsForResources(symbols, resources))
            })
            val invocationFile = File(output, "incremental-input.json").apply { writeText(selected.toString()) }
            implementationFile.get().asFile.delete()
            val result = exec.exec { spec ->
                spec.workingDir(output)
                spec.commandLine(File(compilerDirectory.get().asFile, "XamlCompiler.exe").absolutePath,
                    invocationFile.absolutePath, implementationFile.get().asFile.absolutePath)
                spec.isIgnoreExitValue = true
            }
            val nativePlan = runCatching { WinUiXamlDeclarations.readCompilerOutput(implementationFile.get().asFile.toPath()) }
                .getOrElse { error("XamlCompiler failed (exit ${result.exitValue}): ${it.message}") }
            check(result.exitValue == 0) { "XamlCompiler failed with exit ${result.exitValue}." }
            val nativeOutput = Json.parseToJsonElement(implementationFile.get().asFile.readText()).jsonObject
            if (finalPass) require(nativeOutput.getValue("KotlinImplementation").jsonObject
                .getValue("DeclarationFingerprint").jsonPrimitive.content == WinUiXamlDeclarations.fingerprint(nativePlan)) {
                "XAML source or declarations changed between semantic and final compilation. Rebuild the XAML semantic symbols."
            }
            return nativeOutput
        }
        val changed = sources.keys - cached.keys
        var fresh = if (changed.isNotEmpty() || sources.isEmpty()) invoke(changed) else null
        var reused = cached
        // A newly introduced shared resource must also invalidate otherwise reusable pages.
        if (fresh != null && cached.isNotEmpty() && (!WinUiXamlPageCache.canIsolate(
                WinUiXamlDeclarations.parse(fresh.getValue("KotlinDeclarations").toString())) ||
                (fresh["GeneratedCodeFiles"] as? JsonArray).orEmpty().isNotEmpty())) {
            reused = emptyMap()
            fresh = invoke(sources.keys)
        }
        val compilerOutput = WinUiXamlPageCache.merge(reused.values.map { cache.materialize(it, compiled) } + listOfNotNull(fresh), symbols)
        val plan = WinUiXamlDeclarations.parse(compilerOutput.getValue("KotlinDeclarations").toString())
        require(plan.framework == xamlFramework.get()) { "XamlCompiler returned ${plan.framework} declarations for a ${xamlFramework.get()} compilation." }
        if (symbols != null) {
            require(WinUiXamlDeclarations.canonicalText(plan) == WinUiXamlDeclarations.canonicalText(
                WinUiXamlDeclarations.parse(symbols.getValue("Declarations").toString()))) {
                "XamlCompiler changed the declarations used to compile Kotlin; refusing to publish inconsistent XAML resources."
            }
        }
        require((plan.resources + plan.pages.map { it.resourcePath }).toSet() == sources.keys) {
            "XamlCompiler returned an incomplete resource set."
        }
        logger.info("XAML: compiled {} resources, reused {} resources.", sources.size - reused.size, reused.size)
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
        require(sources.all { (path, file) -> WinUiXamlDeclarations.sourceFingerprint(file.readText()) == sourceHashes.getValue(path) }) {
            "XAML source changed while the compiler was running. Run the compilation again."
        }
        val fingerprinted = plan.copy(pages = plan.pages.map { it.copy(sourceHash = sourceHashes.getValue(it.resourcePath)) })
        val declarations = WinUiXamlDeclarations.canonicalText(fingerprinted)
        if (finalPass) require(compilerOutput.getValue("KotlinImplementation").jsonObject
            .getValue("DeclarationFingerprint").jsonPrimitive.content == WinUiXamlDeclarations.fingerprint(plan)) {
            "XAML source or declarations changed between semantic and final compilation. Rebuild the XAML semantic symbols."
        }
        // XAMLC preserves the Kotlin semantic fingerprint but does not own source hashing.
        // Keep both output artifacts identical, retaining all compiler logs and implementation fields.
        val enriched = JsonObject(compilerOutput + buildMap {
            put("KotlinDeclarations", Json.parseToJsonElement(declarations))
            if (finalPass) put("KotlinImplementation", JsonObject(compilerOutput.getValue("KotlinImplementation").jsonObject +
                ("DeclarationFingerprint" to JsonPrimitive(WinUiXamlDeclarations.fingerprint(fingerprinted)))))
        })
        GradleFileOperations.writeStringIfChanged(implementationFile.get().asFile.toPath(), enriched.toString())
        GradleFileOperations.writeStringIfChanged(declarationsFile.get().asFile.toPath(), declarations)
        GradleFileOperations.writeStringIfChanged(semanticDeclarationsFile.get().asFile.toPath(), WinUiXamlDeclarations.canonicalText(plan))
        // Prune stale artifacts only after the complete native contract has passed validation.
        val generated = listOf("GeneratedXamlFiles", "GeneratedXbfFiles", "GeneratedCodeFiles").flatMap {
            (compilerOutput[it] as? JsonArray).orEmpty().map { path -> File(path.jsonPrimitive.content).toPath().toAbsolutePath().normalize() }
        }.toSet()
        if (Files.isDirectory(compiled)) Files.walk(compiled).use { paths ->
            val obsolete = paths.filter(Files::isRegularFile).filter { it !in generated }.toList()
            fileSystem.delete { it.delete(obsolete) }
        }
        if (WinUiXamlPageCache.canIsolate(plan)) fresh?.let { nativeOutput ->
            val freshPlan = WinUiXamlDeclarations.parse(nativeOutput.getValue("KotlinDeclarations").toString())
            freshPlan.pages.forEach { page -> cache.write(keys.getValue(page.resourcePath), page.resourcePath, nativeOutput, compiled) }
        }
        cache.retain(if (WinUiXamlPageCache.canIsolate(plan)) keys.values.toSet() else emptySet())
        if (finalPass && plan.framework == XamlFramework.Uwp) {
            val resourceRoot = packageResourcesDirectory.get().asFile
            fileSystem.delete { it.delete(resourceRoot) }
            xamlPackageResources(compiled.toFile(), plan).forEach { (source, target) ->
                GradleFileOperations.copyFile(source.toPath(), resourceRoot.resolve(target).toPath())
            }
        }
    }
}

/** System UWP initializes Application from Files/App.xaml, independently of its class name.
 * SDK CppWinRT_AppPass1.InitializeComponent is empty; WinUI 3 explicitly loads the source URI.
 * Keep compiler/IDE source paths intact and normalize only the package resource boundary. */
internal fun xamlPackageResources(compiled: File, plan: WinUiXamlDeclarationIndex): Map<File, String> {
    val application = if (plan.framework == XamlFramework.Uwp) {
        val applications = plan.pages.filter { it.isApplication }
        require(applications.size <= 1) { "A UWP application package can contain only one XAML Application." }
        applications.singleOrNull()?.resourcePath?.substringBeforeLast('.')
    } else null
    val targets = mutableSetOf<String>()
    return compiled.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("xaml", "xbf") }
        .sortedBy { it.relativeTo(compiled).invariantSeparatorsPath }.associateWith { source ->
            val path = source.relativeTo(compiled).invariantSeparatorsPath
            val target = if (application != null && path.substringBeforeLast('.') == application)
                "App.${source.extension.lowercase()}" else path
            require(targets.add(target.lowercase())) { "XAML resources collide at package path $target." }
            target
        }
}

private fun xamlCompilerItem(file: File, link: String? = null) = buildJsonObject {
    put("ItemSpec", file.absolutePath)
    put("FullPath", file.absolutePath)
    if (link != null) {
        put("MSBuild_Link", link)
        put("MSBuild_TargetPath", link)
    }
}

/** XAMLC SourceFileManager uses these two MSBuild item kinds to set IsApplication. */
internal fun xamlCompilerSourceItems(sources: Map<String, File>, applications: Collection<File>): JsonObject {
    fun key(file: File) = file.toPath().toAbsolutePath().normalize().toString().lowercase()
    val applicationPaths = applications.map(::key).toSet()
    require(applicationPaths.all { application -> sources.values.any { key(it) == application } }) {
        "Kotlin XAML application items contain a file outside the compilation's XAML inputs."
    }
    val (applicationItems, pages) = sources.toSortedMap().entries.partition { key(it.value) in applicationPaths }
    return buildJsonObject {
        put("XamlApplications", JsonArray(applicationItems.map { (path, file) -> xamlCompilerItem(file, path) }))
        put("XamlPages", JsonArray(pages.map { (path, file) -> xamlCompilerItem(file, path) }))
    }
}
