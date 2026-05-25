package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files
import java.nio.file.Path

@DisableCachingByDefault(because = "Authenticode signatures are time- and certificate-store-dependent.")
abstract class SignWinRtApplicationPackageTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputPackageFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val signPackage: Property<Boolean>

    @get:Input
    abstract val signToolExecutable: Property<String>

    @get:Input
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val runtimeIdentifier: Property<String>

    @get:Input
    abstract val signingCertificateThumbprint: Property<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val signingCertificateFile: RegularFileProperty

    @get:Input
    abstract val signingCertificatePassword: Property<String>

    @get:Input
    abstract val signingTimestampUrl: Property<String>

    @get:Input
    abstract val signingHashAlgorithm: Property<String>

    init {
        signPackage.convention(false)
        signToolExecutable.convention("")
        windowsSdkVersion.convention("")
        signingCertificateThumbprint.convention("")
        signingCertificatePassword.convention("")
        signingTimestampUrl.convention("")
        signingHashAlgorithm.convention("SHA256")
    }

    @TaskAction
    fun sign() {
        if (!signPackage.get() || !isWindowsHost()) {
            return
        }
        val source = inputPackageFile.get().asFile.toPath()
        val target = outputFile.get().asFile.toPath()
        if (!Files.isRegularFile(source)) {
            throw GradleException("Cannot sign appx/msix package because package file does not exist: $source.")
        }
        AppPackageFileSupport.validatePackageExtension(source, "sign")
        AppPackageFileSupport.validatePackageExtension(target, "sign")
        if (source.toAbsolutePath().normalize() == target.toAbsolutePath().normalize()) {
            throw GradleException("Cannot sign appx/msix package because signed output file must be different from input package file: $target.")
        }
        val certificateFile = signingCertificateFile.orNull?.asFile?.toPath()
        if (certificateFile != null && !Files.isRegularFile(certificateFile)) {
            throw GradleException("Cannot sign appx/msix package because signing certificate file does not exist: $certificateFile.")
        }
        val signTool = discoverSignToolExecutable() ?: run {
            throw GradleException("Cannot sign appx/msix package because signtool.exe was not found.")
        }
        Files.deleteIfExists(target)
        target.parent?.let(Files::createDirectories)
        Files.copy(source, target)
        val signed = SignToolRunner.sign(
            signTool = signTool,
            packageFile = target,
            certificateThumbprint = signingCertificateThumbprint.get(),
            certificateFile = certificateFile,
            certificatePassword = signingCertificatePassword.get(),
            timestampUrl = signingTimestampUrl.get(),
            hashAlgorithm = signingHashAlgorithm.get(),
            logger = logger,
        )
        if (!signed) {
            Files.deleteIfExists(target)
            throw GradleException("Failed to sign appx/msix package at $target.")
        }
    }

    private fun discoverSignToolExecutable(): Path? =
        ProjectPriToolResolver.signToolExecutable(
            signToolExecutable.get(),
            windowsSdkVersion.get(),
            runtimeIdentifier.get(),
        )
}
