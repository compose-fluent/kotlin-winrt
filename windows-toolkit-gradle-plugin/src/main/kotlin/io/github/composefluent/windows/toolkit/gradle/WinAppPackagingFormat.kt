package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.runtime.WinAppPackageIdentity

internal fun WindowsPackageType.hostPackageIdentity(): WinAppPackageIdentity =
    if (this == WindowsPackageType.None) WinAppPackageIdentity.Unpackaged else WinAppPackageIdentity.Packaged

/** Packaging choices share compilation, authoring hosts, launchers, and runtime images. */
internal enum class WinAppPackagingFormat(
    val packageType: WindowsPackageType,
    val deployment: WindowsAppSdkDeployment,
    val directoryName: String,
) {
    PackagedFrameworkDependent(WindowsPackageType.Packaged, WindowsAppSdkDeployment.FrameworkDependent, "packaged-framework-dependent"),
    PackagedSelfContained(WindowsPackageType.Packaged, WindowsAppSdkDeployment.SelfContained, "packaged-self-contained"),
    UnpackagedFrameworkDependent(WindowsPackageType.None, WindowsAppSdkDeployment.FrameworkDependent, "unpackaged-framework-dependent"),
    UnpackagedSelfContained(WindowsPackageType.None, WindowsAppSdkDeployment.SelfContained, "unpackaged-self-contained"),
}
