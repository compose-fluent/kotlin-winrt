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
        check(componentDllFile.isFile) {
            "Expected native authored component DLL: $componentDllFile"
        }
        check(authoredWinmdFile.isFile) {
            "Expected native authored WinMD: $authoredWinmdFile"
        }
        check(authoredHostManifestFile.isFile) {
            "Expected native authored host manifest: $authoredHostManifestFile"
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
