package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files

@DisableCachingByDefault(because = "Installing app packages mutates the local Windows user profile.")
abstract class InstallWinRTApplicationPackageTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageFile: RegularFileProperty

    @get:Input
    abstract val installPackage: Property<Boolean>

    @get:Input
    abstract val powerShellExecutable: Property<String>

    @get:Input
    abstract val forceApplicationShutdown: Property<Boolean>

    init {
        installPackage.convention(false)
        powerShellExecutable.convention("powershell.exe")
        forceApplicationShutdown.convention(true)
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
        if (!PowerShellAppxInstaller.install(
            powerShellExecutable = powerShellExecutable.get(),
            packageFile = source,
            forceApplicationShutdown = forceApplicationShutdown.get(),
            logger = logger,
        )) {
            throw GradleException("Failed to install appx/msix package at $source.")
        }
    }
}
