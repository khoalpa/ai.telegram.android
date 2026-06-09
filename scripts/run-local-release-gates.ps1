param(
    [switch]$PlainConsole
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $repoRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "Could not find gradlew.bat at $gradlew"
}

$consoleArgs = @()
if ($PlainConsole) {
    $consoleArgs += "--console=plain"
}

Push-Location $repoRoot
try {
    & $gradlew `
        ":app:testOutsidePlayDebugUnitTest" `
        ":app:lintOutsidePlayDebug" `
        ":app:assembleOutsidePlayDebug" `
        ":app:assembleOutsidePlayDebugAndroidTest" `
        @consoleArgs

    if ($LASTEXITCODE -ne 0) {
        throw "Local release gates failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
