package io.github.composefluent.winrt.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateWinRTMingwApplicationEntryTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val legacyOutputDirectories: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val mainClass: Property<String>

    @get:Input
    abstract val packageType: Property<String>

    @get:Input
    abstract val windowsAppSdkDeployment: Property<String>

    @get:Input
    abstract val entryPointFunctionName: Property<String>

    @get:Internal
    val entryPoint: Provider<String>
        get() = entryPointFunctionName.map { "io.github.composefluent.winrt.application.$it" }

    init {
        entryPointFunctionName.convention("main")
        packageType.convention(WindowsPackageType.Packaged.name)
        windowsAppSdkDeployment.convention(WinRTWindowsAppSdkDeployment.FrameworkDependent.name)
    }

    @TaskAction
    fun generate() {
        val outputRoot = outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        legacyOutputDirectories.files
            .map { it.toPath().toAbsolutePath().normalize() }
            .filterNot(outputRoot::equals)
            .forEach(GradleFileOperations::deleteDirectory)
        GradleFileOperations.cleanDirectory(outputRoot)
        val mainClassValue = mainClass.orNull.orEmpty()
        if (mainClassValue.isBlank()) {
            return
        }
        val mainFunction = nativeMainFunctionName(mainClassValue)
        val unpackaged = packageType.get() == WindowsPackageType.None.name
        mingwApplicationEntrySource(
            mainFunctionName = mainFunction,
            unpackaged = unpackaged,
            entryFunctionName = entryPointFunctionName.get(),
            windowsAppSdkDeployment = windowsAppSdkDeployment.get(),
        ).writeTo(outputRoot)
    }
}

internal const val KOTLIN_WINRT_MINGW_APPLICATION_ENTRY_POINT: String =
    "io.github.composefluent.winrt.application.main"

private fun nativeMainFunctionName(mainClass: String): String {
    val normalized = mainClass.trim()
    if (normalized.isBlank()) {
        throw GradleException("Kotlin/WinRT mingw application entry requires an application mainClass.")
    }
    if (normalized.endsWith(".MainKt")) {
        return normalized.removeSuffix(".MainKt") + ".main"
    }
    return normalized
}

private fun mingwApplicationEntrySource(
    mainFunctionName: String,
    unpackaged: Boolean,
    entryFunctionName: String,
    windowsAppSdkDeployment: String = WinRTWindowsAppSdkDeployment.FrameworkDependent.name,
): FileSpec {
    val userMainPackage = mainFunctionName.substringBeforeLast('.', missingDelimiterValue = "")
    val userMainName = mainFunctionName.substringAfterLast('.')
    if (userMainPackage.isBlank() || userMainName.isBlank()) {
        throw GradleException(
            "Kotlin/WinRT mingw application main '$mainFunctionName' must be a fully qualified top-level function.",
        )
    }
    val userMain = MemberName(userMainPackage, userMainName)
    val bootstrap = ClassName("io.github.composefluent.winrt.runtime", "WinRTWindowsAppSdkBootstrap")
    val hostConfiguration = ClassName("io.github.composefluent.winrt.runtime", "WinRTApplicationHostConfiguration")
    val packageIdentity = ClassName("io.github.composefluent.winrt.runtime", "WinRTApplicationPackageIdentity")
    val deploymentMode = ClassName("io.github.composefluent.winrt.runtime", "WinRTWindowsAppSdkDeploymentMode")
    val deployment = ClassName("io.github.composefluent.winrt.runtime", "WinRTWindowsAppSdkDeployment")
    val packageIdentityName = if (unpackaged) "Unpackaged" else "Packaged"
    val fileName = if (entryFunctionName == "main") "WinRTMingwApplicationEntry" else "WinRTMingwApplicationEntry_$entryFunctionName"
    return FileSpec.builder("io.github.composefluent.winrt.application", fileName)
        .addAliasedImport(userMain, "userMain")
        .addFunction(
            FunSpec.builder(entryFunctionName)
                .beginControlFlow(
                    "%T.initializeApplicationHost(%T.fromStagedRuntimeAssets(packageIdentity = %T.%L, windowsAppSdkDeployment = %T.%L, runtimeAssetsRoot = %T.discoverRuntimeAssetsRoot())).use",
                    bootstrap,
                    hostConfiguration,
                    packageIdentity,
                    packageIdentityName,
                    deploymentMode,
                    windowsAppSdkDeployment,
                    deployment,
                )
                .addStatement("%M()", userMain)
                .endControlFlow()
                .build(),
        )
        .build()
}
