# Start-InfiniteConquest.ps1 — launch Infinite Conquest from a source checkout.
# Prefers prebuilt jars in dist\app; otherwise builds with the Gradle wrapper.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$appDir = Join-Path $root "dist\app"
$guiJar = Join-Path $appDir "infinite-conquest-gui.jar"

function Find-Java {
    foreach ($candidate in @($env:JAVA_HOME, "")) {
        $exe = if ($candidate) { Join-Path $candidate "bin\java.exe" } else { "java.exe" }
        try {
            $version = & $exe -version 2>&1 | Select-Object -First 1
            if ($version -match 'version "(\d+)') {
                if ([int]$Matches[1] -ge 17) { return $exe }
                Write-Host "Found Java $version but 17+ is required." -ForegroundColor Yellow
            }
        } catch { }
    }
    return $null
}

$java = Find-Java
if (-not $java) {
    Write-Host "Java 17 or newer is required but was not found." -ForegroundColor Red
    Write-Host "Install Temurin 17+ from https://adoptium.net and re-run."
    exit 1
}

if (-not (Test-Path $guiJar)) {
    Write-Host "Game jars not found in dist\app — building with Gradle..." -ForegroundColor Cyan
    $gradlew = Join-Path $root "gradlew.bat"
    if (-not (Test-Path $gradlew)) {
        Write-Host "gradlew.bat not found; cannot build. Place built jars in dist\app." -ForegroundColor Red
        exit 1
    }
    & $gradlew :game-gui:jar :game-cli:jar :game-core:jar --offline
    if (-not (Test-Path $guiJar)) {
        Write-Host "Build did not produce $guiJar." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Launching Infinite Conquest..." -ForegroundColor Green
& $java -jar $guiJar
