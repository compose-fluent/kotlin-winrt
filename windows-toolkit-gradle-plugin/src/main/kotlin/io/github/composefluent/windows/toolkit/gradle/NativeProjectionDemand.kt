package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.projections.generator.NativeProjectionRecipe
import io.github.composefluent.winrt.projections.generator.NativeInstanceMemberDemand
import io.github.composefluent.winrt.metadata.staticFactoryInterfacesWithoutProjectedUses
import io.github.composefluent.winrt.projections.generator.redirectedWinUIXamlProjectionSurfaceTypeReferences
import kotlinx.serialization.json.*
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.*
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject

internal const val NATIVE_BUSINESS_PROJECTION_ROOT = "generated/kotlin-winrt/native-business"

internal fun compileUsesSdkOverlaySources(project: Project, taskName: String): Boolean =
    project.plugins.hasPlugin("winrt.prebuilt-projection") &&
        !taskName.startsWith("compileWinRTProjection") && !taskName.startsWith("compileKotlinWinRTProjection")

internal data class NativeSemanticDemand(val types: Set<String>, val staticMembers: Set<String>,
    val instanceMembers: Set<String>, val exposedTypes: Set<String>, val constructionTypes: Set<String>)

internal fun readNativeSemanticDemand(lines: List<String>): NativeSemanticDemand {
    require(lines.firstOrNull() == "kotlin-winrt-native-uses-v4") {
        "Unsupported Native usage manifest. Rebuild its producer with the current compiler plugin."
    }
    val types = linkedSetOf<String>()
    val members = linkedSetOf<String>()
    val instances = linkedSetOf<String>()
    val exposed = linkedSetOf<String>()
    val constructed = linkedSetOf<String>()
    lines.drop(1).filter(String::isNotBlank).forEach { line ->
        val fields = line.split('\t')
        require(fields.all(String::isNotBlank) &&
            (fields[0] in setOf("T", "E", "C") && fields.size == 2 || fields[0] in setOf("M", "I") && fields.size == 3)) {
            "Invalid Native semantic demand row: $line"
        }
        // A callable reference can be the only use of its declaring type.
        types += fields[1]
        if (fields[0] == "M") members += "${fields[1]}\t${fields[2]}"
        if (fields[0] == "I") instances += "${fields[1]}\t${fields[2]}"
        if (fields[0] == "E") exposed += fields[1]
        if (fields[0] == "C") constructed += fields[1]
    }
    return NativeSemanticDemand(types, members, instances, exposed, constructed)
}

/** Implementation owners must see the same sliced interface surface as their dependencies.
 * Full reference contracts remain the inputs to business compilation only. */
internal fun nativeProjectionOwnerOrder(owners: List<String>, dependencies: Map<String, List<String>>): List<String> {
    val available = owners.toSet()
    val visited = mutableSetOf<String>()
    val visiting = mutableSetOf<String>()
    val ordered = mutableListOf<String>()
    fun visit(owner: String) {
        if (owner in visited) return
        check(visiting.add(owner)) { "Cyclic Native projection owner dependency: $owner" }
        dependencies[owner].orEmpty().sorted().forEach(::visit)
        visiting.remove(owner)
        visited += owner
        if (owner in available) ordered += owner
    }
    owners.sorted().forEach(::visit)
    return ordered
}

@CacheableTask
abstract class GenerateWinRTSdkOverlaysTask : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val recipeDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun generate() {
        NativeProjectionRecipe.read(recipeDirectory.get().asFile.toPath())
            .generateBusinessOverlays(outputDirectory.get().asFile.toPath())
    }
}

@CacheableTask
abstract class PackWinRTNativeUsesTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val compilationOutputs: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val semanticUses: ConfigurableFileCollection
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction fun pack() {
        val uses = semanticUses.files.singleOrNull { it.isFile }
        check(uses != null || compilationOutputs.files.none { it.exists() }) {
            "Native business KLIB is missing its semantic usage manifest; rebuild its producer."
        }
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(uses?.readText() ?: "kotlin-winrt-native-uses-v4\n")
    }
}

@CacheableTask
abstract class PackWinRTNativeRecipeTask : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val recipeDirectory: DirectoryProperty
    @get:Input abstract val moduleName: Property<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction fun pack() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            val entries = listOf("recipe.json", "model.json").associateWith {
                recipeDirectory.file(it).get().asFile.readBytes()
            } + ("module-name.txt" to moduleName.get().toByteArray())
            entries.toSortedMap().forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name).apply { time = 0 })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}

