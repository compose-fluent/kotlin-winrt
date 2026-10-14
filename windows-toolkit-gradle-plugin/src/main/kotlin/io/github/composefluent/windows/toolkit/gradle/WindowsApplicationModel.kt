package io.github.composefluent.windows.toolkit.gradle

/** The build and runtime use one application-model contract. AppX itself is only a package format. */
typealias WindowsApplicationModel = io.github.composefluent.winrt.runtime.WinAppApplicationModel

internal fun validateWindowsApplicationModel(
    model: WindowsApplicationModel,
    packageType: WindowsPackageType,
    deployment: WindowsAppSdkDeployment,
    framework: io.github.composefluent.winrt.runtime.XamlFramework,
    jvmRuntimeMode: WinAppJvmRuntimeMode = WinAppJvmRuntimeMode.Bundled,
) {
    if (model != WindowsApplicationModel.UWP) return
    require(packageType == WindowsPackageType.Packaged) { "UWP applications require AppX/MSIX package identity." }
    require(deployment == WindowsAppSdkDeployment.None) { "UWP applications use the Windows runtime and cannot bootstrap Windows App SDK." }
    require(framework == io.github.composefluent.winrt.runtime.XamlFramework.UWP) { "UWP applications require windows.xaml.framework = XamlFramework.UWP." }
    require(jvmRuntimeMode == WinAppJvmRuntimeMode.Bundled) { "UWP AppContainer applications require a bundled JVM runtime image." }
}
