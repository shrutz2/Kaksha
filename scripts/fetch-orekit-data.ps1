# Windows version of fetch-orekit-data.sh. Run once from the project root.
$ErrorActionPreference = 'Stop'

$target = 'backend\data\orekit-data'
$url = 'https://gitlab.orekit.org/orekit/orekit-data/-/archive/main/orekit-data-main.zip'

if (Test-Path $target) {
    Write-Output "$target already exists, nothing to do."
    return
}

$work = Join-Path ([System.IO.Path]::GetTempPath()) ([System.Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $work | Out-Null
Invoke-WebRequest -Uri $url -OutFile "$work\orekit-data.zip"
Expand-Archive -Path "$work\orekit-data.zip" -DestinationPath $work

New-Item -ItemType Directory -Force -Path 'backend\data' | Out-Null
Move-Item "$work\orekit-data-main" $target
Remove-Item -Recurse -Force $work
Write-Output "Orekit data installed in $target"
