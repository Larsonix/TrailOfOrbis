$path = Join-Path $PSScriptRoot "..\src\main\resources\Common\UI\Custom\Pages\TrailOfOrbis"

Get-ChildItem -Path $path -Filter "Node_*.ui" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw -Encoding UTF8

    # Match #NodeBtn followed by anything until space or {, and remove ALL underscores in that match
    # First extract the ID, then remove underscores from it
    $newContent = $content -replace '#NodeBtn[a-z0-9_]+', {
        $id = $_.Value
        $id -replace '_', ''
    }

    Set-Content $_.FullName -Value $newContent -NoNewline -Encoding UTF8
    Write-Host "Fixed: $($_.Name)"
}

Write-Host "Done!"
