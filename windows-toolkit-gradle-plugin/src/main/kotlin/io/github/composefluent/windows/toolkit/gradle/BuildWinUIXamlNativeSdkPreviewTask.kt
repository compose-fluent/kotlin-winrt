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
import javax.inject.Inject

/** Fixed UWP SDK host. CsWinRT's projection/authoring boundaries remain the same;
 * Kotlin/Native supplies the AppContainer executable without a JVM filesystem dependency. */
@CacheableTask
internal abstract class BuildWinUIXamlNativeSdkPreviewTask @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectionSources: DirectoryProperty
    @get:Classpath abstract val compilerPluginClasspath: ConfigurableFileCollection
    @get:Classpath abstract val compilerClasspath: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerPlatformFiles: ConfigurableFileCollection
    @get:Classpath abstract val runtimeLibraries: ConfigurableFileCollection
    @get:Input abstract val compilerHome: Property<String>
    @get:Input abstract val javaHome: Property<String>
    @get:Internal abstract val cacheDirectory: DirectoryProperty
    @get:OutputFile abstract val executable: RegularFileProperty
    @get:OutputFile abstract val metadataReferencesFile: RegularFileProperty

    @TaskAction fun build() {
        val root = projectionSources.get().asFile.toPath()
        val home = Path.of(compilerHome.get())
        require(Files.isRegularFile(home.resolve("konan/lib/kotlin-native-compiler-embeddable.jar"))) {
            "Install the project's Kotlin/Native Windows compiler before preparing the UWP SDK designer."
        }
        val work = temporaryDir.toPath()
        Files.createDirectories(work)
        val entry = work.resolve("SdkPreviewEntry.kt")
        Files.writeString(entry, """
            @file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)
            package io.github.composefluent.winrt.generated.xaml
            import io.github.composefluent.winrt.runtime.*
            fun main() {
                WinAppHostScope.initialize(WinAppHostConfiguration.fromStagedRuntimeAssets()).use {
                    KotlinWinUIXamlPreviewHost.main(emptyArray())
                }
            }
        """.trimIndent())
        val sources = Files.walk(root).use { paths -> paths.filter {
            Files.isRegularFile(it) && it.toString().endsWith(".kt")
        }.sorted().toList() }
        require(sources.isNotEmpty()) { "The selected SDK has no XAML projection sources." }
        val output = executable.get().asFile.toPath()
        Files.createDirectories(output.parent)
        val options = mapOf(
            "xamlFramework" to "uwp",
            "metadataIndex" to root.resolve("kotlin-winrt-authoring/metadata-index.tsv").toString(),
            "compilerSupportManifest" to root.resolve("kotlin-winrt-support/compiler-support.tsv").toString(),
            "compilerSupportClassOutputDirectory" to work.resolve("support").toString(),
            "authoringAssemblyName" to "KotlinWinUIXamlSdkPreview",
            "authoringTargetArtifactName" to "KotlinWinUIXamlPreview.exe",
            "projectionSupportOwnerArtifactName" to "KotlinWinUIXamlPreview.exe",
            "projectionSupportMode" to "embedded",
        )
        val arguments = buildList {
            addAll(listOf("-g", "-target", "mingw_x64", "-produce", "program", "-o", output.toString(),
                "-entry", "io.github.composefluent.winrt.generated.xaml.main", "-Xmulti-platform",
                "-opt-in=kotlinx.cinterop.ExperimentalForeignApi", "-opt-in=kotlin.experimental.ExperimentalNativeApi",
                "-Xcommon-sources=${sources.joinToString(",")}"))
            runtimeLibraries.files.sortedBy { it.name }.forEach { addAll(listOf("-library", it.absolutePath)) }
            add("-Xplugin=${compilerPluginClasspath.files.joinToString(",") { it.absolutePath }}")
            options.forEach { (key, value) -> addAll(listOf("-P", "plugin:io.github.composefluent.winrt.compiler:$key=$value")) }
            addAll(sources.map(Path::toString)); add(entry.toString())
        }
        val response = work.resolve("compiler.args")
        Files.writeString(response, arguments.joinToString("\n") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" })
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(value: String) { digest.update(value.toByteArray()); digest.update(0) }
        field("kotlin-winrt-uwp-native-sdk-preview-debug-v2")
        field(home.toString()); field(javaHome.get()); field(Files.readString(entry))
        val registration = listOf(root.resolve("kotlin-winrt-authoring/metadata-index.tsv")) +
            Files.walk(root.resolve("kotlin-winrt-support")).use { paths -> paths.filter(Files::isRegularFile).sorted().toList() }
        (sources + registration).forEach { path -> field(root.relativize(path).toString()); field(previewFileHash(path)) }
        // Include the installed compiler's standard library and target settings,
        // as well as the compiler and all runtime/compiler-plugin KLIBs/JARs.
        (compilerClasspath.files + compilerPluginClasspath.files + runtimeLibraries.files).map { it.toPath() }
            .plus(compilerPlatformFiles.files.map { it.toPath() }).forEach { path ->
                field(path.fileName.toString())
                if (Files.isDirectory(path)) Files.walk(path).use { paths -> paths.filter(Files::isRegularFile).sorted().forEach {
                    field(path.relativize(it).toString()); field(previewFileHash(it))
                } } else field(previewFileHash(path))
            }
        val key = digest.digest().joinToString("") { "%02x".format(it) }
        val store = cacheDirectory.get().asFile.toPath()
        val cache = store.resolve(key)
        val cachedExecutable = cache.resolve("KotlinWinUIXamlPreview.exe")
        withWinRTCacheLock(store.resolve("$key.lock")) {
            val valid = runCatching { Files.isRegularFile(cachedExecutable) &&
                Files.readString(cache.resolve("sha256")) == previewFileHash(cachedExecutable) &&
                Files.newInputStream(cachedExecutable).use { it.read() == 0x4d && it.read() == 0x5a }
            }.getOrDefault(false)
            if (valid) {
                logger.lifecycle("Reusing the cached UWP Native XAML design host; project code is not compiled.")
                Files.copy(cachedExecutable, output, REPLACE_EXISTING)
            } else {
                logger.lifecycle("Preparing the cached UWP Native XAML design host (SDK only).")
                exec.javaexec { spec ->
                    spec.executable = Path.of(javaHome.get(), "bin/java.exe").toString()
                    spec.classpath = compilerClasspath
                    spec.mainClass.set("org.jetbrains.kotlin.cli.utilities.MainKt")
                    spec.jvmArgs("-Xmx6g", "-Dfile.encoding=UTF-8", "-Dkonan.home=$home")
                    spec.args("konanc", "@${response}")
                }
                Files.createDirectories(cache)
                val temporary = cache.resolve(".preview.exe.tmp")
                Files.copy(output, temporary, REPLACE_EXISTING)
                Files.move(temporary, cachedExecutable, REPLACE_EXISTING, ATOMIC_MOVE)
                Files.writeString(cache.resolve("sha256"), previewFileHash(cachedExecutable))
            }
        }
        val references = readPreparedMetadataCache(root.resolve("kotlin-winrt-metadata/resolved-sources.tsv")).files
        val metadata = metadataReferencesFile.get().asFile.toPath()
        Files.createDirectories(metadata.parent)
        GradleFileOperations.writeStringIfChanged(metadata, "{\"ReferenceAssemblies\":[" + references.joinToString(",") {
            "{\"FullPath\":${listOf(it.toString()).toJsonArray().removePrefix("[").removeSuffix("]")}}"
        } + "]}")
    }
}