/** Discovery runs after business compilation and before implementation generation. No
 * implementation KLIB is an input, so neither a DLL nor the EXE roots the full SDK. */
@CacheableTask
abstract class BuildWinRTNativeDemandTask : DefaultTask() {
    @get:Inject protected abstract val executionStartParameter: org.gradle.StartParameter
    // A cached configuration must not freeze a previous invocation's --build-cache/--rerun-tasks.
    @get:Internal val persistentCompilationReuseAllowed: Boolean
        get() = executionStartParameter.isBuildCacheEnabled && !executionStartParameter.isRerunTasks
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val demandArtifacts: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val localUses: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val referenceLibraries: ConfigurableFileCollection
    @get:Classpath abstract val compilerClasspath: ConfigurableFileCollection
    @get:Classpath abstract val pluginClasspath: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerDistributionFiles: ConfigurableFileCollection
    @get:Nested abstract val javaLauncher: Property<JavaLauncher>
    @get:Input abstract val compilerJvmArgs: ListProperty<String>
    @get:Internal abstract val compilationReuse: Property<NativeProjectionCompilationService>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:Inject protected abstract val execOperations: ExecOperations

    @TaskAction fun build() {
        val root = temporaryDir.resolve("recipes").apply { mkdirs() }
        val recipes = demandArtifacts.files.filter { it.extension == "winrt-recipe" }.sortedBy { it.name }.mapIndexed { i, artifact ->
            val directory = root.resolve(i.toString()).apply { mkdirs() }
            ZipFile(artifact).use { zip ->
                listOf("recipe.json", "model.json", "module-name.txt").forEach { name ->
                    val entry = requireNotNull(zip.getEntry(name)) { "Incomplete Native recipe $artifact: $name" }
                    directory.resolve(name).writeBytes(zip.getInputStream(entry).use { it.readBytes() })
                }
            }
            directory.resolve("module-name.txt").readText() to NativeProjectionRecipe.read(directory.toPath())
        }.sortedBy { it.first }
        check(recipes.map { it.first }.distinct().size == recipes.size) { "Duplicate Native projection module owners" }
        val manifestsWithUses = (localUses.files + demandArtifacts.files.filter { it.name.endsWith(".winrt-uses") }).map { file ->
            check(file.isFile) { "Missing semantic Native usage manifest: $file. Rebuild its producer with the current compiler plugin." }
            try { readNativeSemanticDemand(file.readLines()) }
            catch (error: IllegalArgumentException) { throw IllegalArgumentException("$file: ${error.message}", error) }
        }
        val uses = manifestsWithUses.flatMap { it.types }.toSortedSet()
        val members = manifestsWithUses.flatMap { it.staticMembers }.toSortedSet()
        val instances = manifestsWithUses.flatMap { it.instanceMembers }.toSortedSet()
        val exposed = manifestsWithUses.flatMap { it.exposedTypes }.toSortedSet()
        val closure = NativeProjectionRecipe.closure(recipes.map { it.second }, uses)
        val constructed = manifestsWithUses.flatMap { it.constructionTypes }.toSortedSet()
        val abiOnlyInterfaces = closure.staticFactoryInterfacesWithoutProjectedUses(uses,
            ::redirectedWinUIXamlProjectionSurfaceTypeReferences)
        val instanceDemand = NativeInstanceMemberDemand.create(closure, instances, exposed, constructed, members, abiOnlyInterfaces)
        val names = closure.namespaces.flatMap { it.types }.map { it.qualifiedName }.toSet()
        val output = outputDirectory.get().asFile.apply { mkdirs() }
        val sourceRoot = output.resolve("sources").toPath()
        GradleFileOperations.deleteDirectory(sourceRoot)
        val retained = mutableSetOf<String>()
        val launcher = javaLauncher.get()
        val toolFiles = (compilerClasspath.files + pluginClasspath.files + compilerDistributionFiles.files +
            listOf(launcher.executablePath.asFile, launcher.metadata.installationPath.file("release").asFile)).distinct()
        val toolDigests = nativeCompilationToolDigests(toolFiles)
        val tool = NativeKlibContractReader(execOperations, launcher, compilerClasspath)
        val libraryManifests = referenceLibraries.files.filter { it.isDirectory || (it.isFile && it.extension == "klib") }
            .associateWith(tool::manifest)
        val references = libraryManifests.keys.associateBy { libraryManifests.getValue(it).getProperty("unique_name") }
        val manifests = references.mapValues { libraryManifests.getValue(it.value) }
        fun ownerDependencies(module: String): List<File> {
            check(module in manifests) { "Missing reference contract for Native projection owner $module" }
            val visited = mutableSetOf(module)
            val selected = linkedSetOf<File>()
            fun visit(name: String) {
                manifests[name]?.getProperty("depends").orEmpty().split(' ').filter(String::isNotBlank).forEach { dependency ->
                    if (visited.add(dependency)) {
                        references[dependency]?.let { selected += it; visit(dependency) }
                    }
                }
            }
            visit(module)
            return selected.toList()
        }
        val report = mutableListOf("roots=${uses.size}", "closure=${names.size}", "static-member-roots=${members.size}",
            "abi-only-static-interfaces=${abiOnlyInterfaces.size}", "instance-member-roots=${instances.size}",
            "exposed-type-roots=${exposed.size}", "omitted-instance-owner-members=${instanceDemand.omittedMembers.values.sumOf { it.size }}")
        val recipeIndices = recipes.mapIndexed { index, recipe -> recipe.first to index }.toMap()
        val compiledOwners = mutableMapOf<String, File>()
        val dependencies = manifests.mapValues { (_, manifest) ->
            manifest.getProperty("depends").orEmpty().split(' ').filter(String::isNotBlank)
        }
        nativeProjectionOwnerOrder(recipes.map { it.first }, dependencies).forEach { module ->
            val i = recipeIndices.getValue(module)
            val recipe = recipes[i].second
            val selected = names.intersect(recipe.ownedTypes)
            report += "$module: ${selected.size}/${recipe.ownedTypes.size} types"
            logger.lifecycle("Native demand implementation {}: {}/{} types", module, selected.size, recipe.ownedTypes.size)
            output.resolve("demand-report.txt").writeText(report.joinToString("\n", postfix = "\n"))
            val source = sourceRoot.resolve(i.toString())
            if (selected.isEmpty()) {
                // Keep the dependency's unique_name resolvable even when its implementation
                // is empty: business KLIB manifests still list their compile-time modules.
                GradleFileOperations.deleteDirectory(source)
                source.toFile().mkdirs()
                source.resolve("Empty.kt").toFile().writeText("package io.github.composefluent.winrt.projections.empty\n")
            } else recipe.generate(closure, source, members, abiOnlyInterfaces, instanceDemand)
            val library = output.resolve("projection-$i.klib")
            retained += library.name
            val sources = source.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }
                .filter { selected.isEmpty() || isGeneratedWinRTProjectionSource(it) }
                .sortedBy { it.invariantSeparatorsPath }.toList()
            val implementationDependencies = ownerDependencies(module).map { reference ->
                val dependencyOwner = libraryManifests.getValue(reference).getProperty("unique_name")
                if (dependencyOwner in recipeIndices) compiledOwners.getValue(dependencyOwner) else reference
            }
            val support = source.resolve("kotlin-winrt-support/compiler-support.tsv")
            val arguments = buildList {
                addAll(listOf("-produce", "library", "-target", "mingw_x64", "-module-name", module,
                    "-o", library.absolutePath, "-Xpartial-linkage-loglevel=error",
                    "-Xklib-relative-path-base=${source.toAbsolutePath()}"))
                // Projection declarations are shared sources, just as in the producer's
                // KMP compilation (including optional JVM annotations on value classes).
                add("-Xmulti-platform")
                add("-Xcommon-sources=${sources.joinToString(",") { it.absolutePath }}")
                // Match the owner's dependency direction. Feeding higher SDK/business
                // headers here would introduce a circular dependency on this very module.
                implementationDependencies.forEach { dependency ->
                    addAll(listOf("-library", dependency.absolutePath))
                }
                add("-Xplugin=${pluginClasspath.files.joinToString(",") { it.absolutePath }}")
                (if (selected.isEmpty()) emptyList() else listOf("compilerSupportManifest=$support", "projectionSupportMode=embedded",
                    "projectionSupportOwnerArtifactName=${recipe.owner}")).forEach {
                    addAll(listOf("-P", "plugin:io.github.composefluent.winrt.compiler:$it"))
                }
                sources.forEach { add(it.absolutePath) }
            }
            val response = temporaryDir.resolve("compile-$i.args")
            response.writeText(arguments.joinToString("\n") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" })
            val key = nativeCompilationKey(source.toFile(), sources,
                if (selected.isEmpty()) emptyList() else listOf(support.toFile()), library,
                implementationDependencies, toolFiles,
                "${launcher.executablePath}:${launcher.metadata.javaRuntimeVersion}", compilerJvmArgs.get(), arguments, toolDigests)
            val reused = compilationReuse.get().copyOrCompile(key, library, module, persistentCompilationReuseAllowed) {
                execOperations.javaexec { spec ->
                    spec.executable = launcher.executablePath.asFile.absolutePath
                    spec.classpath = compilerClasspath
                    spec.mainClass.set("org.jetbrains.kotlin.cli.utilities.MainKt")
                    spec.jvmArgs(compilerJvmArgs.get())
                    spec.args("konanc", "@${response.absolutePath}")
                }.assertNormalExitValue()
                check(tool.manifest(library).getProperty("unique_name") == module) { "Native implementation owner changed: $module" }
            }
            if (reused) logger.lifecycle("Reusing identical Native implementation {}", module)
            check(tool.manifest(library).getProperty("unique_name") == module) { "Native implementation owner changed: $module" }
            compiledOwners[module] = library
        }
        output.listFiles().orEmpty().filter { it.extension == "klib" && it.name !in retained }.forEach { it.delete() }
        output.resolve("demand-report.txt").writeText(report.joinToString("\n", postfix = "\n"))
    }
}

