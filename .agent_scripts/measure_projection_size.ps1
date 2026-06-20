param(
    [Parameter(Mandatory = $true)]
    [string] $BaselineJar,

    [Parameter(Mandatory = $true)]
    [string] $OptimizedJar,

    [string] $GeneratedSourceRoot = "",

    [switch] $Json
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Measure-Jar {
    param([Parameter(Mandatory = $true)][string] $Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Jar does not exist: $Path"
    }

    $file = Get-Item -LiteralPath $Path
    $zip = [System.IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        $classEntries = @($zip.Entries | Where-Object { $_.FullName.EndsWith(".class") })
        $supportEntries = @($classEntries | Where-Object { $_.FullName -match "WinRTModulePlatformAbiCall" })
        $compressedClassBytes = ($classEntries | Measure-Object -Property CompressedLength -Sum).Sum
        $uncompressedClassBytes = ($classEntries | Measure-Object -Property Length -Sum).Sum
        $supportCompressedBytes = ($supportEntries | Measure-Object -Property CompressedLength -Sum).Sum
        $supportUncompressedBytes = ($supportEntries | Measure-Object -Property Length -Sum).Sum

        [pscustomobject]@{
            Path = $file.FullName
            TotalBytes = [int64] $file.Length
            TotalMiB = [math]::Round($file.Length / 1MB, 2)
            ClassCount = $classEntries.Count
            CompressedClassBytes = [int64] $compressedClassBytes
            CompressedClassMiB = [math]::Round($compressedClassBytes / 1MB, 2)
            UncompressedClassBytes = [int64] $uncompressedClassBytes
            UncompressedClassMiB = [math]::Round($uncompressedClassBytes / 1MB, 2)
            ModuleAbiSupportClassCount = $supportEntries.Count
            ModuleAbiSupportCompressedBytes = [int64] ($supportCompressedBytes ?? 0)
            ModuleAbiSupportUncompressedBytes = [int64] ($supportUncompressedBytes ?? 0)
            ModuleAbiSupportClasses = @($supportEntries | ForEach-Object { $_.FullName } | Sort-Object)
        }
    } finally {
        $zip.Dispose()
    }
}

function Count-Pattern {
    param(
        [Parameter(Mandatory = $true)][string[]] $Files,
        [Parameter(Mandatory = $true)][string] $Pattern
    )

    if ($Files.Count -eq 0) {
        return 0
    }

    $matches = Select-String -LiteralPath $Files -Pattern $Pattern -AllMatches -ErrorAction SilentlyContinue
    if ($null -eq $matches) {
        return 0
    }
    return (($matches | ForEach-Object { $_.Matches.Count }) | Measure-Object -Sum).Sum
}

function Measure-GeneratedSource {
    param([Parameter(Mandatory = $true)][string] $Root)

    if ([string]::IsNullOrWhiteSpace($Root) -or -not (Test-Path -LiteralPath $Root)) {
        return $null
    }

    $files = @(Get-ChildItem -LiteralPath $Root -Recurse -File -Filter "*.kt" | ForEach-Object { $_.FullName })
    $supportFiles = @($files | Where-Object { [System.IO.Path]::GetFileName($_) -match "^WinRTModulePlatformAbiCall" })
    $projectionFiles = @($files | Where-Object { [System.IO.Path]::GetFileName($_) -notmatch "^WinRTModulePlatformAbiCall" })
    $supportContents = $supportFiles | ForEach-Object { Get-Content -LiteralPath $_ -Raw }
    $supportObjectNames = @(
        foreach ($contents in $supportContents) {
            [regex]::Matches($contents, "internal\s+(?:actual\s+)?object\s+(WinRTModulePlatformAbiCall_[A-Za-z0-9_]+)") |
                ForEach-Object { $_.Groups[1].Value }
        }
    )
    $functionShapes = @(
        foreach ($contents in $supportContents) {
            [regex]::Matches($contents, "internal\s+(?:actual\s+|expect\s+)?fun\s+([A-Za-z0-9_]+)\s*\(") |
                ForEach-Object { $_.Groups[1].Value }
        }
    )

    [pscustomobject]@{
        SourceRoot = (Resolve-Path -LiteralPath $Root).Path
        KotlinFileCount = $files.Count
        ModuleAbiSupportSourceFiles = $supportFiles.Count
        ModuleAbiSupportObjectNames = @($supportObjectNames | Sort-Object -Unique)
        ModuleAbiSupportFunctionCount = $functionShapes.Count
        ModuleAbiSupportFunctionNames = @($functionShapes | Sort-Object -Unique)
        ProjectionModuleAbiCallSites = [int] (Count-Pattern -Files $projectionFiles -Pattern "WinRTModulePlatformAbiCall_")
        ProjectionIntrinsicCallSites = [int] (Count-Pattern -Files $projectionFiles -Pattern "WinRtProjectionIntrinsic\.")
        SupportIntrinsicCallSites = [int] (Count-Pattern -Files $supportFiles -Pattern "WinRtProjectionIntrinsic\.")
        ForbiddenBoxedSignatures = [int] (Count-Pattern -Files $supportFiles -Pattern "vararg|Array<Any\?>|List<Any\?>|Array<Any>|List<Any>")
    }
}

$baseline = Measure-Jar -Path $BaselineJar
$optimized = Measure-Jar -Path $OptimizedJar
$source = Measure-GeneratedSource -Root $GeneratedSourceRoot

$result = [pscustomobject]@{
    Baseline = $baseline
    Optimized = $optimized
    Delta = [pscustomobject]@{
        TotalBytes = $optimized.TotalBytes - $baseline.TotalBytes
        TotalMiB = [math]::Round(($optimized.TotalBytes - $baseline.TotalBytes) / 1MB, 2)
        ClassCount = $optimized.ClassCount - $baseline.ClassCount
        CompressedClassBytes = $optimized.CompressedClassBytes - $baseline.CompressedClassBytes
        CompressedClassMiB = [math]::Round(($optimized.CompressedClassBytes - $baseline.CompressedClassBytes) / 1MB, 2)
        UncompressedClassBytes = $optimized.UncompressedClassBytes - $baseline.UncompressedClassBytes
        UncompressedClassMiB = [math]::Round(($optimized.UncompressedClassBytes - $baseline.UncompressedClassBytes) / 1MB, 2)
    }
    GeneratedSource = $source
}

if ($Json) {
    $result | ConvertTo-Json -Depth 8
} else {
    $result.Baseline | Format-List
    $result.Optimized | Format-List
    $result.Delta | Format-List
    if ($null -ne $result.GeneratedSource) {
        $result.GeneratedSource | Format-List
    }
}
