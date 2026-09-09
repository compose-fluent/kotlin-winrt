package io.github.composefluent.winrt.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/** Re-evaluate installed tools per build, including when reusing Gradle's configuration cache. */
abstract class WindowsNativeToolchainValueSource : ValueSource<WindowsNativeToolchain, WindowsNativeToolchainValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val forAuthoring: Property<Boolean>
        val authoredHostManifestFiles: ConfigurableFileCollection
        val dependencyIdentityFiles: ConfigurableFileCollection
        val runtimeIdentifier: Property<String>
        val windowsSdkVersion: Property<String>
        val windowsSdkRegistryRoots: ListProperty<String>
    }

    override fun obtain(): WindowsNativeToolchain? {
        if (!isWindowsHost()) return null
        if (parameters.forAuthoring.get() && parameters.authoredHostManifestFiles.isEmpty &&
            parameters.dependencyIdentityFiles.files.flatMap(::readAuthoredHostManifestRecords).none { it.requiresJvmAuthoringHost() }
        ) return null
        val sdk = findWindowsSdk(
            version = parameters.windowsSdkVersion.get().takeIf(String::isNotBlank),
            registryRoots = parameters.windowsSdkRegistryRoots.get().orNullIfEmpty(),
        ) ?: throw IllegalStateException(
            "No Windows SDK ${parameters.windowsSdkVersion.get()} installation found. Kotlin/WinRT JVM native hosts require Windows SDK headers and libraries.",
        )
        return WindowsNativeToolchainDiscovery().resolve(parameters.runtimeIdentifier.get(), sdk)
    }
}

internal fun AuthoredHostManifestRecord.requiresJvmAuthoringHost(): Boolean =
    !hostExportsClass.isNullOrBlank() && targetArtifact.endsWith(".jar", ignoreCase = true) &&
        (activatableClasses + activatableClassTargets.keys).any(String::isNotBlank)
