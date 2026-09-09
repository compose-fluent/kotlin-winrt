package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException

internal data class WinAppPackagePin(
    val name: String,
    val version: String,
)

internal object WinAppConfigurationDefaults {
    const val WINDOWS_SDK_TOOLS_VERSION = "10.0.26100.1742"

    val toolingPackages: List<WinAppPackagePin> = toolingPackages(WINDOWS_SDK_TOOLS_VERSION)

    fun toolingPackages(windowsSdkToolsVersion: String): List<WinAppPackagePin> = listOf(
        WinAppPackagePin("Microsoft.Windows.CppWinRT", "2.0.240405.15"),
        WinAppPackagePin("Microsoft.Windows.SDK.BuildTools", windowsSdkToolsVersion),
        WinAppPackagePin("Microsoft.Windows.SDK.CPP", windowsSdkToolsVersion),
    )

    val toolingPackageIds: Set<String> = toolingPackages
        .mapTo(linkedSetOf()) { pkg -> pkg.name.lowercase() }
        .apply {
            // WinApp restores this BuildTools dependency for packaging; it is not application payload.
            add("microsoft.windows.sdk.buildtools.msix")
        }
}

internal fun resolveWinAppPackagePins(
    packageSpecs: Iterable<String>,
    toolingPackages: Iterable<WinAppPackagePin> = WinAppConfigurationDefaults.toolingPackages,
): List<WinAppPackagePin> {
    val declared = packageSpecs
        .map(::parseNuGetPackageIdentity)
        .groupBy { identity -> identity.normalizedPackageId.lowercase() }
        .mapValues { (_, identities) ->
            val versions = identities
                .map { identity -> identity.normalizedVersion }
                .distinctBy(String::lowercase)
            if (versions.size != 1) {
                throw GradleException(
                    "Conflicting NuGet versions were declared for ${identities.first().normalizedPackageId}: " +
                        versions.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(),
                )
            }
            WinAppPackagePin(
                name = identities
                    .map { identity -> identity.normalizedPackageId }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { value -> value })
                    .first(),
                version = versions.single(),
            )
        }
        .toMutableMap()

    toolingPackages.forEach { tooling ->
        validateWinAppPackagePin(tooling)
        val key = tooling.name.lowercase()
        val existing = declared[key]
        if (existing != null && !existing.version.equals(tooling.version, ignoreCase = true)) {
            throw GradleException(
                "NuGet package ${tooling.name} is required by kotlin-winrt at ${tooling.version}, " +
                    "but ${existing.version} was declared.",
            )
        }
        declared[key] = tooling
    }

    return declared.values
        .onEach(::validateWinAppPackagePin)
        .sortedWith(
            compareBy<WinAppPackagePin, String>(String.CASE_INSENSITIVE_ORDER) { pin -> pin.name }
                .thenBy { pin -> pin.name },
        )
}

internal fun renderWinAppConfiguration(packages: Iterable<WinAppPackagePin>): String {
    val packageList = packages.toList()
    return buildString {
        if (packageList.isEmpty()) {
            appendLine("packages: []")
            return@buildString
        }
        appendLine("packages:")
        packageList.forEach { pkg ->
            validateWinAppPackagePin(pkg)
            appendLine("  - name: ${pkg.name}")
            appendLine("    version: ${pkg.version}")
        }
    }
}

private fun validateWinAppPackagePin(pin: WinAppPackagePin) {
    if (!NUGET_PACKAGE_ID.matches(pin.name)) {
        throw GradleException("NuGet package ID '${pin.name}' cannot be represented safely in winapp.yaml.")
    }
    if (!NUGET_EXACT_VERSION.matches(pin.version)) {
        throw GradleException(
            "NuGet package ${pin.name} must use an exact version that can be represented safely in winapp.yaml: " +
                "'${pin.version}'.",
        )
    }
}

private val NUGET_PACKAGE_ID = Regex("""[A-Za-z0-9_.-]+""")
private val NUGET_EXACT_VERSION = Regex("""[0-9]+(?:\.[0-9A-Za-z-]+)+(?:\+[0-9A-Za-z.-]+)?""")
