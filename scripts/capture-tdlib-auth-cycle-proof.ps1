param(
    [string]$DeviceSerial = "",
    [string]$OutputDir = "",
    [string]$PackageName = "ai.telegram.android",
    [switch]$PlainConsole
)

$ErrorActionPreference = "Stop"

function Write-Step($Message) {
    if ($PlainConsole) {
        Write-Host $Message
    } else {
        Write-Host ""
        Write-Host "== $Message =="
    }
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    $adbArgs = @()
    if ($DeviceSerial) {
        $adbArgs += @("-s", $DeviceSerial)
    }
    $adbArgs += $Args
    & adb @adbArgs
}

function Save-AdbText {
    param(
        [string]$Path,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Args
    )
    Invoke-Adb @Args | Out-File -FilePath $Path -Encoding utf8
}

function Save-Screenshot {
    param([string]$Path)
    $remote = "/sdcard/ai-telegram-auth-proof.png"
    Invoke-Adb shell screencap -p $remote | Out-Null
    Invoke-Adb pull $remote $Path | Out-Null
    Invoke-Adb shell rm $remote | Out-Null
}

function Save-WindowDump {
    param([string]$Path)
    $remote = "/sdcard/ai-telegram-auth-proof.xml"
    Invoke-Adb shell uiautomator dump $remote | Out-Null
    Invoke-Adb pull $remote $Path | Out-Null
    Invoke-Adb shell rm $remote | Out-Null
}

function Capture-State {
    param(
        [string]$Prefix,
        [string]$Label
    )
    Write-Step $Label
    Save-Screenshot (Join-Path $OutputDir "$Prefix.png")
    Save-WindowDump (Join-Path $OutputDir "$Prefix.xml")
    Save-AdbText (Join-Path $OutputDir "$Prefix-activity.txt") shell dumpsys activity activities
    Save-AdbText (Join-Path $OutputDir "$Prefix-package.txt") shell dumpsys package $PackageName
}

if (-not $OutputDir) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path "build" "tdlib-auth-cycle-proof-$stamp"
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Write-Step "Checking connected disposable QA device"
Save-AdbText (Join-Path $OutputDir "00-device.txt") shell getprop ro.product.manufacturer
Save-AdbText (Join-Path $OutputDir "00-model.txt") shell getprop ro.product.model
Save-AdbText (Join-Path $OutputDir "00-sdk.txt") shell getprop ro.build.version.sdk
Save-AdbText (Join-Path $OutputDir "00-package.txt") shell dumpsys package $PackageName

Write-Host "Use this script only with a disposable Telegram QA account/session."
Write-Host "Do not use it on a personal or production Telegram account."
Read-Host "Press Enter after opening the app at the login/settings auth surface"
Capture-State "01-auth-start" "Capture auth start"

Write-Host "Complete phone number, code and 2FA if prompted."
Read-Host "Press Enter after the app reaches TDLib Ready / connected chat list"
Capture-State "02-auth-ready" "Capture logged-in ready state"

Write-Host "Force-stop and reopen the app to verify session persistence."
Invoke-Adb shell am force-stop $PackageName | Out-Null
Invoke-Adb shell monkey -p $PackageName 1 | Out-Null
Start-Sleep -Seconds 3
Capture-State "03-after-force-stop" "Capture ready state after force-stop"

Write-Host "Use the app UI to logout/reset local TDLib data."
Read-Host "Press Enter after logout/reset returns the app to the auth surface"
Capture-State "04-after-logout-reset" "Capture logout/reset state"

Write-Host "Sign in again with the same disposable QA account."
Read-Host "Press Enter after the second login reaches TDLib Ready / connected chat list"
Capture-State "05-second-auth-ready" "Capture second login ready state"

Write-Step "Logcat crash scan"
Save-AdbText (Join-Path $OutputDir "06-logcat-tail.txt") logcat -d -t 800

Write-Step "Redaction check"
& (Join-Path $PSScriptRoot "redact-sensitive-artifacts.ps1") -Paths $OutputDir
& (Join-Path $PSScriptRoot "redact-sensitive-artifacts.ps1") -Paths $OutputDir -CheckOnly

Write-Host ""
Write-Host "TDLib auth-cycle proof saved to: $OutputDir"
Write-Host "Review screenshots/XML/text manually before sharing; redaction removes known secret patterns, not arbitrary chat names or message text."
