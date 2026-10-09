package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/** Mirrors VC ResourceCompile and C# ApplicationIcon: one ICO becomes Win32 ICON/GROUP_ICON resources. */
@DisableCachingByDefault(because = "Uses the installed Windows resource compiler")
abstract class CompileWinAppIconTask : DefaultTask() {
    @get:Inject
    protected abstract val providers: ProviderFactory

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val launcherIcon: RegularFileProperty

    @get:Input
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val windowsSdkRegistryRoots: ListProperty<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    val resourceCompiler: Provider<File> = providers.of(WindowsResourceCompilerValueSource::class.java) {
        it.parameters.windowsSdkVersion.set(windowsSdkVersion)
        it.parameters.windowsSdkRegistryRoots.set(windowsSdkRegistryRoots)
    }

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        windowsSdkVersion.convention("")
        windowsSdkRegistryRoots.convention(emptyList())
        onlyIf("a launcher ICO is configured on Windows") { launcherIcon.isPresent && isWindowsHost() }
    }

    @TaskAction
    fun compile() {
        val icon = launcherIcon.get().asFile.toPath()
        require(icon.fileName.toString().endsWith(".ico", ignoreCase = true)) {
            "application.launcherIcon requires a Win32 .ico file; PNG, SVG and other image files must be converted to ICO first: $icon"
        }
        val output = outputFile.get().asFile.toPath()
        Files.createDirectories(output.parent)
        Files.deleteIfExists(output)
        val work = temporaryDir.toPath()
        Files.createDirectories(work)
        // A fixed local name avoids RC string escaping and preserves the original multi-size ICO bytes.
        Files.copy(icon, work.resolve("launcher.ico"), StandardCopyOption.REPLACE_EXISTING)
        val source = work.resolve("launcher.rc")
        Files.writeString(source, "1 ICON \"launcher.ico\"\n")
        val result = runWindowsNativeProcess(
            listOf(resourceCompiler.get().absolutePath, "/nologo", "/fo", output.toString(), source.toString()),
            System.getenv(),
            work,
        )
        if (result.exitCode != 0) {
            Files.deleteIfExists(output)
            error("Launcher icon resource compilation failed with exit code ${result.exitCode}.\n${result.output}")
        }
    }
}

/** Discover the SDK resource compiler without requiring a VC developer shell or C++ compiler. */
abstract class WindowsResourceCompilerValueSource : ValueSource<File, WindowsResourceCompilerValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val windowsSdkVersion: Property<String>
        val windowsSdkRegistryRoots: ListProperty<String>
    }

    override fun obtain(): File? {
        if (!isWindowsHost()) return null
        val sdk = findWindowsSdk(
            parameters.windowsSdkVersion.get(),
            parameters.windowsSdkRegistryRoots.get().orNullIfEmpty(),
        ) ?: error("Launcher icon compilation requires an installed Windows SDK.")
        val host = windowsSdkArchitecture("win-${System.getProperty("os.arch").lowercase().replace("aarch64", "arm64")}")
        return sdk.tool("rc.exe", host)?.toFile()
            ?: error("Windows SDK ${sdk.version} has no $host rc.exe resource compiler at ${sdk.binRoot}.")
    }
}
