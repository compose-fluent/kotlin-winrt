package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WindowsSdkRootDiscovery
import java.io.ByteArrayOutputStream
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Reads the Windows SDK registry without violating Gradle's configuration-cache process rules.
 *
 * A ValueSource is allowed to invoke a process while its value is being calculated during
 * configuration. The result is tracked as a configuration input and can then be reused by the
 * metadata resolver as an explicit registry candidate list.
 */
abstract class WindowsSdkRegistryRootsValueSource : ValueSource<List<String>, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): List<String> {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return emptyList()
        }
        return WindowsSdkRootDiscovery.registryQueryViews
            .mapNotNull(::queryRegistryView)
            .distinct()
    }

    private fun queryRegistryView(view: List<String>): String? {
        val output = ByteArrayOutputStream()
        val result = runCatching {
            execOperations.exec { spec ->
                spec.commandLine(
                    "reg.exe",
                    "query",
                    WindowsSdkRootDiscovery.registryKey,
                    "/v",
                    WindowsSdkRootDiscovery.registryValue,
                    *view.toTypedArray(),
                )
                spec.standardOutput = output
                spec.errorOutput = output
                spec.isIgnoreExitValue = true
            }
        }.getOrNull() ?: return null
        if (result.exitValue != 0) {
            return null
        }

        return WindowsSdkRootDiscovery.parseRegistryOutput(output.toByteArray())
    }

}
