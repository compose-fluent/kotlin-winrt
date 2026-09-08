import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyWinRTSampleIdentityTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val identityFile: RegularFileProperty

    @get:Input
    abstract val winuiEnabled: Property<Boolean>

    @get:Input
    abstract val windowsAppSdkVersion: Property<String>

    @TaskAction
    fun verify() {
        val identityJson = identityFile.get().asFile.readText()
        check("\"model\": \"application\"" in identityJson) {
            "Expected sample application identity JSON to use the application model."
        }
        check("winrt-projections" in identityJson) {
            "Expected sample application identity JSON to include the winrt-projections identity dependency."
        }
        check("winrt-runtime" !in identityJson) {
            "Runtime implementation dependencies must not be treated as Kotlin WinRT identity metadata."
        }
        if (!winuiEnabled.get()) {
            check("Microsoft.WindowsAppSDK" !in identityJson) {
                "WindowsAppSDK must not be declared when kotlinWinRT.samples.enableWinUI=false."
            }
        } else {
            val expectedPackage = "Microsoft.WindowsAppSDK@${windowsAppSdkVersion.get()}"
            check(expectedPackage in identityJson) {
                "Expected sample application identity JSON to include $expectedPackage."
            }
        }
    }
}

abstract class VerifyWinRTSampleRuntimeAssetsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeAssetsDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        check(runtimeAssetsDirectory.get().asFile.resolve("SimpleMathComponent.dll").isFile) {
            "Expected SimpleMathComponent.dll to be staged as a local WinRT component runtime asset."
        }
    }
}

val sampleWinUIEnabled = providers.gradleProperty("kotlinWinRT.samples.enableWinUI")
    .map(String::toBooleanStrict)
    .orElse(true)
val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRT.samples.windowsAppSdkVersion")
    .orElse("2.2.0")

val verifyWinRTSampleIdentity by tasks.registering(VerifyWinRTSampleIdentityTask::class) {
    group = "verification"
    description = "Verifies the sample application aggregates Kotlin WinRT identity metadata from projection dependencies."
    dependsOn("generateWinRTApplicationIdentityWinuiJvmMain")
    identityFile.set(
        layout.buildDirectory.file(
            "generated/kotlin-winrt/identity/variant-WinuiJvmMain/kotlin-winrt-application.json",
        ),
    )
    winuiEnabled.set(sampleWinUIEnabled)
    windowsAppSdkVersion.set(sampleWindowsAppSdkVersion)
}

val verifyWinRTSampleRuntimeAssets by tasks.registering(VerifyWinRTSampleRuntimeAssetsTask::class) {
    group = "verification"
    description = "Verifies the sample application stages local WinRT component runtime assets."
    dependsOn("stageWinRTRuntimeAssetsWinuiJvmMain")
    runtimeAssetsDirectory.set(layout.buildDirectory.dir("kotlin-winrt/application-layout/winuiJvm_main/runtime-assets"))
}

val verifyWinRTSampleRun by tasks.registering {
    group = "verification"
    description = "Runs the sample application through the native Kotlin/WinRT host without opt-in native WinRT smoke tests."
    dependsOn("runWinRTApplicationHostWinuiJvmMain")
}

val verifyWinRTSampleMingwRun by tasks.registering {
    group = "verification"
    description = "Runs the sample application through the mingwX64 executable."
    dependsOn("runReleaseExecutableMingwX64")
}

val validateWinRTSampleIntegration by tasks.registering {
    group = "verification"
    description = "Runs the sample-specific identity, asset, and JVM/native smoke checks."
    dependsOn("verifyWinRTSampleMode")
    dependsOn(verifyWinRTSampleIdentity)
    dependsOn(verifyWinRTSampleRuntimeAssets)
    dependsOn(verifyWinRTSampleRun)
    dependsOn(verifyWinRTSampleMingwRun)
}
