package io.github.composefluent.winrt.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/** Select one installed SDK for metadata, native builds, and manifest defaults. */
abstract class WindowsSdkVersionValueSource : ValueSource<String, WindowsSdkVersionValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val registryRoots: ListProperty<String>
    }

    override fun obtain(): String? =
        if (isWindowsHost()) findWindowsSdk(registryRoots = parameters.registryRoots.get())?.version else null
}
