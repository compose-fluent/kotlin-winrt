package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyWinRtNativeAuthoringComponentFixtureTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val componentDll: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredWinmd: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredHostManifest: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val identityFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val activationFactoryPlanSource: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val serverActivationFactoriesSource: RegularFileProperty

    @get:Input
    abstract val runtimeClassNames: ListProperty<String>

    @get:Input
    abstract val expectedDllName: Property<String>

    @get:Input
    @get:Optional
    abstract val forbiddenDllName: Property<String>

    @TaskAction
    fun verify() {
        val componentDllFile = componentDll.get().asFile
        val authoredWinmdFile = authoredWinmd.get().asFile
        val authoredHostManifestFile = authoredHostManifest.get().asFile
        val identity = identityFile.get().asFile
        val activationFactoryPlan = activationFactoryPlanSource.get().asFile
        val serverActivationFactories = serverActivationFactoriesSource.get().asFile
        check(componentDllFile.isFile) {
            "Expected native authored component DLL: $componentDllFile"
        }
        check(authoredWinmdFile.isFile) {
            "Expected native authored WinMD: $authoredWinmdFile"
        }
        check(authoredHostManifestFile.isFile) {
            "Expected native authored host manifest: $authoredHostManifestFile"
        }
        check(activationFactoryPlan.isFile) {
            "Expected generated native authored activation factory plan: $activationFactoryPlan"
        }
        check(serverActivationFactories.isFile) {
            "Expected generated native authored server activation factories: $serverActivationFactories"
        }

        val hostManifestText = authoredHostManifestFile.readText()
        runtimeClassNames.get().forEach { runtimeClassName ->
            check(hostManifestText.contains(runtimeClassName)) {
                "Expected authored runtime class '$runtimeClassName' in native host manifest: $hostManifestText"
            }
        }
        check(hostManifestText.contains(expectedDllName.get())) {
            "Expected native host manifest to name the actual mingw shared library: $hostManifestText"
        }
        forbiddenDllName.orNull?.let { forbidden ->
            check(!hostManifestText.contains(forbidden)) {
                "Native host manifest must not use forbidden DLL name '$forbidden': $hostManifestText"
            }
        }

        val identityText = identity.readText()
        check(identityText.contains(componentDllFile.absolutePath.replace("\\", "\\\\"))) {
            "Expected native component DLL in identity authoredTargetArtifacts: $identityText"
        }
        check(identityText.contains(authoredWinmdFile.absolutePath.replace("\\", "\\\\"))) {
            "Expected native authored WinMD in identity: $identityText"
        }
        check(identityText.contains(authoredHostManifestFile.absolutePath.replace("\\", "\\\\"))) {
            "Expected native authored host manifest in identity: $identityText"
        }

        val activationFactoryPlanText = activationFactoryPlan.readText()
        val serverActivationFactoriesText = serverActivationFactories.readText()
        runtimeClassNames.get().forEach { runtimeClassName ->
            val projectedTypeEntry = activationFactoryPlanText.substringAfter(
                "projectedTypeName = \"$runtimeClassName\"",
                missingDelimiterValue = "",
            )
            check(projectedTypeEntry.isNotEmpty()) {
                "Expected generated activation factory plan for '$runtimeClassName': $activationFactoryPlanText"
            }
            val entry = projectedTypeEntry.substringBefore("AuthoringActivationFactoryEntry(")
            check(entry.contains("isActivatable = true")) {
                "Expected native authored runtime class '$runtimeClassName' to expose default activation: $entry"
            }
            check(entry.contains("activateInstanceBehavior = \"newProjectedInstanceToMarshalInspectable\"")) {
                "Expected native authored runtime class '$runtimeClassName' to create a projected instance from activateInstance: $entry"
            }
            check(entry.contains("runClassConstructorTypeName = \"$runtimeClassName\"")) {
                "Expected native authored runtime class '$runtimeClassName' constructor target in activation plan: $entry"
            }

            val kotlinClassName = runtimeClassName.substringAfterLast('.')
            check(serverActivationFactoriesText.contains("$kotlinClassName()")) {
                "Expected native server activation factory to instantiate '$kotlinClassName': $serverActivationFactoriesText"
            }
        }
        check(serverActivationFactoriesText.contains("ComWrappersSupport.createCCWForObject")) {
            "Expected native server activation factories to marshal activated objects through ComWrappersSupport: $serverActivationFactoriesText"
        }
        check(!serverActivationFactoriesText.contains("does not expose default activation")) {
            "Native component fixture default activation must not fall back to notImplemented server factories: $serverActivationFactoriesText"
        }
    }
}

abstract class VerifyWinRtNativeAuthoringConsumerFixtureTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeAssetsRoot: DirectoryProperty

    @get:Input
    abstract val expectedDllName: Property<String>

    @get:Input
    abstract val expectedWinmdName: Property<String>

    @get:Input
    abstract val expectedHostManifestName: Property<String>

    @get:Input
    abstract val runtimeClassNames: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val forbiddenJarName: Property<String>

    @TaskAction
    fun verify() {
        val root = runtimeAssetsRoot.get().asFile
        val stagedDll = root.resolve(expectedDllName.get())
        val stagedWinmd = root.resolve(expectedWinmdName.get())
        val stagedHostManifest = root.resolve(expectedHostManifestName.get())
        check(stagedDll.isFile) {
            "Expected dependency native authored DLL to be staged: $stagedDll"
        }
        check(stagedWinmd.isFile) {
            "Expected dependency native authored WinMD to be staged: $stagedWinmd"
        }
        check(stagedHostManifest.isFile) {
            "Expected dependency native authored host manifest to be staged: $stagedHostManifest"
        }
        val hostManifestText = stagedHostManifest.readText()
        runtimeClassNames.get().forEach { runtimeClassName ->
            check(hostManifestText.contains(runtimeClassName)) {
                "Expected staged host manifest to preserve authored class mapping for '$runtimeClassName': $hostManifestText"
            }
        }
        check(hostManifestText.contains(expectedDllName.get())) {
            "Expected staged host manifest to target the native authored DLL: $hostManifestText"
        }
        forbiddenJarName.orNull?.let { forbidden ->
            check(!root.resolve(forbidden).exists()) {
                "Native authored dependency staging must not require the JVM jar host artifact."
            }
        }
    }
}
