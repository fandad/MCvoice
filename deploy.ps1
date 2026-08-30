param(
    [bool]$IncludePv = $true
)

$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$properties = Get-Content -Raw (Join-Path $projectRoot "gradle.properties")
$modVersion = [regex]::Match($properties, "(?m)^mod_version=(.+)$").Groups[1].Value.Trim()

$cacheCandidates = @(
    (Join-Path $projectRoot ".gradle-fresh-cache"),
    "E:\GradleCache"
)
$gradleUserHome = $cacheCandidates |
    Where-Object { Test-Path -LiteralPath (Join-Path $_ "caches") } |
    Select-Object -First 1
if (-not $gradleUserHome) {
    $gradleUserHome = "E:\GradleCache"
}

$env:GRADLE_USER_HOME = $gradleUserHome
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26.0.2"

Push-Location $projectRoot
try {
    & "E:\gradle-9.6.1\bin\gradle.bat" build --no-daemon --no-watch-fs --no-parallel
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed"
    }
}
finally {
    Pop-Location
}

function Get-TargetMods([string]$versionPattern) {
    $versionsRoot = "E:\MC\.minecraft\versions"
    if (-not (Test-Path -LiteralPath $versionsRoot)) {
        return @()
    }

    Get-ChildItem -Directory -LiteralPath $versionsRoot |
        Where-Object { $_.Name -like "*$versionPattern*" } |
        ForEach-Object { Join-Path $_.FullName "mods" } |
        Where-Object { Test-Path -LiteralPath $_ }
}

function Deploy-Jar([string]$source, [string[]]$modsDirs, [string]$historyRoot, [string]$historyCategory) {
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Build artifact not found: $source"
    }
    $jarName = Split-Path -Leaf $source

    foreach ($modsDir in $modsDirs) {
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

    if ($historyRoot -and $historyCategory) {
        $historyCategoryDir = Join-Path $historyRoot $historyCategory
        if (-not (Test-Path -LiteralPath $historyCategoryDir)) {
            New-Item -ItemType Directory -Path $historyCategoryDir -Force | Out-Null
        }
        Copy-Item -LiteralPath $source -Destination (Join-Path $historyCategoryDir $jarName) -Force
        Write-Output "deployed $jarName -> $historyCategoryDir"
    }
}

function Copy-PvFiles([string[]]$modsDirs, [string]$pvJarName) {
    if (-not $IncludePv) {
        return
    }

    $pvRoot = Join-Path $projectRoot "libs\pv"
    $pvSource = Join-Path $pvRoot $pvJarName
    $groupsSource = Join-Path $pvRoot "pv-addon-groups-1.1.1.jar"
    if (-not (Test-Path -LiteralPath $pvSource) -or -not (Test-Path -LiteralPath $groupsSource)) {
        return
    }

    foreach ($modsDir in $modsDirs) {
        if (-not (Test-Path -LiteralPath $modsDir)) {
            continue
        }
        Copy-Item -LiteralPath $pvSource -Destination (Join-Path $modsDir $pvJarName) -Force
        Copy-Item -LiteralPath $groupsSource -Destination (Join-Path $modsDir "pv-addon-groups-1.1.1.jar") -Force
        Write-Output "deployed PV files -> $modsDir"
    }
}

$historyDir = "E:\Bakabot" + [char]0x5386 + [char]0x53F2 + "\MCvoice"
if (-not (Test-Path -LiteralPath $historyDir)) {
    New-Item -ItemType Directory -Path $historyDir -Force | Out-Null
}
$instanceSuffix = [string][char]0x5B9E + [string][char]0x4F8B
$instanceDir = Join-Path $historyDir ($modVersion + $instanceSuffix)
if (-not (Test-Path -LiteralPath $instanceDir)) {
    New-Item -ItemType Directory -Path $instanceDir -Force | Out-Null
}

$artifacts = @(
    @{
        Source = Join-Path $projectRoot "mc26\build\libs\mcvoice-$modVersion+26.x.jar"
        Pattern = "26.2"
        Category = "26.x"
        PvJar = "plasmovoice-fabric-26.2-2.1.16.jar"
    },
    @{
        Source = Join-Path $projectRoot "mc12111\build\libs\mcvoice-$modVersion+1.21.11.jar"
        Pattern = "1.21.11"
        Category = "1.21.11"
        PvJar = "plasmovoice-fabric-1.21.11-2.1.16.jar"
        DeployMods = $false
    },
    @{
        Source = Join-Path $projectRoot "mc1218\build\libs\mcvoice-$modVersion+1.21.8.jar"
        Pattern = "1.21.8"
        Category = "1.21.8"
        PvJar = "plasmovoice-fabric-1.21.6-2.1.16.jar"
    }
)

foreach ($artifact in $artifacts) {
    $modsDirs = @(Get-TargetMods $artifact.Pattern)
    if ($null -ne $artifact.DeployMods -and -not $artifact.DeployMods) {
        $modsDirs = @()
    }
    Deploy-Jar $artifact.Source $modsDirs $historyDir $artifact.Category
    Copy-PvFiles $modsDirs $artifact.PvJar
    if (Test-Path -LiteralPath $artifact.Source) {
        Copy-Item -LiteralPath $artifact.Source -Destination (Join-Path $instanceDir (Split-Path -Leaf $artifact.Source)) -Force
    }
}

Write-Output "deploy finished"
