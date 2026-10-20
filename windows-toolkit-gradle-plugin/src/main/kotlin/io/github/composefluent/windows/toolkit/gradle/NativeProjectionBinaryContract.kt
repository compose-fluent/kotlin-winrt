package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import javax.inject.Inject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream

/** Publish the compiler's business header without bundling ordinary overlay bodies. */
@CacheableTask
abstract class PackWinRTNativeHeaderTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val header: ConfigurableFileCollection
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction fun pack() {
        val source = header.singleFile
        val entries = sortedMapOf<String, ByteArray>()
        if (source.isDirectory) {
            source.walkTopDown().filter(File::isFile).forEach {
                entries[it.relativeTo(source).invariantSeparatorsPath] = it.readBytes()
            }
        } else {
            ZipFile(source).use { zip -> zip.entries().asSequence().filterNot { it.isDirectory }.forEach {
                entries[it.name] = zip.getInputStream(it).use { stream -> stream.readBytes() }
            } }
        }
        check("default/manifest" in entries) { "Missing Native header manifest: $source" }
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name).apply { time = 0 })
            zip.write(bytes)
            zip.closeEntry()
        } }
        val output = outputFile.get().asFile
        val bytes = buffer.toByteArray()
        if (!output.isFile || !output.readBytes().contentEquals(bytes)) {
            output.parentFile.mkdirs()
            output.writeBytes(bytes)
        }
    }
}

/** Official header KLIB IR retains defaults, annotations and inline bodies without ordinary
 * implementation bodies. Dumping that IR omits serialized source offsets, unlike hashing ZIP bytes.
 */
@CacheableTask
abstract class GenerateWinRTNativeContractTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val headerKlibs: ConfigurableFileCollection
    @get:Classpath abstract val compilerClasspath: ConfigurableFileCollection
    @get:Nested abstract val javaLauncher: Property<JavaLauncher>
    @get:Input abstract val kotlinCompilerVersion: Property<String>
    @get:Input abstract val owner: Property<String>
    @get:Input abstract val producerVersion: Property<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty
    @get:Inject protected abstract val execOperations: ExecOperations

    @TaskAction fun generate() {
        val tool = NativeKlibContractReader(execOperations, javaLauncher.get(), compilerClasspath)
        val declarations = sortedMapOf<String, String>()
        headerKlibs.files.filter { it.exists() }.forEach { file ->
            val manifest = tool.manifest(file)
            val module = requireNotNull(manifest.getProperty("unique_name")) { "Missing KLIB module identity: $file" }
            val header = listOf("unique_name", "compiler_version", "abi_version", "metadata_version", "native_targets",
                "builtins_platform", "language_features", "ir_signature_versions")
                .joinToString("\n") { "$it=${manifest.getProperty(it).orEmpty()}" }
            check(declarations.put(module, projectionContractDigest(header + "\n" + tool.dump(file, "dump-ir"))) == null) {
                "Multiple reference headers claim Native module '$module'"
            }
        }
        check(declarations.isNotEmpty()) { "No Native projection headers were produced" }
        writeProjectionBinaryContract(outputFile.get().asFile, "mingw_x64", kotlinCompilerVersion.get(),
            owner.get(), producerVersion.get(), declarations)
    }
}

internal fun configureNativeProjectionHeader(project: Project, compilation: KotlinNativeCompilation): Provider<File> {
    val base = project.layout.buildDirectory.dir("classes/kotlin-winrt/native-header/${compilation.compileTaskProvider.name}")
    val packed = base.map { File(it.asFile.parentFile, it.asFile.name + ".klib") }
    val header = compilation.compileTaskProvider.flatMap { task ->
        base.zip(task.produceUnpackagedKlib) { directory, unpacked ->
            if (unpacked) directory.asFile else File(directory.asFile.parentFile, directory.asFile.name + ".klib")
        }
    }
    compilation.compileTaskProvider.configure { task ->
        // Native uses the compilation's packaging mode for its header too. Declaring
        // both possible outputs also lets NO-SOURCE clean headers after deleting sources.
        task.outputs.dir(base).withPropertyName("winrtProjectionUnpackedHeader")
        task.outputs.file(packed).withPropertyName("winrtProjectionPackedHeader")
        task.compilerOptions.freeCompilerArgs.add(base.map { "-Xheader-klib-path=${it.asFile.absolutePath}" })
        task.compilerOptions.freeCompilerArgs.add("-Xklib-relative-path-base=${project.projectDir.absolutePath}")
    }
    return header
}

/** CsWinRT publishes the reference assembly, not its discarded ordinary method bodies.
 * Native's official header keeps the same module identity, inline/default IR and ABI.
 * The normal reference output stays private for the owner's overlay friendship. */
internal fun nativeProjectionReferenceArtifact(project: Project, reference: KotlinNativeCompilation): Provider<RegularFile> {
    val name = "${reference.target.name}WinRTProjectionReferenceKlib"
    val packed = if (name in project.tasks.names) {
        project.tasks.named(name, PackWinRTNativeHeaderTask::class.java)
    } else {
        val header = configureNativeProjectionHeader(project, reference)
        project.tasks.register(name, PackWinRTNativeHeaderTask::class.java) { task ->
            task.header.from(project.files(header).builtBy(reference.compileTaskProvider))
            task.outputFile.set(project.layout.buildDirectory.file("libs/${reference.target.name}-winrt-projection-reference.klib"))
        }
    }
    return packed.flatMap { it.outputFile }
}
