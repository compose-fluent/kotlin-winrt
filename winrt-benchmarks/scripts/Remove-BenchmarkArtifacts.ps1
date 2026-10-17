#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$BuildRoot = (Join-Path $PSScriptRoot '../..' 'build'),
    [Parameter(Mandatory)][string[]]$KeepSnapshotNames,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repositoryBuild = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..' 'build'))
$root = (Resolve-Path -LiteralPath $BuildRoot).Path
if ($root -ne $repositoryBuild -and !$root.StartsWith($repositoryBuild + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Cleanup is restricted to this repository build directory.'
}
$ancestor = Get-Item -LiteralPath $root
while ($ancestor -and $ancestor.FullName.Length -ge $repositoryBuild.Length) {
    if ($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing linked build root: $ancestor" }
    $ancestor = $ancestor.Parent
}
foreach ($name in $KeepSnapshotNames) {
    if ([IO.Path]::GetFileName($name) -ne $name -or $name -in @('.', '..')) {
        throw "KeepSnapshotNames must contain directory names, not paths: $name"
    }
}
$targets = [Collections.Generic.List[object]]::new()
$binaryExtensions = @('.exe', '.dll', '.jar', '.class', '.klib', '.pdb', '.obj', '.o', '.a', '.lib', '.ilk', '.exp', '.jfr', '.etl', '.dmp')
foreach ($directory in Get-ChildItem -LiteralPath $root -Directory) {
    if ($directory.Name -notmatch '^(perf|event|benchmark|contention)' -or $directory.Name -in $KeepSnapshotNames) { continue }
    if ($directory.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing linked directory: $directory" }
    if (Test-Path -LiteralPath (Join-Path $directory.FullName 'snapshot.json')) {
        foreach ($name in @('assets', 'native', 'jvm', 'cppwinrt', 'cswinrt')) {
            $path = Join-Path $directory.FullName $name
            if (Test-Path -LiteralPath $path) { $targets.Add((Get-Item -LiteralPath $path)) }
        }
    } else {
        # Older experiments predate snapshot manifests and saved individual test programs.
        $children = @(Get-ChildItem -LiteralPath $directory.FullName -Recurse -Force)
        if ($children.Where({ $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) {
            throw "Refusing tree containing links: $directory"
        }
        foreach ($file in $children.Where({ !$_.PSIsContainer -and $_.Extension -in $binaryExtensions })) { $targets.Add($file) }
    }
}
[long]$bytes = 0
foreach ($target in $targets) {
    $resolved = (Resolve-Path -LiteralPath $target.FullName).Path
    if (!$resolved.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Target escaped build directory: $resolved"
    }
    if ($target.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Refusing link: $resolved" }
    if ($target.PSIsContainer) {
        $children = @(Get-ChildItem -LiteralPath $resolved -Recurse -Force)
        if ($children.Where({ $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw "Refusing linked tree: $resolved" }
        $bytes += [long](($children.Where({ !$_.PSIsContainer }) | Measure-Object Length -Sum).Sum)
    } else { $bytes += $target.Length }
}
# All targets are checked before deleting anything. Measurement JSON/logs, catalogs and
# snapshot manifests remain available; no source, module build, or package cache is touched.
if ($Apply) {
    foreach ($target in $targets) { Remove-Item -LiteralPath $target.FullName -Recurse -Force }
}
[pscustomobject]@{ Applied = [bool]$Apply; Targets = $targets.Count; Bytes = $bytes; GiB = [math]::Round($bytes / 1GB, 3); KeptSnapshots = $KeepSnapshotNames }
