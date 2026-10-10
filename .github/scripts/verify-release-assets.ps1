[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Directory,
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string] $Version,
    [Parameter(Mandatory)][string] $Commit
)
$ErrorActionPreference = 'Stop'
$expected = @("Kotlin-WinUI-Gallery-$Version-jvm.msix", "Kotlin-WinUI-Gallery-$Version-mingwX64.msix", 'winui-gallery-signing.cer')
foreach ($target in @('jvm', 'mingwX64')) {
    $package = [IO.Compression.ZipFile]::OpenRead((Join-Path $Directory "Kotlin-WinUI-Gallery-$Version-$target.msix"))
    try {
        $manifest = $package.GetEntry('AppxManifest.xml')
        if (-not $manifest -or -not $package.GetEntry('AppxSignature.p7x')) { throw "Unsigned or invalid MSIX: $target" }
        $reader = [IO.StreamReader]::new($manifest.Open())
        try { [xml]$xml = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $identity = $xml.SelectSingleNode("/*[local-name()='Package']/*[local-name()='Identity']")
        if ($null -eq $identity -or $identity.GetAttribute('Version') -cne "$Version.0" -or
            $identity.GetAttribute('Name') -cne 'ComposeFluent.KotlinWinUIGallery' -or
            $identity.GetAttribute('Publisher') -cne 'CN=ComposeFluent' -or
            $identity.GetAttribute('ProcessorArchitecture') -cne 'x64') { throw "Wrong Gallery package identity: $target" }
    } finally { $package.Dispose() }
}
foreach ($variant in @('idea-262', 'as-261', 'as-canary-262')) {
    $file = "kotlin-winrt-ide-$Version-$variant.zip"
    $expected += $file
    $metadata = Get-Content -LiteralPath (Join-Path $Directory "$variant.json") -Raw | ConvertFrom-Json
    if ($metadata.variant -cne $variant -or $metadata.pluginVersion -cne $Version -or
        $metadata.commit -cne $Commit -or $metadata.file -cne $file) { throw "Wrong IDE artifact provenance: $variant" }
    $hash = (Get-FileHash -LiteralPath (Join-Path $Directory $file) -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -cne $metadata.sha256) { throw "Checksum mismatch: $file" }
}
$actual = @(Get-ChildItem -LiteralPath $Directory -File | Where-Object { $_.Extension -in @('.msix', '.zip', '.cer') } | ForEach-Object Name)
if (Compare-Object ($expected | Sort-Object) ($actual | Sort-Object)) { throw 'Missing or unexpected release assets.' }
$checksums = foreach ($file in $expected | Sort-Object) {
    $path = Join-Path $Directory $file
    if ((Get-Item -LiteralPath $path).Length -eq 0) { throw "Empty asset: $file" }
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $file"
}
$checksums | Set-Content -LiteralPath (Join-Path $Directory 'SHA256SUMS.txt') -Encoding utf8NoBOM
