package io.github.composefluent.windows.toolkit.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class UWPDevelopmentRunnerTest {
    @Test fun powershell_preserves_activation_arguments_and_installs_frameworks_before_registration() {
        // SDK IApplicationActivationManager::ActivateApplication takes a single LPCWSTR. Its
        // contents must survive PowerShell's -File binding without becoming script parameters.
        assumeTrue(isWindowsHost())
        val root = Files.createTempDirectory("uwp-runner-arguments-")
        val runner = root.resolve("run-uwp.ps1")
        Files.writeString(runner, uwpDevelopmentRunnerScript)
        Files.writeString(root.resolve("AppxManifest.xml"), """
            <Package><Identity Name="Example.UWP" Publisher="CN=Example" />
              <Applications><Application Id="App" /></Applications></Package>
        """.trimIndent())
        val harness = root.resolve("harness.ps1")
        Files.writeString(harness, harnessScript)
        val framework = root.resolve("framework with spaces.appx").toString()
        for (arguments in listOf("", "-NoLaunch", "--name \"two words\"", "中文 --path \"C:\\目录\\\"", "first\nsecond")) {
            Files.writeString(root.resolve("run-uwp.json"), buildJsonObject {
                put("applicationArguments", arguments)
                put("dependencyPackages", JsonArray(listOf(buildJsonObject {
                    put("path", framework)
                    put("name", "Example.Framework")
                    put("publisher", "CN=Framework")
                    put("version", "1.0.0.0")
                    put("architecture", "x64")
                })))
            }.toString())
            runPowerShell(harness)
            val result = Json.parseToJsonElement(Files.readString(root.resolve("result.json"))).jsonObject
            assertEquals(arguments, result.getValue("arguments").jsonPrimitive.content)
            assertEquals("Example.UWP_family!App", result.getValue("applicationId").jsonPrimitive.content)
            assertEquals("", result.getValue("failure").jsonPrimitive.content)
            assertEquals(listOf("install:$framework", "remove-preserving-data", "register"),
                result.getValue("operations").jsonArray.map { it.jsonPrimitive.content })
        }
        runPowerShell(harness, "-FrameworkInstalled")
        val reused = Json.parseToJsonElement(Files.readString(root.resolve("result.json"))).jsonObject
        assertEquals(listOf("remove-preserving-data", "register"), reused.getValue("operations").jsonArray.map { it.jsonPrimitive.content })
        runPowerShell(harness, "-StopOnly")
        val stopped = Json.parseToJsonElement(Files.readString(root.resolve("result.json"))).jsonObject
        assertEquals("Example.UWP_full", stopped.getValue("stoppedPackage").jsonPrimitive.content)
        assertTrue(stopped.getValue("operations").jsonArray.isEmpty())
        val originalManifest = Files.readString(root.resolve("AppxManifest.xml"))
        Files.writeString(root.resolve("AppxManifest.xml"), "$originalManifest\n<!-- updated extensions -->")
        runPowerShell(harness, "-NonDevelopment")
        val rejectedRegistration = Json.parseToJsonElement(Files.readString(root.resolve("result.json"))).jsonObject
        assertTrue(rejectedRegistration.getValue("failure").jsonPrimitive.content.contains("Refusing to replace non-development package"))
        assertTrue(rejectedRegistration.getValue("operations").jsonArray.isEmpty())
        runPowerShell(harness, "-FailRegistration")
        val restored = Json.parseToJsonElement(Files.readString(root.resolve("result.json"))).jsonObject
        assertTrue(restored.getValue("failure").jsonPrimitive.content.contains("registration failed"))
        assertEquals(listOf("install:$framework", "remove-preserving-data", "register", "register"),
            restored.getValue("operations").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("", restored.getValue("applicationId").jsonPrimitive.content)
        assertEquals(originalManifest, Files.readString(root.resolve("AppxManifest.xml")))
        runPowerShell(harness, "-StaleLocation")
        val stale = Json.parseToJsonElement(Files.readString(root.resolve("result.json"))).jsonObject
        assertTrue(stale.getValue("failure").jsonPrimitive.content.contains("Package registration retained"))
        assertEquals("", stale.getValue("applicationId").jsonPrimitive.content)
        runPowerShell(harness, "-ApplicationExitCode", "0")
        runPowerShell(harness, "-ApplicationExitCode", "23", expectedExitCode = 23)
    }

    private fun runPowerShell(harness: Path, vararg extraArguments: String, expectedExitCode: Int = 0) {
        val process = ProcessBuilder(listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", harness.toString()) + extraArguments).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, expectedExitCode, process.waitFor())
    }

    private val harnessScript = """
        param([switch]${'$'}StopOnly, [switch]${'$'}FrameworkInstalled, [switch]${'$'}NonDevelopment, [switch]${'$'}FailRegistration, [switch]${'$'}StaleLocation, [int]${'$'}ApplicationExitCode = -1)
        ${'$'}ErrorActionPreference = 'Stop'
        Add-Type -TypeDefinition @'
        namespace KotlinWinRT.UWP {
            public static class Launcher {
                public static string Arguments = "";
                public static string ApplicationId = "";
                public static string StoppedPackage = "";
                public static int ExitCode = -1;
                public static uint Activate(string id, string arguments) {
                    ApplicationId = id; Arguments = arguments;
                    if (ExitCode >= 0) {
                        using (var process = System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo {
                            FileName = "powershell.exe", UseShellExecute = false, CreateNoWindow = true,
                            Arguments = "-NoProfile -Command \"Start-Sleep -Milliseconds 300; exit " + ExitCode + "\""
                        })) { return (uint)process.Id; }
                    }
                    return 1;
                }
                public static void Terminate(string fullName) { StoppedPackage = fullName; }
            }
        }
'@
        ${'$'}global:operations = New-Object 'System.Collections.Generic.List[string]'
        ${'$'}global:frameworkInstalled = ${'$'}FrameworkInstalled
        ${'$'}global:nonDevelopment = ${'$'}NonDevelopment
        ${'$'}global:failRegistration = ${'$'}FailRegistration
        ${'$'}global:installLocation = ${'$'}PSScriptRoot
        if (${'$'}StaleLocation) {
            ${'$'}global:installLocation = Join-Path ${'$'}PSScriptRoot 'previous-variant'
            [IO.Directory]::CreateDirectory(${'$'}global:installLocation) | Out-Null
            Copy-Item -LiteralPath (Join-Path ${'$'}PSScriptRoot 'AppxManifest.xml') -Destination (Join-Path ${'$'}global:installLocation 'AppxManifest.xml')
        }
        function Add-Type { param(${'$'}TypeDefinition) }
        function Add-AppxPackage {
            param([string]${'$'}Path, [switch]${'$'}Register, [switch]${'$'}ForceApplicationShutdown)
            if (${'$'}Register) {
                ${'$'}global:operations.Add('register')
                if (${'$'}global:failRegistration) {
                    ${'$'}global:failRegistration = ${'$'}false
                    throw 'registration failed'
                }
            }
            else { ${'$'}global:operations.Add('install:' + ${'$'}Path) }
        }
        function Remove-AppxPackage {
            param([string]${'$'}Package, [switch]${'$'}PreserveApplicationData)
            if (${'$'}Package -ne 'Example.UWP_full' -or -not ${'$'}PreserveApplicationData) { throw 'Unexpected package removal' }
            ${'$'}global:operations.Add('remove-preserving-data')
        }
        function Get-AppxPackage {
            param([string]${'$'}Name)
            if (${'$'}Name -eq 'Example.Framework') {
                if (${'$'}global:frameworkInstalled) {
                    [pscustomobject]@{ Publisher = 'CN=Framework'; Architecture = 'X64'; Version = '2.0.0.0' }
                }
                return
            }
            [pscustomobject]@{ Name = 'Example.UWP'; Publisher = 'CN=Example'; PackageFamilyName = 'Example.UWP_family'; PackageFullName = 'Example.UWP_full'; IsDevelopmentMode = -not ${'$'}global:nonDevelopment; InstallLocation = ${'$'}global:installLocation }
            [pscustomobject]@{ Name = 'Example.UWP'; Publisher = 'CN=Other'; PackageFamilyName = 'Other_family'; PackageFullName = 'Other_full' }
        }
        ${'$'}failure = ''
        [KotlinWinRT.UWP.Launcher]::ExitCode = ${'$'}ApplicationExitCode
        ${'$'}runnerExitCode = 0
        try {
            & (Join-Path ${'$'}PSScriptRoot 'run-uwp.ps1') -Manifest (Join-Path ${'$'}PSScriptRoot 'AppxManifest.xml') -Configuration (Join-Path ${'$'}PSScriptRoot 'run-uwp.json') -Detach:(${'$'}ApplicationExitCode -lt 0) -StopOnly:${'$'}StopOnly
            ${'$'}runnerExitCode = ${'$'}LASTEXITCODE
        } catch { ${'$'}failure = ${'$'}_.ToString() }
        ${'$'}result = [ordered]@{
            arguments = [KotlinWinRT.UWP.Launcher]::Arguments
            applicationId = [KotlinWinRT.UWP.Launcher]::ApplicationId
            stoppedPackage = [KotlinWinRT.UWP.Launcher]::StoppedPackage
            operations = @(${'$'}global:operations.ToArray())
            failure = ${'$'}failure
        } | ConvertTo-Json -Compress
        [IO.File]::WriteAllText((Join-Path ${'$'}PSScriptRoot 'result.json'), ${'$'}result)
        if (${'$'}ApplicationExitCode -ge 0) { exit ${'$'}runnerExitCode }
    """.trimIndent()
}