internal fun nativeDemandConfiguration(project: Project, compilation: KotlinNativeCompilation): Configuration {
    val compile = project.configurations.getByName(compilation.compileDependencyConfigurationName)
    return project.configurations.maybeCreate("${compilation.target.name}${compilation.name.replaceFirstChar(Char::uppercaseChar)}WinRTLink").apply {
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom(compile)
        compile.attributes.keySet().forEach { key ->
            @Suppress("UNCHECKED_CAST") val typed = key as org.gradle.api.attributes.Attribute<Any>
            attributes.attribute(typed, compile.attributes.getAttribute(typed)!!)
        }
        attributes.attribute(WINRT_NATIVE_PROJECTION_ARTIFACT, "demand")
    }
}

internal fun configureNativeDemandVariant(
    project: Project,
    target: KotlinNativeTarget,
    reference: KotlinNativeCompilation,
    hasProjection: Boolean,
) {
    val api = project.configurations.getByName(target.apiElementsConfigurationName)
    val business = target.compilations.getByName("main")
    // KGP's packed business artifact can have a different path from the compilation
    // output. Use the same ownership test as the reference variant, not path equality
    // with the business output (which silently drops SDK COM overlays).
    val projectionFile = if (hasProjection) nativeProjectionReferenceArtifact(project, reference) else null
    val hasBusiness = !hasProjection || api.outgoing.artifacts.any { it.file != projectionFile?.get()?.asFile }
    val usageFileName = "${target.name}.winrt-uses"
    val usageOutput = project.layout.buildDirectory.file("kotlin-winrt/native-demand-uses/$usageFileName")
    val usage = if (hasBusiness) project.tasks.register("${target.name}WinRTNativeUses", PackWinRTNativeUsesTask::class.java) {
        it.semanticUses.from(project.files(project.layout.buildDirectory.file(
            "kotlin-winrt/native-authoring/${business.compileTaskProvider.name}/kotlin-winrt/native-projection-uses.tsv")).builtBy(business.compileTaskProvider))
        it.compilationOutputs.from(business.compileTaskProvider.flatMap { compile -> compile.outputFile })
        it.outputFile.set(usageOutput)
    } else null
    val recipe = if (hasProjection) project.tasks.register("${target.name}WinRTNativeRecipe", PackWinRTNativeRecipeTask::class.java) {
        it.recipeDirectory.set(project.layout.buildDirectory.dir("generated/kotlin-winrt/reference/src/kotlin-winrt-native-recipe"))
        it.dependsOn("generateWinRTReferenceProjections")
        it.moduleName.set(reference.compileTaskProvider.get().compilerOptions.moduleName.get())
        it.outputFile.set(project.layout.buildDirectory.file("libs/${target.name}.winrt-recipe"))
    } else null
    project.configurations.create("${target.name}WinRTDemandElements") { demand ->
        demand.isCanBeResolved = false
        demand.isCanBeConsumed = true
        demand.extendsFrom(*api.extendsFrom.toTypedArray())
        demand.withDependencies { dependencies -> api.dependencies.forEach { dependencies.add(it.copy()) } }
        api.dependencyConstraints.forEach { demand.dependencyConstraints.add(it) }
        api.attributes.keySet().forEach { key ->
            @Suppress("UNCHECKED_CAST") val typed = key as org.gradle.api.attributes.Attribute<Any>
            demand.attributes.attribute(typed, api.attributes.getAttribute(typed)!!)
        }
        demand.attributes.attribute(WINRT_NATIVE_PROJECTION_ARTIFACT, "demand")
        recipe?.let { demand.outgoing.artifact(it.flatMap { task -> task.outputFile }) }
        // Prebuilt owners contain only the SDK projection; all other libraries carry
        // business code and its semantic usage manifest to downstream final links.
        if (hasBusiness) {
            demand.outgoing.artifact(business.compileTaskProvider.flatMap { it.outputFile })
            demand.outgoing.artifact(usageOutput) {
                it.builtBy(requireNotNull(usage))
            }
        }
    }
    project.pluginManager.withPlugin("maven-publish") {
        project.extensions.getByType(org.gradle.api.publish.PublishingExtension::class.java).publications
            .withType(org.gradle.api.publish.maven.MavenPublication::class.java).matching { it.name == target.name }
            .configureEach { publication ->
                val recipeFile = recipe?.flatMap { it.outputFile }
                recipeFile?.let { file -> publication.artifact(file) {
                    it.classifier = "winrt-native-recipe"; it.extension = "winrt-recipe"
                } }
                usage?.let { task -> publication.artifact(usageOutput) {
                    it.classifier = "winrt-native-uses"; it.extension = "winrt-uses"; it.builtBy(task)
                } }
                val base = "${publication.artifactId}-${publication.version}"
                project.tasks.withType(org.gradle.api.publish.tasks.GenerateModuleMetadata::class.java)
                    .matching { it.name == "generateMetadataFileFor${publication.name.replaceFirstChar(Char::uppercaseChar)}Publication" }
                    .configureEach { task ->
                        val output = task.outputFile
                        recipeFile?.let { task.inputs.file(it).withPropertyName("nativeDemandRecipe") }
                        usage?.let { producer ->
                            task.inputs.file(producer.flatMap { it.outputFile }).withPropertyName("nativeDemandUses")
                        }
                        task.doLast {
                            val artifacts = buildList {
                                recipeFile?.let { add(it.get().asFile to "$base-winrt-native-recipe.winrt-recipe") }
                                if (hasBusiness) add(usageOutput.get().asFile to "$base-winrt-native-uses.winrt-uses")
                            }
                            addNativeDemandMetadataVariant(output.get().asFile, artifacts, if (hasBusiness) "$base.klib" else null)
                        }
                    }
            }
    }
}

