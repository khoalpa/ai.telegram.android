param(
    [string]$DeviceSerial = "",
    [ValidateSet("Auto", "DeviceSafe", "ComposeUi", "All")]
    [string]$TestGroup = "Auto",
    [int]$TimeoutSeconds = 600,
    [switch]$AllowAllOnRestrictedDevice,
    [switch]$DisposableInstall,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $repoRoot "gradlew.bat"
$appPackage = "ai.telegram.android"
$testPackage = "ai.telegram.android.test"
$runner = "$testPackage/androidx.test.runner.AndroidJUnitRunner"
$deviceSafeClasses = @(
    "ai.telegram.android.data.RepositoryRoomTest",
    "ai.telegram.android.data.AppDatabaseMigrationTest"
)
$composeUiAnnotation = "ai.telegram.android.ComposeUiConnectedTest"

function Invoke-Adb {
    param([string[]]$Arguments)
    $adbArgs = @()
    if ($DeviceSerial) {
        $adbArgs += @("-s", $DeviceSerial)
    }
    $adbArgs += $Arguments
    & adb @adbArgs
}

function Get-AdbText {
    param([string[]]$Arguments)
    $output = Invoke-Adb -Arguments $Arguments
    return ($output -join "`n").Trim()
}

function Get-FirstDeviceSerial {
    $lines = & adb devices
    $devices = $lines | Where-Object { $_ -match "^\S+\s+device$" }
    if (-not $devices -or $devices.Count -eq 0) {
        throw "No connected adb device found."
    }
    return (($devices | Select-Object -First 1) -split "\s+")[0]
}

function Test-RestrictedDevice {
    param(
        [string]$Manufacturer,
        [string]$Brand,
        [string]$Model,
        [string]$MiuiVersion,
        [string]$HyperOsVersion
    )
    $fingerprint = "$Manufacturer $Brand $Model $MiuiVersion $HyperOsVersion".ToLowerInvariant()
    return (
        $fingerprint.Contains("xiaomi") -or
        $fingerprint.Contains("redmi") -or
        $fingerprint.Contains("hyperos") -or
        $MiuiVersion.Length -gt 0
    )
}

function Test-EmulatorDevice {
    param(
        [string]$Serial,
        [string]$Manufacturer,
        [string]$Brand,
        [string]$Model
    )
    $qemu = Get-AdbText -Arguments @("shell", "getprop", "ro.kernel.qemu")
    $fingerprint = "$Serial $Manufacturer $Brand $Model".ToLowerInvariant()
    return $qemu -eq "1" -or $fingerprint.Contains("emulator") -or $fingerprint.Contains("sdk_gphone")
}

function Start-XiaomiInstallWatcher {
    param([string]$Serial)
    return Start-Job -ArgumentList $Serial -ScriptBlock {
        param([string]$WatcherSerial)

        function Invoke-WatcherAdb {
            param([string[]]$Arguments)
            if ($WatcherSerial) {
                adb -s $WatcherSerial @Arguments
            } else {
                adb @Arguments
            }
        }

        function Tap-NodeCenter {
            param(
                [string]$Xml,
                [string]$ResourceId
            )
            $pattern = '<node\b(?=[^>]*resource-id="' + [regex]::Escape($ResourceId) + '")[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
            $match = [regex]::Match($Xml, $pattern)
            if (-not $match.Success) {
                return $false
            }
            $x = [int](([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2)
            $y = [int](([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2)
            Invoke-WatcherAdb -Arguments @("shell", "input", "tap", "$x", "$y") | Out-Null
            return $true
        }

        for ($i = 0; $i -lt 300; $i++) {
            $xml = Invoke-WatcherAdb -Arguments @("exec-out", "uiautomator", "dump", "/dev/tty") 2>$null
            if ($xml -match "Install via USB" -and $xml -match "android:id/button2") {
                if ($xml -match "Remember my choice") {
                    Tap-NodeCenter -Xml $xml -ResourceId "com.miui.securitycenter:id/do_not_ask_checkbox" | Out-Null
                    Start-Sleep -Milliseconds 150
                    $xml = Invoke-WatcherAdb -Arguments @("exec-out", "uiautomator", "dump", "/dev/tty") 2>$null
                }
                if (Tap-NodeCenter -Xml $xml -ResourceId "android:id/button2") {
                    Write-Output "Accepted Xiaomi Install via USB dialog at poll $i."
                }
            }
            Start-Sleep -Milliseconds 500
        }
    }
}

function Invoke-WithTimeout {
    param(
        [scriptblock]$Script,
        [int]$Seconds,
        [string]$Description
    )
    $job = Start-Job -ScriptBlock $Script
    try {
        if (-not (Wait-Job $job -Timeout $Seconds)) {
            Stop-Job $job -ErrorAction SilentlyContinue | Out-Null
            throw "$Description timed out after $Seconds seconds."
        }
        Receive-Job $job
        if ($job.State -eq "Failed") {
            throw "$Description failed."
        }
    } finally {
        Remove-Job $job -ErrorAction SilentlyContinue
    }
}

if (-not $DeviceSerial) {
    $DeviceSerial = Get-FirstDeviceSerial
}

$manufacturer = Get-AdbText -Arguments @("shell", "getprop", "ro.product.manufacturer")
$brand = Get-AdbText -Arguments @("shell", "getprop", "ro.product.brand")
$model = Get-AdbText -Arguments @("shell", "getprop", "ro.product.model")
$api = Get-AdbText -Arguments @("shell", "getprop", "ro.build.version.sdk")
$abi = Get-AdbText -Arguments @("shell", "getprop", "ro.product.cpu.abi")
$miuiVersion = Get-AdbText -Arguments @("shell", "getprop", "ro.miui.ui.version.name")
$hyperOsVersion = Get-AdbText -Arguments @("shell", "getprop", "ro.mi.os.version.name")
$restrictedDevice = Test-RestrictedDevice `
    -Manufacturer $manufacturer `
    -Brand $brand `
    -Model $model `
    -MiuiVersion $miuiVersion `
    -HyperOsVersion $hyperOsVersion
$emulatorDevice = Test-EmulatorDevice `
    -Serial $DeviceSerial `
    -Manufacturer $manufacturer `
    -Brand $brand `
    -Model $model

$resolvedGroup = $TestGroup
if ($TestGroup -eq "Auto") {
    $resolvedGroup = if ($restrictedDevice) { "DeviceSafe" } else { "ComposeUi" }
}

if (-not $emulatorDevice -and -not $DisposableInstall) {
    throw "Refusing to install or run connected instrumentation on real device '$manufacturer $brand $model' without -DisposableInstall. Use a disposable QA install, an emulator, or manual QA for logged-in Telegram sessions."
}

if ($restrictedDevice -and $resolvedGroup -eq "All" -and -not $AllowAllOnRestrictedDevice) {
    throw "Refusing to run the full connected suite on restricted device '$manufacturer $brand $model'. Pass -AllowAllOnRestrictedDevice only for a disposable install."
}

Write-Host "Device: $DeviceSerial | $manufacturer $brand $model | API $api | ABI $abi"
Write-Host "Emulator device: $emulatorDevice"
Write-Host "Disposable install asserted: $DisposableInstall"
Write-Host "Restricted runner profile: $restrictedDevice"
Write-Host "Resolved test group: $resolvedGroup"

Push-Location $repoRoot
try {
    if (-not $SkipBuild) {
        & $gradlew ":app:assembleOutsidePlayDebug" ":app:assembleOutsidePlayDebugAndroidTest" --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to build app/test APKs."
        }
    }

    $abiApk = Join-Path $repoRoot "app\build\outputs\apk\outsidePlay\debug\app-outsidePlay-$abi-debug.apk"
    if (-not (Test-Path -LiteralPath $abiApk)) {
        $abiApk = Join-Path $repoRoot "app\build\outputs\apk\outsidePlay\debug\app-outsidePlay-arm64-v8a-debug.apk"
    }
    $testApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\outsidePlay\debug\app-outsidePlay-debug-androidTest.apk"
    if (-not (Test-Path -LiteralPath $abiApk)) {
        throw "Could not find app APK for ABI '$abi'."
    }
    if (-not (Test-Path -LiteralPath $testApk)) {
        throw "Could not find Android test APK."
    }

    $watcher = $null
    if ($restrictedDevice) {
        $watcher = Start-XiaomiInstallWatcher -Serial $DeviceSerial
    }
    try {
        Invoke-Adb -Arguments @("install", "--no-streaming", "-r", "-d", $abiApk)
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to install app APK."
        }
        Invoke-Adb -Arguments @("install", "--no-streaming", "-r", "-d", $testApk)
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to install Android test APK."
        }
    } finally {
        if ($watcher) {
            Stop-Job $watcher -ErrorAction SilentlyContinue | Out-Null
            Receive-Job $watcher -ErrorAction SilentlyContinue
            Remove-Job $watcher -ErrorAction SilentlyContinue
        }
    }

    $instrumentArgs = @("shell", "am", "instrument", "-w")
    switch ($resolvedGroup) {
        "DeviceSafe" {
            $instrumentArgs += @("-e", "class", ($deviceSafeClasses -join ","))
        }
        "ComposeUi" {
            $instrumentArgs += @("-e", "annotation", $composeUiAnnotation)
        }
        "All" {
        }
    }
    $instrumentArgs += $runner

    $serialForJob = $DeviceSerial
    $argsForJob = $instrumentArgs
    Invoke-WithTimeout -Seconds $TimeoutSeconds -Description "Instrumentation $resolvedGroup" -Script {
        $adbArgsForJob = $using:argsForJob
        if ($using:serialForJob) {
            $instrumentOutput = & adb -s $using:serialForJob @adbArgsForJob 2>&1
        } else {
            $instrumentOutput = & adb @adbArgsForJob 2>&1
        }
        $instrumentOutput
        $instrumentText = ($instrumentOutput -join "`n")
        if ($LASTEXITCODE -ne 0) {
            throw "adb instrument exited with code $LASTEXITCODE."
        }
        if (
            $instrumentText -match "FAILURES!!!" -or
            $instrumentText -match "Tests run:\s*\d+,\s+Failures:\s*[1-9]" -or
            $instrumentText -match "INSTRUMENTATION_CODE:\s*-1" -or
            $instrumentText -match "Process crashed"
        ) {
            throw "Instrumentation $using:resolvedGroup reported test failures."
        }
    }
} catch {
    Write-Error $_
    exit 1
} finally {
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $testPackage) 2>$null | Out-Null
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $appPackage) 2>$null | Out-Null
    Pop-Location
}
