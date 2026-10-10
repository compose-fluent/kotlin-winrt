[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^v\d+\.\d+\.\d+$')][string] $Tag,
    [Parameter(Mandatory)][ValidateSet('Verify', 'Publish')][string] $Mode,
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$modules = @(
    ':winrt-runtime', ':winrt-metadata', ':winrt-generator', ':winrt-authoring',
    ':winrt-compiler-plugin', ':winrt-compiler-plugin:compiler-kotlin-2-4-20',
    ':winrt-compiler-plugin:callsite-contract', ':winrt-compiler-plugin:callsite-lowering',
    ':winrt-compiler-plugin:callsite-lowering:lowering-kotlin-2-4-20'
)
$task = if ($Mode -eq 'Publish') { 'publishAndReleaseToMavenCentral' } else { 'publishToMavenLocal' }
$options = @("-Pwinrt.releaseTag=$Tag", '--no-daemon', '--no-configuration-cache', '--max-workers=1',
    '-Dorg.gradle.jvmargs=-Xmx8g -XX:+UseG1GC -Dfile.encoding=UTF-8')
if ($DryRun) { $options += '--dry-run' }

function Invoke-ReleaseGradle([string[]] $Tasks) {
    & (Join-Path $repository 'gradlew.bat') @Tasks @options
    if ($LASTEXITCODE -ne 0) { throw "Release $Mode failed (exit $LASTEXITCODE)." }
}

Push-Location $repository
try {
    Invoke-ReleaseGradle @($modules | ForEach-Object { "${_}:$task" })
    Invoke-ReleaseGradle @('-p', 'windows-toolkit-gradle-plugin', ":$task")

    # Check the dependent desktop projections before the full SDK matrix so
    # missing compile references fail as soon as the default SDK is available.
    $desktopProjectionTasks = @(
        ":winrt-projections:windows-webview2:$task", ":winrt-projections:windows-app-sdk:$task"
    )
    if ($Mode -eq 'Verify') {
        Invoke-ReleaseGradle $desktopProjectionTasks
    }

    # Projection versions retain their metadata baseline; dependencies use the tag.
    $properties = Get-Content -LiteralPath 'gradle.properties'
    $sdkVersions = (($properties | Where-Object { $_ -match '^kotlinWinRT.projections.windowsSdkVersions=' }) -split '=', 2)[1].Split(',')
    foreach ($sdk in $sdkVersions) {
        Invoke-ReleaseGradle @(
            ":winrt-projections:windows-sdk:$task", ":winrt-projections:windows-ui-xaml:$task",
            "-PkotlinWinRT.projections.windowsSdkVersion=$($sdk.Trim())"
        )
    }
    # WebView2 is a public dependency of the App SDK artifact and must be published too.
    if ($Mode -eq 'Publish') {
        Invoke-ReleaseGradle $desktopProjectionTasks
    }
} finally {
    Pop-Location
}
