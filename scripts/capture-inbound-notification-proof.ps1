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
    $remote = "/sdcard/ai-telegram-inbound-proof.png"
    Invoke-Adb shell screencap -p $remote | Out-Null
    Invoke-Adb pull $remote $Path | Out-Null
    Invoke-Adb shell rm $remote | Out-Null
}

function Save-WindowDump {
    param([string]$Path)
    $remote = "/sdcard/ai-telegram-inbound-proof.xml"
    Invoke-Adb shell uiautomator dump $remote | Out-Null
    Invoke-Adb pull $remote $Path | Out-Null
    Invoke-Adb shell rm $remote | Out-Null
}

if (-not $OutputDir) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path "build" "inbound-notification-proof-$stamp"
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Write-Step "Checking connected device"
Save-AdbText (Join-Path $OutputDir "00-device.txt") shell getprop ro.product.manufacturer
Save-AdbText (Join-Path $OutputDir "00-model.txt") shell getprop ro.product.model
Save-AdbText (Join-Path $OutputDir "00-sdk.txt") shell getprop ro.build.version.sdk
Save-AdbText (Join-Path $OutputDir "00-package.txt") shell dumpsys package $PackageName
Save-AdbText (Join-Path $OutputDir "01-notifications-before.txt") shell dumpsys notification
Save-Screenshot (Join-Path $OutputDir "01-before.png")

Write-Step "Send a real inbound Telegram message now"
Write-Host "Use a different Telegram account/device to send a new message to the account logged into $PackageName."
Write-Host "Do not use the debug QA notification receiver for this proof."
Read-Host "Press Enter after the notification appears in Android notification shade"

Save-AdbText (Join-Path $OutputDir "02-notifications-after-inbound.txt") shell dumpsys notification
Invoke-Adb shell cmd statusbar expand-notifications | Out-Null
Start-Sleep -Seconds 1
Save-Screenshot (Join-Path $OutputDir "02-shade-after-inbound.png")
Save-WindowDump (Join-Path $OutputDir "02-shade-after-inbound.xml")

Write-Step "Tap the real AI Telegram message notification"
Write-Host "Manually tap the child message notification for $PackageName in the notification shade."
Write-Host "Avoid tapping the grouped summary notification; it only proves app open, not chat routing."
Read-Host "Press Enter after the app opens from the notification"

Save-Screenshot (Join-Path $OutputDir "03-after-tap.png")
Save-WindowDump (Join-Path $OutputDir "03-after-tap.xml")
Save-AdbText (Join-Path $OutputDir "03-activity-after-tap.txt") shell dumpsys activity activities
Save-AdbText (Join-Path $OutputDir "03-notifications-after-tap.txt") shell dumpsys notification

Write-Step "Redaction check"
& (Join-Path $PSScriptRoot "redact-sensitive-artifacts.ps1") -Paths $OutputDir
& (Join-Path $PSScriptRoot "redact-sensitive-artifacts.ps1") -Paths $OutputDir -CheckOnly

Write-Host ""
Write-Host "Inbound notification proof saved to: $OutputDir"
Write-Host "Review screenshots/XML/text manually before sharing; redaction removes known secret patterns, not arbitrary chat names or message text."
