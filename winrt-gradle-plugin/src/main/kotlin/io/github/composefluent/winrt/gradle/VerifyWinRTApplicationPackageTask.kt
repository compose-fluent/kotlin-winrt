package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isRegularFile

@CacheableTask
abstract class VerifyWinRTApplicationPackageTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceResolutionReport: RegularFileProperty

    @get:Input
    abstract val applicationVariant: Property<String>

    @get:OutputFile
    abstract val markerFile: RegularFileProperty

    @get:Input
    abstract val verifyPackage: Property<Boolean>

    @get:Input
    abstract val packageMode: Property<String>

    @get:Input
    abstract val generatePackage: Property<Boolean>

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

    @get:OutputDirectory
    abstract val unpackDirectory: DirectoryProperty

    init {
        verifyPackage.convention(true)
        applicationVariant.convention("default")
        packageMode.convention(WinRTApplicationPackageMode.Packaged.name)
        generatePackage.convention(true)
        makeAppxExecutable.convention("")
        winAppCliExecutable.convention("winapp")
        winAppCliVersion.convention(WinAppCliDefaults.VERSION)
        winAppCliPackageSha512.convention(WinAppCliDefaults.PACKAGE_SHA512)
        offline.convention(false)
        windowsSdkVersion.convention("")
        windowsSdkRegistryRoots.convention(emptyList())
    }

    @TaskAction
    fun verify() {
        if (!verifyPackage.get() || !isWindowsHost()) {
            return
        }
        val source = packageFile.get().asFile.toPath()
        val marker = markerFile.get().asFile.toPath()
        Files.deleteIfExists(marker)
        if (!source.isRegularFile()) {
            throw GradleException("Cannot verify appx/msix package because package file does not exist: $source.")
        }
        AppPackageFileSupport.validatePackageExtension(source, "verify")
        val unpackRoot = unpackDirectory.get().asFile.toPath()
        GradleFileOperations.cleanDirectory(unpackRoot)
        Files.createDirectories(unpackRoot)
        if (makeAppxExecutable.get().isNotBlank()) {
            unpackWithLegacyMakeAppx(source, unpackRoot)
        } else {
            unpackWithWinAppCli(source, unpackRoot)
        }
        if (!unpackRoot.resolve("AppxManifest.xml").isRegularFile()) {
            throw GradleException("Verified appx/msix package did not unpack an AppxManifest.xml from $source.")
        }
        val manifest = unpackRoot.resolve("AppxManifest.xml")
        val manifestErrors = ProjectPriManifestSupport.validatePackageManifest(manifest) +
            ProjectPriManifestSupport.validatePackageManifestPayload(manifest, unpackRoot)
        val reportErrors = resourceResolutionReport.orNull?.asFile?.toPath()?.let { report ->
            ApplicationPackagePayloadWriter.validateResolutionReport(report, unpackRoot)
        }.orEmpty()
        val errors = manifestErrors + reportErrors
        if (errors.isNotEmpty()) {
            throw GradleException(
                "Verified appx/msix package contains an invalid AppxManifest.xml or payload content from $source:\n" +
                    errors.joinToString(separator = "\n") { "- $it" },
            )
        }
        marker.parent?.let(Files::createDirectories)
        Files.writeString(
            marker,
            listOf(
                "verified=true",
                "packageName=${source.fileName}",
                "packageSha256=${sha256(source)}",
            ).joinToString(separator = System.lineSeparator(), postfix = System.lineSeparator()),
        )
    }

    private fun discoverMakeAppxExecutable(): Path? =
        ProjectPriToolResolver.makeAppxExecutable(
            makeAppxExecutable.get(),
            windowsSdkVersion.get(),
            runtimeIdentifier.get(),
            windowsSdkRegistryRoots.get().orNullIfEmpty(),
        )

    private fun unpackWithLegacyMakeAppx(source: Path, unpackRoot: Path) {
        val makeAppx = discoverMakeAppxExecutable() ?: run {
            throw GradleException("Cannot verify appx/msix package because makeappx.exe was not found.")
        }
        if (!MakeAppxRunner.unpack(makeAppx, source, unpackRoot, logger)) {
            throw GradleException("Failed to verify appx/msix package at $source.")
        }
    }

    private fun unpackWithWinAppCli(source: Path, unpackRoot: Path) {
        winAppCli().run(
            arguments = listOf(
                "tool",
                "makeappx",
                "unpack",
                "/p",
                source.toString(),
                "/d",
                unpackRoot.toString(),
                "/o",
            ),
            workingDirectory = winAppWorkspace.orNull?.asFile?.toPath() ?: unpackRoot.parent,
            description = "unpack application package $source for verification",
        )
    }

    private fun winAppCli(): WinAppCliSupport = WinAppCliSupport(
        configuredExecutable = winAppCliExecutable.get(),
        cliVersion = winAppCliVersion.get(),
        packageSha512 = winAppCliPackageSha512.get(),
        cliCacheDirectory = winAppCliCacheDirectory.orNull?.asFile?.toPath()
            ?: temporaryDir.toPath().resolve("winapp-cli"),
        offline = offline.get(),
        logger = logger,
    )

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
