param(
    [string]$SigningPropertiesPath = ".release\outside-play-signing.properties",
    [string]$KeystorePath = ".release\outside-play-beta.jks",
    [string]$KeyAlias = "outside-play-beta",
    [switch]$GenerateKeystore
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputDir = Join-Path $repoRoot "build\signed-release-$timestamp"
$summaryPath = Join-Path $outputDir "summary.md"
$verifyLogPath = Join-Path $outputDir "apksigner-verify.log"

function Resolve-RepoPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $repoRoot $Path
}

function Find-Apksigner {
    $roots = @()
    if ($env:ANDROID_HOME) {
        $roots += Join-Path $env:ANDROID_HOME "build-tools"
    }
    if ($env:ANDROID_SDK_ROOT) {
        $roots += Join-Path $env:ANDROID_SDK_ROOT "build-tools"
    }
    if ($env:LOCALAPPDATA) {
        $roots += Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools"
    }
    foreach ($root in $roots) {
        if ($root -and (Test-Path -LiteralPath $root)) {
            $tool = Get-ChildItem -LiteralPath $root -Recurse -Filter "apksigner.bat" -ErrorAction SilentlyContinue |
                Sort-Object FullName -Descending |
                Select-Object -First 1
            if ($tool) {
                return $tool.FullName
            }
        }
    }
    throw "Could not find apksigner.bat in Android SDK build-tools."
}

function Find-Keytool {
    $cmd = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }
    $studioKeytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
    if (Test-Path -LiteralPath $studioKeytool) {
        return $studioKeytool
    }
    throw "Could not find keytool.exe."
}

function New-SigningSecret {
    $bytes = New-Object byte[] 36
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd("=").Replace("+", "A").Replace("/", "B")
}

function Read-SigningProperties {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return @{}
    }
    $props = @{}
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }
        $parts = $line -split "=", 2
        if ($parts.Count -eq 2) {
            $props[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
    return $props
}

function Write-SigningProperties {
    param(
        [string]$Path,
        [string]$StoreFile,
        [string]$Alias,
        [string]$StorePassword,
        [string]$KeyPassword
    )
    $dir = Split-Path -Parent $Path
    if ($dir) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    @(
        "# Local outside-Play release signing credentials. Do not commit or share."
        "storeFile=$StoreFile"
        "keyAlias=$Alias"
        "storePassword=$StorePassword"
        "keyPassword=$KeyPassword"
    ) | Set-Content -LiteralPath $Path
}

$signingPropertiesFile = Resolve-RepoPath $SigningPropertiesPath
$keystoreFile = Resolve-RepoPath $KeystorePath
$props = Read-SigningProperties -Path $signingPropertiesFile

if ($GenerateKeystore -and -not (Test-Path -LiteralPath $keystoreFile)) {
    $storePassword = New-SigningSecret
    $keyPassword = $storePassword
    $keytool = Find-Keytool
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $keystoreFile) | Out-Null
    & $keytool `
        -genkeypair `
        -v `
        -storetype PKCS12 `
        -keystore $keystoreFile `
        -alias $KeyAlias `
        -keyalg RSA `
        -keysize 4096 `
        -validity 10000 `
        -dname "CN=AI Telegram Outside Play Beta, OU=Public Beta, O=AI Telegram, L=Ho Chi Minh City, C=VN" `
        -storepass $storePassword `
        -keypass $keyPassword | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE"
    }
    Write-SigningProperties `
        -Path $signingPropertiesFile `
        -StoreFile $KeystorePath `
        -Alias $KeyAlias `
        -StorePassword $storePassword `
        -KeyPassword $keyPassword
    $props = Read-SigningProperties -Path $signingPropertiesFile
}

if (-not $props.ContainsKey("storeFile")) {
    $props["storeFile"] = $KeystorePath
}
if (-not $props.ContainsKey("keyAlias")) {
    $props["keyAlias"] = $KeyAlias
}

$storeFile = Resolve-RepoPath $props["storeFile"]
$alias = $props["keyAlias"]
$storePass = $props["storePassword"]
$keyPass = if ($props.ContainsKey("keyPassword") -and $props["keyPassword"]) {
    $props["keyPassword"]
} else {
    $storePass
}

if (-not (Test-Path -LiteralPath $storeFile)) {
    throw "Keystore not found at $storeFile. Rerun with -GenerateKeystore or provide $SigningPropertiesPath."
}
if (-not $storePass) {
    throw "Missing storePassword in $signingPropertiesFile."
}
if (-not $alias) {
    throw "Missing keyAlias in $signingPropertiesFile."
}

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

Push-Location $repoRoot
try {
    & (Join-Path $repoRoot "gradlew.bat") ":app:assembleOutsidePlayRelease" "--console=plain"
    if ($LASTEXITCODE -ne 0) {
        throw "Release build failed with exit code $LASTEXITCODE"
    }

    $apksigner = Find-Apksigner
    $unsignedDir = Join-Path $repoRoot "app\build\outputs\apk\outsidePlay\release"
    $unsignedApks = @(Get-ChildItem -LiteralPath $unsignedDir -Filter "*-release-unsigned.apk")
    if ($unsignedApks.Count -eq 0) {
        throw "No unsigned outsidePlay release APKs found in $unsignedDir."
    }

    $signedApks = New-Object System.Collections.Generic.List[object]
    foreach ($unsignedApk in $unsignedApks) {
        $signedName = $unsignedApk.Name -replace "-unsigned\.apk$", "-signed.apk"
        $signedPath = Join-Path $outputDir $signedName
        & $apksigner sign `
            --ks $storeFile `
            --ks-key-alias $alias `
            --ks-pass "pass:$storePass" `
            --key-pass "pass:$keyPass" `
            --out $signedPath `
            $unsignedApk.FullName
        if ($LASTEXITCODE -ne 0) {
            throw "apksigner sign failed for $($unsignedApk.Name) with exit code $LASTEXITCODE"
        }

        & $apksigner verify --verbose --print-certs $signedPath 2>&1 |
            Tee-Object -FilePath $verifyLogPath -Append | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "apksigner verify failed for $signedName with exit code $LASTEXITCODE"
        }

        $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $signedPath
        $signedApks.Add([pscustomobject]@{
            name = $signedName
            path = $signedPath
            sha256 = $hash.Hash
            mb = [math]::Round((Get-Item -LiteralPath $signedPath).Length / 1MB, 2)
        })
    }

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Signed outsidePlay release")
    $lines.Add("")
    $lines.Add("- Created: $(Get-Date -Format s)")
    $lines.Add("- Output directory: $outputDir")
    $lines.Add("- Keystore: $storeFile")
    $lines.Add("- Key alias: $alias")
    $lines.Add("- Verification log: $verifyLogPath")
    $lines.Add("")
    $lines.Add("| APK | Size MB | SHA-256 |")
    $lines.Add("| --- | ---: | --- |")
    foreach ($apk in $signedApks) {
        $lines.Add("| $($apk.name) | $($apk.mb) | ``$($apk.sha256)`` |")
    }
    $lines | Set-Content -LiteralPath $summaryPath

    Write-Host "Signed release summary: $summaryPath"
} finally {
    Pop-Location
}
