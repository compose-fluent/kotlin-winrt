package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path

/** Returns true for the Windows App SDK package and its split runtime/WinUI packages. */
internal fun isWindowsAppSdkPackageId(packageId: String): Boolean {
    val normalized = packageId.trim()
    return normalized.equals("Microsoft.WindowsAppSDK", ignoreCase = true) ||
        normalized.startsWith("Microsoft.WindowsAppSDK.", ignoreCase = true)
}

internal fun containsWindowsAppSdkPackage(packageSpecs: Iterable<String>): Boolean =
    packageSpecs.any { spec ->
        isWindowsAppSdkPackageId(parseNuGetPackageIdentity(spec).normalizedPackageId)
    }

/**
 * Resolves the public Gradle deployment setting to the concrete mode consumed by application
 * tasks and generated hosts. Auto deliberately has no runtime representation.
 */
internal fun resolveWindowsAppSdkDeployment(
    requested: WinRTWindowsAppSdkDeployment,
    packageSpecs: Iterable<String>,
    frameworkDependentAvailable: Boolean,
): WinRTWindowsAppSdkDeployment = when {
    requested != WinRTWindowsAppSdkDeployment.Auto -> requested
    !containsWindowsAppSdkPackage(packageSpecs) -> WinRTWindowsAppSdkDeployment.None
    frameworkDependentAvailable -> WinRTWindowsAppSdkDeployment.FrameworkDependent
    else -> WinRTWindowsAppSdkDeployment.SelfContained
}

/**
 * Framework-dependent hosting needs a Bootstrap DLL supplied by WinApp restore or an explicit
 * runtime asset. When restore is enabled the restore task is the producer of that asset, so the
 * mode can be selected before the task executes; disabled restore requires an existing asset.
 */
internal fun frameworkDependentDeploymentAvailable(
    restoreEnabled: Boolean,
    explicitRuntimeAssets: Iterable<Path> = emptyList(),
): Boolean {
    if (restoreEnabled) {
        return true
    }
    // A disabled restore task clears its output. Only explicit assets are authoritative in that
    // mode; a stale .winapp directory must not make Auto select FrameworkDependent.
    return explicitRuntimeAssets.any(::containsWindowsAppSdkBootstrap)
}

private fun containsWindowsAppSdkBootstrap(root: Path): Boolean {
    if (Files.isRegularFile(root)) {
        return root.fileName.toString().equals("Microsoft.WindowsAppRuntime.Bootstrap.dll", ignoreCase = true)
    }
    if (!Files.isDirectory(root)) {
        return false
    }
    return Files.walk(root).use { paths ->
        paths.anyMatch { path ->
            Files.isRegularFile(path) &&
                path.fileName.toString().equals("Microsoft.WindowsAppRuntime.Bootstrap.dll", ignoreCase = true)
        }
    }
}
