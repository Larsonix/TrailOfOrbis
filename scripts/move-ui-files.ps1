$sourceDir = Join-Path $PSScriptRoot "..\src\main\resources\Common\UI\Custom\Pages"
$destDir = "$sourceDir\TrailOfOrbis"

Get-ChildItem "$sourceDir\TrailOfOrbis_*" | ForEach-Object {
    $newName = $_.Name -replace '^TrailOfOrbis_', ''
    $destPath = Join-Path $destDir $newName
    Move-Item $_.FullName -Destination $destPath -Force
    Write-Host "Moved: $($_.Name) -> TrailOfOrbis/$newName"
}

Write-Host "`nTotal files moved: $((Get-ChildItem $destDir).Count)"
