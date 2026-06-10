plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("io.github.composefluent.winrt")
}

val projectionWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion")
    .orElse("2.1.3")
val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionIncludeWinAppSdk = providers.gradleProperty("kotlinWinRt.projections.includeWinAppSdk")
    .map(String::toBooleanStrict)
    .orElse(false)
val projectionIncludeFullWindowsSdk = providers.gradleProperty("kotlinWinRt.projections.includeFullWindowsSdk")
    .map(String::toBooleanStrict)
    .orElse(false)
val fullWindowsSdkProjectionGateRequested = providers.provider {
    gradle.startParameter.taskNames.any { taskName ->
        taskName == "validateWinRtFullWindowsSdkProjectionGate" ||
            taskName.endsWith(":validateWinRtFullWindowsSdkProjectionGate")
    }
}
val projectionUseFullWindowsSdk = projectionIncludeFullWindowsSdk
    .zip(fullWindowsSdkProjectionGateRequested) { propertyEnabled, gateRequested ->
        propertyEnabled || gateRequested
    }

kotlin {
    jvm()
    mingwX64()
}

val generatedWinRtProjectionSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/main/kotlin")

val auditGeneratedWinRtProjectionOutput by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fails if generated projection source leaks fallback invocation or JVM-only reflection paths."
    dependsOn("generateWinRtProjections")
    inputs.dir(generatedWinRtProjectionSources)

    val rootPath = generatedWinRtProjectionSources.get().asFile.absolutePath
    val escapedRoot = rootPath.replace("'", "''")
    val script = """
        ${'$'}ErrorActionPreference = 'Stop'
        ${'$'}root = '$escapedRoot'
        ${'$'}patterns = @(
          'ComVtableInvoker',
          'invokeGenericArgs',
          'Class.forName',
          'Proxy.newProxyInstance',
          'java.lang.reflect',
          'import java.'
        )
        ${'$'}violations = New-Object System.Collections.Generic.List[string]
        Get-ChildItem -Path ${'$'}root -Recurse -File -Filter '*.kt' | ForEach-Object {
          ${'$'}file = ${'$'}_
          ${'$'}lineNumber = 0
          Get-Content -LiteralPath ${'$'}file.FullName | ForEach-Object {
            ${'$'}lineNumber += 1
            ${'$'}line = ${'$'}_
            foreach (${'$'}pattern in ${'$'}patterns) {
              if (${'$'}line.Contains(${'$'}pattern)) {
                ${'$'}relative = [System.IO.Path]::GetRelativePath(${'$'}root, ${'$'}file.FullName).Replace('\', '/')
                ${'$'}violations.Add(('{0}:{1}: {2}: {3}' -f ${'$'}relative, ${'$'}lineNumber, ${'$'}pattern, ${'$'}line.Trim()))
                break
              }
            }
          }
        }
        if (${'$'}violations.Count -gt 0) {
          Write-Error 'Generated WinRT projection output contains forbidden fallback or JVM-only paths.'
          ${'$'}violations | Select-Object -First 50 | ForEach-Object { Write-Error ${'$'}_ }
          exit 1
        }
    """.trimIndent()
    commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
}

tasks.named("check") {
    dependsOn(auditGeneratedWinRtProjectionOutput)
}

winRt {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
    if (projectionIncludeWinAppSdk.get()) projectionWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion) {
            generateProjection = true
        }
    }

    if (projectionUseFullWindowsSdk.get()) {
        namespace("Windows")
        excludeNamespace("Windows.UI.Xaml")
        excludeNamespace("Windows.ApplicationModel.Store.Preview")
        excludeType("Windows.UI.Colors")
        excludeType("Windows.UI.IColors")
        excludeType("Windows.UI.ColorHelper")
        excludeType("Windows.UI.IColorHelper")
        excludeType("Windows.UI.IColorHelperStatics")
        excludeType("Windows.UI.IColorHelperStatics2")
    } else {
        namespace("Windows.Foundation")
        namespace("Windows.Foundation.Collections")
        namespace("Windows.Data.Json")
        namespace("Windows.System")
        namespace("Windows.ApplicationModel.DataTransfer")
        namespace("Windows.System.Display")
        namespace("Windows.UI.ViewManagement")
        namespace("Windows.UI.Xaml.Interop")
    }
    if (projectionIncludeWinAppSdk.get()) {
        excludeNamespace("Windows.UI.Composition")
        excludeType("Windows.UI.Composition")
        namespace("Microsoft.UI.Dispatching")
        namespace("Microsoft.UI.Windowing")
        namespace("Microsoft.UI.Xaml")
        namespace("Microsoft.UI.Xaml.Automation")
        namespace("Microsoft.UI.Xaml.Automation.Peers")
        namespace("Microsoft.UI.Xaml.Controls")
        namespace("Microsoft.UI.Xaml.Media")
    }
    namespace("SimpleMathComponent")
    winmd(
        providers.gradleProperty("kotlinWinRt.samples.simpleMathWinmd")
            .getOrElse(layout.projectDirectory.file("src/main/winrt/SimpleMathComponent.winmd").asFile.absolutePath),
    )
    runtimeAsset(layout.projectDirectory.file("src/main/winrt/SimpleMathComponent.dll").asFile.absolutePath)
    type("Windows.Foundation.IStringable")
    type("Windows.Storage.ApplicationData")
    type("Windows.Storage.KnownFolders")
}
