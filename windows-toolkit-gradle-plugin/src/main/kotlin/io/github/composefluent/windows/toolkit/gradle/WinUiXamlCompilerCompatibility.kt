package io.github.composefluent.windows.toolkit.gradle

import kotlinx.serialization.json.*
import io.github.composefluent.winrt.metadata.WinRTNuGetPackageResolver
import java.io.File

/** The selected WinUI NuGet package owns GenXbf; the compiler package declares its supported versions. */
internal fun validateXamlCompilerWinui(manifest: JsonObject, metadataReferences: Iterable<File>, genXbf: File, windowsAppSdk: String?) {
    val compatibility = manifest["compatibility"]?.jsonObject
        ?: error("The Kotlin XamlCompiler package must declare WinUI compatibility.")
    require(compatibility["genXbfHost"]?.jsonPrimitive?.content == "win-x64" &&
        File(genXbf, "x64/GenXbf.dll").isFile) {
        "Kotlin XamlCompiler requires the selected WinUI package's Windows x64 GenXbf.dll."
    }
    require(windowsAppSdk == null || windowsAppSdk in
        compatibility.getValue("windowsAppSdk").jsonArray.map { it.jsonPrimitive.content }) {
        "XamlCompiler ${manifest["version"]?.jsonPrimitive?.content} does not support Windows App SDK $windowsAppSdk."
    }
    val winui = metadataReferences.filter { it.name.equals("Microsoft.UI.Xaml.winmd", true) }
        .distinctBy { it.toPath().toAbsolutePath().normalize().toString().lowercase() }.singleOrNull()
        ?: error("Kotlin XAML requires one resolved Microsoft.UI.Xaml.winmd reference.")
    val packageId = compatibility.getValue("winuiPackage").jsonPrimitive.content
    fun findPackageRoot(file: File) = generateSequence(file) { it.parentFile }
        .firstOrNull { it.isDirectory && it.listFiles()?.any { file -> file.extension.equals("nuspec", true) } == true }
    val packageRoot = findPackageRoot(winui.parentFile)
        ?: error("Cannot determine the selected WinUI NuGet package identity for $winui.")
    val identity = WinRTNuGetPackageResolver.packageIdentity(packageRoot.toPath())
    val selectedId = identity.packageId
    val selectedVersion = identity.version
    val genXbfIdentity = findPackageRoot(genXbf)?.let { WinRTNuGetPackageResolver.packageIdentity(it.toPath()) }
    require(genXbfIdentity == identity) {
        "GenXbf must come from $selectedId $selectedVersion, matching the selected WinUI metadata."
    }
    require(selectedId.equals(packageId, true) && selectedVersion in
        compatibility.getValue("winuiVersions").jsonArray.map { it.jsonPrimitive.content }) {
        "XamlCompiler ${manifest["version"]?.jsonPrimitive?.content} does not support " +
            "$selectedId $selectedVersion; supported $packageId versions: ${compatibility["winuiVersions"]}."
    }
}
