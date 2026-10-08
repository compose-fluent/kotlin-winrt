package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * A fixed SDK compilation, never a project compilation. Uses the normal WinMD
 * generator and WinRT compiler (CsWinRT's projection responsibility). The cache
 * contains no user code, source paths, resources or application package identity.
 */
@CacheableTask
abstract class BuildWinRTXamlSdkPreviewTask @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectionSources: DirectoryProperty
    @get:Classpath abstract val compilerClasspath: ConfigurableFileCollection
    @get:Classpath abstract val compilerPluginClasspath: ConfigurableFileCollection
    @get:Classpath abstract val runtimeClasspath: ConfigurableFileCollection
    @get:Input abstract val javaHome: Property<String>
    @get:Input abstract val javaMajor: Property<Int>
    @get:Internal abstract val cacheDirectory: DirectoryProperty
    @get:OutputFile abstract val outputJar: RegularFileProperty
    @get:OutputFile abstract val metadataReferencesFile: RegularFileProperty

    @TaskAction
    fun build() {
        val projections = projectionSources.get().asFile.toPath()
        val work = temporaryDir.toPath()
        GradleFileOperations.cleanDirectory(work)
        val references = readPreparedMetadataCache(projections.resolve("kotlin-winrt-metadata/resolved-sources.tsv")).files
        val inputs = listOf(projections).flatMap { root ->
            Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .sorted().map { root.relativize(it).toString().replace('\\', '/') to it }.toList() }
        }
        require(inputs.isNotEmpty()) { "The selected SDK has no XAML projection sources." }
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(text: String) { digest.update(text.toByteArray()); digest.update(0) }
        field("kotlin-winrt-xaml-sdk-preview-v2-no-optimize"); field(javaMajor.get().toString())
        inputs.sortedBy { it.first }.forEach { (name, path) -> field(name); Files.newInputStream(path).use { stream ->
            val buffer = ByteArray(64 * 1024); var count = stream.read(buffer)
            while (count >= 0) { digest.update(buffer, 0, count); count = stream.read(buffer) }
        } }
        // Registration metadata is a compiler input even when declaration text
        // stays identical. Never reuse classes against a changed SDK contract.
        val registrationInputs = listOf(projections.resolve("kotlin-winrt-authoring/metadata-index.tsv")) +
            Files.walk(projections.resolve("kotlin-winrt-support")).use { paths -> paths.filter(Files::isRegularFile).sorted().toList() }
        registrationInputs.sorted().forEach { path ->
            field(projections.relativize(path).toString().replace('\\', '/')); field(previewFileHash(path))
        }
        listOf(compilerClasspath, compilerPluginClasspath, runtimeClasspath).forEach { classpath ->
            classpath.files.sortedBy { it.name }.forEach { file -> field(file.name); field(previewFileHash(file.toPath())) }
        }
        val key = digest.digest().joinToString("") { "%02x".format(it) }
        val store = cacheDirectory.get().asFile.toPath()
        val cache = store.resolve(key)
        val jar = cache.resolve("KotlinWinRTXamlSdkPreview.jar")
        withWinRTCacheLock(store.resolve("$key.lock")) {
            if (!validPreviewCache(jar)) {
                Files.createDirectories(cache)
                val classes = work.resolve("classes")
                Files.createDirectories(classes)
                val args = buildList {
                    // These cached SDK classes are a design tool. Avoid expensive
                    // optimizer passes over the SDK's generated registration tables.
                    addAll(listOf("-no-stdlib", "-no-reflect", "-Xno-optimize", "-jvm-target", javaMajor.get().toString(),
                        "-jdk-home", javaHome.get(), "-module-name", "kotlin_winrt_xaml_sdk_preview",
                        "-classpath", runtimeClasspath.asPath, "-d", classes.toString()))
                    add("-Xplugin=${compilerPluginClasspath.asPath.replace(java.io.File.pathSeparator, ",")}")
                    val options = mapOf(
                        "metadataIndex" to projections.resolve("kotlin-winrt-authoring/metadata-index.tsv").toString(),
                        "compilerSupportManifest" to projections.resolve("kotlin-winrt-support/compiler-support.tsv").toString(),
                        "compilerSupportClassOutputDirectory" to classes.toString(),
                        "authoringAssemblyName" to "KotlinWinRTXamlSdkPreview",
                        "authoringTargetArtifactName" to "KotlinWinRTXamlSdkPreview.jar",
                        "projectionSupportOwnerArtifactName" to "KotlinWinRTXamlSdkPreview.jar",
                        "projectionSupportMode" to "embedded",
                    )
                    options.forEach { (name, value) -> add("-P"); add("plugin:io.github.composefluent.winrt.compiler:$name=$value") }
                    addAll(inputs.map { it.second.toString() })
                }
                // A response file avoids Windows command line length limits. Kotlin
                // owns parsing; quote paths and escapes instead of composing shell code.
                val argumentFile = work.resolve("compiler.args")
                Files.writeString(argumentFile, args.joinToString("\n") {
                    "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
                })
                logger.lifecycle("Preparing the cached WinUI XAML design host (SDK only).")
                exec.javaexec { spec ->
                    spec.executable = Path.of(javaHome.get(), "bin", "java.exe").toString()
                    spec.classpath = compilerClasspath
                    spec.mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
                    spec.jvmArgs("-Xmx4096m", "-XX:+UseSerialGC")
                    spec.args("@${argumentFile}")
                }
                val temporaryJar = cache.resolve(".preview.jar.tmp")
                JarOutputStream(Files.newOutputStream(temporaryJar)).use { archive ->
                    Files.walk(classes).use { paths -> paths.filter(Files::isRegularFile).sorted().forEach { file ->
                        archive.putNextEntry(JarEntry(classes.relativize(file).toString().replace('\\', '/')).apply { time = 0 })
                        Files.copy(file, archive); archive.closeEntry()
                    } }
                }
                Files.move(temporaryJar, jar, REPLACE_EXISTING, ATOMIC_MOVE)
                Files.writeString(cache.resolve("sha256"), previewFileHash(jar))
            } else logger.lifecycle("Reusing the cached WinUI XAML design host; project code is not compiled.")
            val target = outputJar.get().asFile.toPath()
            Files.createDirectories(target.parent)
            Files.copy(jar, target, REPLACE_EXISTING)
        }
        val metadata = metadataReferencesFile.get().asFile.toPath()
        Files.createDirectories(metadata.parent)
        GradleFileOperations.writeStringIfChanged(metadata, "{\"ReferenceAssemblies\":[" + references.joinToString(",") {
            "{\"FullPath\":${listOf(it.toString()).toJsonArray().removePrefix("[").removeSuffix("]")}}"
        } + "]}")
    }
}

private fun previewFileHash(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { stream ->
        val buffer = ByteArray(64 * 1024); var count = stream.read(buffer)
        while (count >= 0) { digest.update(buffer, 0, count); count = stream.read(buffer) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun validPreviewCache(jar: Path) = runCatching {
    Files.isRegularFile(jar) && Files.readString(jar.parent.resolve("sha256")) == previewFileHash(jar) &&
        ZipFile(jar.toFile()).use { it.getEntry("io/github/composefluent/winrt/generated/xaml/KotlinWinRTXamlPreviewHost.class") != null }
}.getOrDefault(false)
