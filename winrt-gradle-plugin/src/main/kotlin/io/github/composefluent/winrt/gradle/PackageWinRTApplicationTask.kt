package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

@CacheableTask
abstract class PackageWinRTApplicationTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageDirectory: DirectoryProperty

    @get:Input
    abstract val applicationVariant: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val generatePackage: Property<Boolean>

    @get:Input
    abstract val packageMode: Property<String>

    /** Whether WinApp CLI should package Windows App SDK runtime payloads self-contained. */
    @get:Input
    abstract val selfContained: Property<Boolean>

    @get:Input
    abstract val makeAppxExecutable: Property<String>

    @get:Input
    abstract val winAppCliExecutable: Property<String>

    @get:Input
    abstract val winAppCliVersion: Property<String>

    @get:Input
    abstract val winAppCliPackageSha512: Property<String>

    @get:Internal
    abstract val winAppCliCacheDirectory: DirectoryProperty

    @get:Internal
    abstract val winAppWorkspace: DirectoryProperty

    @get:Input
    abstract val offline: Property<Boolean>

    @get:Input
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val windowsSdkRegistryRoots: ListProperty<String>

    @get:Input
    abstract val runtimeIdentifier: Property<String>

    init {
        generatePackage.convention(true)
        applicationVariant.convention("default")
        packageMode.convention(WinRTApplicationPackageMode.Packaged.name)
        selfContained.convention(false)
        makeAppxExecutable.convention("")
        winAppCliExecutable.convention("winapp")
        winAppCliVersion.convention(WinAppCliDefaults.VERSION)
        winAppCliPackageSha512.convention(WinAppCliDefaults.PACKAGE_SHA512)
        offline.convention(false)
        windowsSdkVersion.convention("")
        windowsSdkRegistryRoots.convention(emptyList())
    }

    @TaskAction
    fun pack() {
        if (!generatePackage.get() || !isWindowsHost()) {
            return
        }
        val packageRoot = packageDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val target = outputFile.get().asFile.toPath().toAbsolutePath().normalize()
        if (target.startsWith(packageRoot)) {
            throw GradleException(
                "Cannot create appx/msix package at $target because the package output is inside the staged package root $packageRoot.",
            )
        }
        AppPackageFileSupport.validatePackageExtension(target, "create")
        if (!packageRoot.resolve("AppxManifest.xml").isRegularFile()) {
            throw GradleException("Cannot create appx/msix package because AppxManifest.xml was not staged in $packageRoot.")
        }
        val manifest = packageRoot.resolve("AppxManifest.xml")
        val manifestErrors = ProjectPriManifestSupport.validatePackageManifest(manifest) +
            ProjectPriManifestSupport.validatePackageManifestPayload(manifest, packageRoot)
        if (manifestErrors.isNotEmpty()) {
            throw GradleException(
                "Cannot create appx/msix package because AppxManifest.xml is invalid in $packageRoot:\n" +
                    manifestErrors.joinToString(separator = "\n") { "- $it" },
            )
        }
        Files.deleteIfExists(target)
        target.parent?.let(Files::createDirectories)
        if (makeAppxExecutable.get().isNotBlank()) {
            packageWithLegacyMakeAppx(packageRoot, target)
        } else {
            packageWithWinAppCli(packageRoot, manifest, target)
        }
        if (!target.isRegularFile()) {
            throw GradleException("WinApp packaging completed but did not create appx/msix package at $target.")
        }
    }

    private fun packageWithLegacyMakeAppx(packageRoot: Path, target: Path) {
        val makeAppx = discoverMakeAppxExecutable() ?: run {
            throw GradleException("Cannot create appx/msix package because makeappx.exe was not found.")
        }
        if (!MakeAppxRunner.pack(makeAppx, packageRoot, target, logger)) {
            Files.deleteIfExists(target)
            throw GradleException("Failed to create appx/msix package at $target.")
        }
    }

    private fun packageWithWinAppCli(packageRoot: Path, manifest: Path, target: Path) {
        val arguments = if (target.fileName.toString().endsWith(".appx", ignoreCase = true)) {
            listOf(
                "tool",
                "makeappx",
                "pack",
                "/d",
                packageRoot.toString(),
                "/p",
                target.toString(),
                "/o",
            )
        } else {
            buildList {
                add("package")
                add(packageRoot.toString())
                add("--output")
                add(target.toString())
                add("--manifest")
                add(manifest.toString())
                add("--skip-pri")
                if (selfContained.get()) {
                    add("--self-contained")
                }
                add("--quiet")
            }
        }
        try {
            winAppCli().run(
                arguments = arguments,
                workingDirectory = winAppWorkspace.orNull?.asFile?.toPath() ?: packageRoot,
                description = "package the staged application at $packageRoot",
            )
        } catch (error: Exception) {
            runCatching { Files.deleteIfExists(target) }
            throw error
        }
    }

    private fun discoverMakeAppxExecutable(): Path? =
        ProjectPriToolResolver.makeAppxExecutable(
            makeAppxExecutable.get(),
            windowsSdkVersion.get(),
            runtimeIdentifier.get(),
            windowsSdkRegistryRoots.get().orNullIfEmpty(),
        )

    private fun winAppCli(): WinAppCliSupport = WinAppCliSupport(
        configuredExecutable = winAppCliExecutable.get(),
        cliVersion = winAppCliVersion.get(),
        packageSha512 = winAppCliPackageSha512.get(),
        cliCacheDirectory = winAppCliCacheDirectory.orNull?.asFile?.toPath()
            ?: temporaryDir.toPath().resolve("winapp-cli"),
        offline = offline.get(),
        logger = logger,
    )
}
