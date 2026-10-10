[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('idea-262', 'as-261', 'as-canary-262')]
    [string] $Variant,
    [Parameter(Mandatory)]
    [string] $SdkVersion,
    [Parameter(Mandatory)]
    [ValidatePattern('^\d+\.\d+\.\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?$')]
    [string] $Version,
    [string] $LocalIdePath,
    [ValidatePattern('^v\d+\.\d+\.\d+$')]
    [string] $ReleaseTag,
    [string] $OutputDirectory
)

$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$ideProject = Join-Path $repository 'winrt-compiler-plugin/ide'
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $ideProject "build/variants/$Variant/release"
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

# Standalone ide-v releases retain their snapshot toolchain. A project release
# bundles and selects the exact same stable toolchain as Maven Central.
$previousRefType = $env:GITHUB_REF_TYPE
try {
    $env:GITHUB_REF_TYPE = 'branch'
    $arguments = @('-p', $ideProject, ':test', 'buildPlugin',
        'verifyPluginStructure', 'verifyPluginClassOwnership',
        "-PkotlinWinRT.ide.variant=$Variant", "-PkotlinWinRT.ide.sdkVersion=$SdkVersion",
        "-PkotlinWinRT.ide.version=$Version", '--project-cache-dir',
        (Join-Path $ideProject ".gradle/variants/$Variant"),
        '--no-daemon', '--no-configuration-cache', '--max-workers=2',
        '-Dorg.gradle.jvmargs=-Xmx3g -XX:+UseSerialGC -Dfile.encoding=UTF-8')
    if ($LocalIdePath) {
        $arguments += "-PkotlinWinRT.ide.path=$LocalIdePath"
    }
    if ($ReleaseTag) {
        if ($ReleaseTag -cne "v$Version") { throw 'Plugin and project release versions must match.' }
        $arguments += "-Pwinrt.releaseTag=$ReleaseTag"
        $arguments += "-PkotlinWinRT.ide.toolchainVersion=$Version"
    }
    & (Join-Path $repository 'gradlew.bat') @arguments
    if ($LASTEXITCODE -ne 0) { throw "IDE plugin validation failed (exit $LASTEXITCODE)." }
} finally {
    $env:GITHUB_REF_TYPE = $previousRefType
}

$archive = Join-Path $ideProject "build/variants/$Variant/distributions/kotlin-winrt-ide-$Version.zip"
if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
    throw "The plugin distribution is missing: $archive"
}

# Check the installed descriptor, not just the ZIP filename. A package built
# against the wrong SDK must never be labeled as another distribution.
$zip = [IO.Compression.ZipFile]::OpenRead($archive)
try {
    $mainJar = @($zip.Entries | Where-Object { $_.FullName.EndsWith("/lib/kotlin-winrt-ide-$Version.jar") })
    if ($mainJar.Count -ne 1) { throw 'Expected exactly one Kotlin WinRT UI JAR.' }
    $stream = $mainJar[0].Open()
    $memory = [IO.MemoryStream]::new()
    try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
    $memory.Position = 0
    $jar = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Read)
    try {
        $descriptor = $jar.GetEntry('META-INF/plugin.xml')
        if (-not $descriptor) { throw 'The installed plugin descriptor is missing.' }
        $reader = [IO.StreamReader]::new($descriptor.Open())
        try { [xml]$plugin = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $buildLine = $Variant.Split('-')[-1]
        if ($plugin.'idea-plugin'.id -cne 'io.github.composefluent.winrt.ide' -or
            $plugin.'idea-plugin'.version -cne $Version -or
            $plugin.'idea-plugin'.'idea-version'.'since-build' -cne $buildLine -or
            $plugin.'idea-plugin'.'idea-version'.'until-build' -cne "$buildLine.*") {
            throw 'The installed plugin ID, version or IDE baseline does not match the release.'
        }
        $androidAdapter = $jar.GetEntry('META-INF/kotlin-winrt-android-studio.xml')
        if (($Variant.StartsWith('as-')) -ne ($null -ne $androidAdapter)) {
            throw 'The Android Studio gallery adapter does not match this distribution.'
        }
        foreach ($resource in @('templates/toolchain.zip', 'templates/application-assets.zip')) {
            if (-not $jar.GetEntry($resource)) { throw "Missing bundled resource: $resource" }
        }
    } finally { $jar.Dispose(); $memory.Dispose() }
} finally { $zip.Dispose() }

$fileName = "kotlin-winrt-ide-$Version-$Variant.zip"
$destination = Join-Path $OutputDirectory $fileName
Copy-Item -LiteralPath $archive -Destination $destination -Force
$sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
"$sha256  $fileName" | Set-Content -LiteralPath (Join-Path $OutputDirectory "$fileName.sha256") -Encoding utf8NoBOM
$commit = & git -C $repository rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Could not identify the source commit.' }
[ordered]@{
    variant = $Variant
    sdkVersion = $SdkVersion
    pluginVersion = $Version
    commit = $commit
    file = $fileName
    sha256 = $sha256
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory "$Variant.json") -Encoding utf8NoBOM
Write-Host "Validated release package: $destination"
