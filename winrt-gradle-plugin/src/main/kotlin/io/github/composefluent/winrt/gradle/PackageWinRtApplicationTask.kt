package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

@CacheableTask
abstract class PackageWinRtApplicationTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val generatePackage: Property<Boolean>

    @get:Input
    abstract val makeAppxExecutable: Property<String>

    @get:Input
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val runtimeIdentifier: Property<String>

    init {
        generatePackage.convention(true)
        makeAppxExecutable.convention("")
        windowsSdkVersion.convention("")
    }

    @TaskAction
    fun pack() {
        val packageRoot = packageDirectory.get().asFile.toPath()
        val target = outputFile.get().asFile.toPath()
        Files.deleteIfExists(target)
        if (!generatePackage.get() || !isWindowsHost()) {
            return
        }
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
        val makeAppx = discoverMakeAppxExecutable() ?: run {
            throw GradleException("Cannot create appx/msix package because makeappx.exe was not found.")
        }
        target.parent?.let(Files::createDirectories)
        if (!MakeAppxRunner.pack(makeAppx, packageRoot, target, logger)) {
            Files.deleteIfExists(target)
            throw GradleException("Failed to create appx/msix package at $target.")
        }
        if (!target.isRegularFile()) {
            throw GradleException("makeappx completed but did not create appx/msix package at $target.")
        }
    }

    private fun discoverMakeAppxExecutable(): Path? =
        ProjectPriToolResolver.makeAppxExecutable(
            makeAppxExecutable.get(),
            windowsSdkVersion.get(),
            runtimeIdentifier.get(),
        )
}
