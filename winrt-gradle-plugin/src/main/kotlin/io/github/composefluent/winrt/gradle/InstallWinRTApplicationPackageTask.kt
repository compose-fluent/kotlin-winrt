package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path

@DisableCachingByDefault(because = "Installing app packages mutates the local Windows user profile.")
abstract class InstallWinRTApplicationPackageTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageFile: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyPackageFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyLockFiles: ConfigurableFileCollection

    @get:Input
    abstract val dependencyPackageSpecs: ListProperty<String>

    @get:Input
    abstract val runtimeIdentifier: Property<String>

    @get:Input
    abstract val installPackage: Property<Boolean>

    @get:Input
    abstract val packageType: Property<String>

    /** Whether restored Windows App SDK framework packages are already embedded in the app. */
    @get:Input
    abstract val includeRestoredFrameworkDependencies: Property<Boolean>

    @get:Input
    abstract val powerShellExecutable: Property<String>

    @get:Input
    abstract val forceApplicationShutdown: Property<Boolean>

    init {
        installPackage.convention(false)
        packageType.convention(WindowsPackageType.Packaged.name)
        includeRestoredFrameworkDependencies.convention(true)
        powerShellExecutable.convention("powershell.exe")
        forceApplicationShutdown.convention(true)
        dependencyPackageSpecs.convention(emptyList())
        runtimeIdentifier.convention(currentWindowsRuntimeIdentifier())
    }

    @TaskAction
    fun install() {
        if (!installPackage.get() || !isWindowsHost()) {
            return
        }
        val source = packageFile.get().asFile.toPath()
        if (!Files.isRegularFile(source)) {
            throw GradleException("Cannot install appx/msix package because package file does not exist: $source.")
        }
        AppPackageFileSupport.validatePackageExtension(source, "install")
        val restoredRoots = dependencyLockFiles.files
            .filter(java.io.File::isFile)
            .let { lockFiles ->
                if (lockFiles.isEmpty()) emptyList() else readWinAppRestoredPackageRoots(
                    lockFiles = lockFiles,
                    rootPackageSpecs = dependencyPackageSpecs.get(),
                )
            }
        val explicitDependencies = dependencyPackageFiles.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .onEach { dependency ->
                AppxManifestPackageSupport.validateFrameworkPackageArchive(dependency, runtimeIdentifier.get())
            }
        val restoredDependencies = if (includeRestoredFrameworkDependencies.get()) {
            AppxManifestPackageSupport.discoverFrameworkPackageArchives(restoredRoots, runtimeIdentifier.get())
        } else {
            emptyList()
        }
        val dependencyPaths = AppxManifestPackageSupport.selectFrameworkPackageArchives(
            applicationPackage = source,
            candidateArchives = explicitDependencies + restoredDependencies,
            runtimeIdentifier = runtimeIdentifier.get(),
        )
            .onEach { dependency ->
                if (!Files.isRegularFile(dependency)) {
                    throw GradleException("Configured AppX dependency package does not exist: $dependency")
                }
                if (dependency == source.toAbsolutePath().normalize()) {
                    throw GradleException("Application package cannot also be used as its own dependency: $dependency")
                }
            }
        if (!PowerShellAppxInstaller.install(
            powerShellExecutable = powerShellExecutable.get(),
            packageFile = source,
            dependencyPackagePaths = dependencyPaths,
            forceApplicationShutdown = forceApplicationShutdown.get(),
            logger = logger,
        )) {
            throw GradleException("Failed to install appx/msix package at $source.")
        }
    }
}
