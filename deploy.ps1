$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$properties = Get-Content -Raw (Join-Path $projectRoot "gradle.properties")
$modVersion = [regex]::Match($properties, "(?m)^mod_version=(.+)$").Groups[1].Value.Trim()
$mcVersion = [regex]::Match($properties, "(?m)^minecraft_version=(.+)$").Groups[1].Value.Trim()
$jarName = "mcvoice-$modVersion+$mcVersion.jar"
$source = Join-Path $projectRoot "build\libs\$jarName"

$env:GRADLE_USER_HOME = "E:\GradleCache"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26.0.2"
Push-Location $projectRoot
try {
    & "E:\gradle-9.6.1\bin\gradle.bat" build --offline --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle 构建失败，已停止部署"
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $source)) {
    throw "没有找到构建产物: $source"
}

$activeMods = @(
    "E:\MC\.minecraft\versions\26.2test\mods",
    "E:\MC\.minecraft\versions\26.2\mods"
)

foreach ($modsDir in $activeMods) {
    if (-not (Test-Path -LiteralPath $modsDir)) {
        continue
    }

    $versionDir = Split-Path -Parent $modsDir
    $disabledDir = Join-Path $versionDir "mods_disabled"
    if (-not (Test-Path -LiteralPath $disabledDir)) {
        New-Item -ItemType Directory -Path $disabledDir -Force | Out-Null
    }

    Get-ChildItem -File -LiteralPath $modsDir -Filter "mcvoice-*.jar" |
        Where-Object { $_.Name -ne $jarName } |
        ForEach-Object {
            Move-Item -LiteralPath $_.FullName -Destination (Join-Path $disabledDir $_.Name) -Force
            Write-Output "disabled $($_.FullName)"
        }

    Copy-Item -LiteralPath $source -Destination (Join-Path $modsDir $jarName) -Force
    Write-Output "deployed $jarName -> $modsDir"
}

$historyDir = "E:\Bakabot" + [char]0x5386 + [char]0x53F2 + "\MCvoice"
if (-not (Test-Path -LiteralPath $historyDir)) {
    New-Item -ItemType Directory -Path $historyDir -Force | Out-Null
}
Copy-Item -LiteralPath $source -Destination (Join-Path $historyDir $jarName) -Force
Write-Output "deployed $jarName -> $historyDir"