internal fun addNativeDemandMetadataVariant(metadata: File, artifacts: List<Pair<File, String>>, businessName: String?) {
    val document = Json.parseToJsonElement(metadata.readText()).jsonObject
    val variants = document.getValue("variants").jsonArray
    val original = variants.map { it.jsonObject }.single { variant ->
        val attributes = variant["attributes"]?.jsonObject
        attributes?.get("org.jetbrains.kotlin.platform.type")?.jsonPrimitive?.content == "native" &&
            attributes["org.gradle.usage"]?.jsonPrimitive?.content == "kotlin-api" &&
            WINRT_NATIVE_PROJECTION_ARTIFACT.name !in attributes && variant["files"] != null
    }
    val name = original.getValue("name").jsonPrimitive.content + "-winrt-demand"
    val demand = JsonObject(original + mapOf(
        "name" to JsonPrimitive(name),
        "attributes" to JsonObject(original.getValue("attributes").jsonObject +
            (WINRT_NATIVE_PROJECTION_ARTIFACT.name to JsonPrimitive("demand"))),
        "files" to JsonArray(original.getValue("files").jsonArray.filter {
            businessName != null &&
                (it.jsonObject["url"]?.jsonPrimitive?.content ?: it.jsonObject.getValue("name").jsonPrimitive.content) == businessName
        } + artifacts.map { (file, fileName) -> nativeModuleArtifact(file, fileName) }),
    ))
    metadata.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(document +
        ("variants" to JsonArray(variants.filterNot { it.jsonObject.getValue("name").jsonPrimitive.content == name } + demand)))) + "\n")
}
