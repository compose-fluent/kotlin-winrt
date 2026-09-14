package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Path
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/** Isolates NuGet process execution from Gradle configuration-cache instrumentation. */
abstract class PreparedNuGetRootsValueSource : ValueSource<List<String>, PreparedNuGetRootsValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val executable: Property<String>
        val cliVersion: Property<String>
        val cliCacheDirectory: Property<String>
        val scratchDirectory: Property<String>
        val installRoot: Property<String>
        val packageSpecs: ListProperty<String>
        val lookupOnly: Property<Boolean>
    }

    override fun obtain(): List<String> {
        val logger = Logging.getLogger(PreparedNuGetRootsValueSource::class.java)
        val cache = Path.of(parameters.cliCacheDirectory.get())
        val scratch = Path.of(parameters.scratchDirectory.get())
        return if (parameters.lookupOnly.get()) {
            resolveNuGetCliGlobalPackagesRoots(
                enabled = true,
                executable = parameters.executable.get(),
                cliVersion = parameters.cliVersion.get(),
                cliCacheDirectory = cache,
                scratchDirectory = scratch,
                logger = logger,
            )
        } else {
            restoreNuGetPackagesToDirectory(
                packageIdentities = parameters.packageSpecs.get().map(::parseNuGetPackageIdentity),
                installRoot = Path.of(parameters.installRoot.get()),
                nuGetCli = NuGetCliSupport(
                    executable = parameters.executable.get(),
                    cliVersion = parameters.cliVersion.get(),
                    cliCacheDirectory = cache,
                    scratchDirectory = scratch,
                    logger = logger,
                ),
            )
        }.map(Path::toString)
    }
}
