param(
    [string]$DeviceSerial = "",
    [string]$ComposeDeviceSerial = "",
    [string]$ComposeAvdName = "",
    [int]$ConnectedTimeoutSeconds = 600,
    [switch]$SkipConnected,
    [switch]$SkipCompose,
    [switch]$AllowDisposableRealDeviceConnectedQa,
    [switch]$KeepEmulatorRunning
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputDir = Join-Path $repoRoot "build\release-qa-$timestamp"
$summaryJson = Join-Path $outputDir "summary.json"
$summaryMd = Join-Path $outputDir "summary.md"
$steps = New-Object System.Collections.Generic.List[object]
$startedEmulator = $null
$startedEmulatorSerial = ""

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

function Add-Step {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Log,
        [string]$Notes = ""
    )
    $steps.Add([pscustomobject]@{
        name = $Name
        status = $Status
        log = $Log
        notes = $Notes
    })
}

function Invoke-LoggedProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$LogName,
        [bool]$Required = $true
    )
    $logPath = Join-Path $outputDir $LogName
    $stdoutPath = "$logPath.stdout.tmp"
    $stderrPath = "$logPath.stderr.tmp"
    Write-Host "==> $Name"
    Write-Host "    $FilePath $($Arguments -join ' ')"
    $process = Start-Process `
        -FilePath $FilePath `
        -ArgumentList $Arguments `
        -Wait `
        -PassThru `
        -NoNewWindow `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath
    $exitCode = $process.ExitCode

    if (Test-Path -LiteralPath $logPath) {
        Remove-Item -LiteralPath $logPath -Force
    }
    if (Test-Path -LiteralPath $stdoutPath) {
        Get-Content -LiteralPath $stdoutPath | Tee-Object -FilePath $logPath -Append
    }
    if (Test-Path -LiteralPath $stderrPath) {
        Get-Content -LiteralPath $stderrPath | Tee-Object -FilePath $logPath -Append
    }
    Remove-Item -LiteralPath $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue
    if ($exitCode -eq 0) {
        Add-Step -Name $Name -Status "PASS" -Log $logPath
        return
    }

    Add-Step -Name $Name -Status "FAIL" -Log $logPath -Notes "Exit code $exitCode"
    if ($Required) {
        throw "$Name failed with exit code $exitCode. See $logPath"
    }
}

function Get-AdbDevices {
    $lines = & adb devices
    return @(
        $lines | Where-Object { $_ -match "^\S+\s+device$" } | ForEach-Object {
            ($_ -split "\s+")[0]
        }
    )
}

function Get-AdbText {
    param(
        [string]$Serial,
        [string[]]$Arguments
    )
    $output = & adb -s $Serial @Arguments
    return ($output -join "`n").Trim()
}

function Test-RestrictedDevice {
    param([string]$Serial)
    $manufacturer = Get-AdbText -Serial $Serial -Arguments @("shell", "getprop", "ro.product.manufacturer")
    $brand = Get-AdbText -Serial $Serial -Arguments @("shell", "getprop", "ro.product.brand")
    $model = Get-AdbText -Serial $Serial -Arguments @("shell", "getprop", "ro.product.model")
    $miuiVersion = Get-AdbText -Serial $Serial -Arguments @("shell", "getprop", "ro.miui.ui.version.name")
    $hyperOsVersion = Get-AdbText -Serial $Serial -Arguments @("shell", "getprop", "ro.mi.os.version.name")
    $fingerprint = "$manufacturer $brand $model $miuiVersion $hyperOsVersion".ToLowerInvariant()
    return (
        $fingerprint.Contains("xiaomi") -or
        $fingerprint.Contains("redmi") -or
        $fingerprint.Contains("hyperos") -or
        $miuiVersion.Length -gt 0
    )
}

function Test-EmulatorDevice {
    param([string]$Serial)
    $manufacturer = Get-AdbText -Serial $Serial -Arguments @("shell", "getprop", "ro.product.manufacturer")
    $brand = Get-AdbText -Serial $Serial -Arguments @("shell", "getprop", "ro.product.brand")
    $model = Get-AdbText -Serial $Serial -Arguments @("shell", "getprop", "ro.product.model")
    $qemu = Get-AdbText -Serial $Serial -Arguments @("shell", "getprop", "ro.kernel.qemu")
    $fingerprint = "$Serial $manufacturer $brand $model".ToLowerInvariant()
    return $qemu -eq "1" -or $fingerprint.Contains("emulator") -or $fingerprint.Contains("sdk_gphone")
}

function Find-EmulatorExe {
    $candidates = @()
    if ($env:ANDROID_HOME) {
        $candidates += Join-Path $env:ANDROID_HOME "emulator\emulator.exe"
    }
    if ($env:LOCALAPPDATA) {
        $candidates += Join-Path $env:LOCALAPPDATA "Android\Sdk\emulator\emulator.exe"
    }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
    }
    return ""
}

function Wait-ForBoot {
    param(
        [string]$Serial,
        [int]$TimeoutSeconds = 180
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $booted = Get-AdbText -Serial $Serial -Arguments @("shell", "getprop", "sys.boot_completed") 2>$null
        if ($booted -eq "1") {
            & adb -s $Serial shell input keyevent 82 | Out-Null
            return
        }
        Start-Sleep -Seconds 3
    }
    throw "Emulator $Serial did not boot within $TimeoutSeconds seconds."
}

function Start-ComposeAvd {
    param([string]$AvdName)
    $emulatorExe = Find-EmulatorExe
    if (-not $emulatorExe) {
        throw "Android emulator binary not found."
    }
    $avds = & $emulatorExe -list-avds
    if ($AvdName -and -not ($avds -contains $AvdName)) {
        throw "AVD '$AvdName' not found. Available AVDs: $($avds -join ', ')"
    }
    if (-not $AvdName) {
        $AvdName = $avds | Select-Object -First 1
    }
    if (-not $AvdName) {
        return ""
    }

    $before = Get-AdbDevices
    $process = Start-Process `
        -FilePath $emulatorExe `
        -ArgumentList @("-avd", $AvdName, "-no-snapshot-save", "-no-boot-anim") `
        -PassThru `
        -WindowStyle Hidden
    $script:startedEmulator = $process

    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $after = Get-AdbDevices
        $newDevice = $after | Where-Object { $before -notcontains $_ -and $_ -like "emulator-*" } | Select-Object -First 1
        if ($newDevice) {
            Wait-ForBoot -Serial $newDevice
            $script:startedEmulatorSerial = $newDevice
            return $newDevice
        }
        Start-Sleep -Seconds 2
    }
    throw "Started AVD '$AvdName' but no emulator device appeared."
}

function Find-ComposeDevice {
    if ($ComposeDeviceSerial) {
        return $ComposeDeviceSerial
    }
    if ($ComposeAvdName) {
        return Start-ComposeAvd -AvdName $ComposeAvdName
    }
    $devices = Get-AdbDevices
    foreach ($serial in $devices) {
        if (-not (Test-RestrictedDevice -Serial $serial)) {
            return $serial
        }
    }
    return ""
}

try {
    $powershell = (Get-Command powershell.exe).Source

    Invoke-LoggedProcess `
        -Name "Local release gates" `
        -FilePath $powershell `
        -Arguments @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $repoRoot "scripts\run-local-release-gates.ps1"), "-PlainConsole") `
        -LogName "01-local-release-gates.log"

    if (-not $SkipConnected) {
        $devices = Get-AdbDevices
        $safeDevice = if ($DeviceSerial) { $DeviceSerial } else { $devices | Select-Object -First 1 }
        if ($safeDevice) {
            $safeDeviceIsEmulator = Test-EmulatorDevice -Serial $safeDevice
            if ($safeDeviceIsEmulator -or $AllowDisposableRealDeviceConnectedQa) {
                $connectedArgs = @(
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    (Join-Path $repoRoot "scripts\run-connected-safe.ps1"),
                    "-DeviceSerial",
                    $safeDevice,
                    "-TestGroup",
                    "DeviceSafe",
                    "-SkipBuild",
                    "-TimeoutSeconds",
                    "$ConnectedTimeoutSeconds"
                )
                if (-not $safeDeviceIsEmulator) {
                    $connectedArgs += "-DisposableInstall"
                }
                Invoke-LoggedProcess `
                    -Name "Connected DeviceSafe instrumentation" `
                    -FilePath $powershell `
                    -Arguments $connectedArgs `
                    -LogName "02-connected-device-safe.log"
            } else {
                Add-Step -Name "Connected DeviceSafe instrumentation" -Status "SKIPPED" -Log "" -Notes "Real device connected but not marked disposable. Pass -AllowDisposableRealDeviceConnectedQa only for a disposable install; use manual QA for logged-in Telegram sessions."
            }
        } else {
            Add-Step -Name "Connected DeviceSafe instrumentation" -Status "SKIPPED" -Log "" -Notes "No adb device connected."
        }
    } else {
        Add-Step -Name "Connected DeviceSafe instrumentation" -Status "SKIPPED" -Log "" -Notes "Skipped by parameter."
    }

    if (-not $SkipCompose) {
        $composeDevice = Find-ComposeDevice
        if ($composeDevice) {
            $composeDeviceIsEmulator = Test-EmulatorDevice -Serial $composeDevice
            if ($composeDeviceIsEmulator -or $AllowDisposableRealDeviceConnectedQa) {
                $composeArgs = @(
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    (Join-Path $repoRoot "scripts\run-connected-safe.ps1"),
                    "-DeviceSerial",
                    $composeDevice,
                    "-TestGroup",
                    "ComposeUi",
                    "-SkipBuild",
                    "-TimeoutSeconds",
                    "$ConnectedTimeoutSeconds"
                )
                if (-not $composeDeviceIsEmulator) {
                    $composeArgs += "-DisposableInstall"
                }
                Invoke-LoggedProcess `
                    -Name "Connected Compose UI instrumentation" `
                    -FilePath $powershell `
                    -Arguments $composeArgs `
                    -LogName "03-connected-compose-ui.log"
            } else {
                Add-Step -Name "Connected Compose UI instrumentation" -Status "SKIPPED" -Log "" -Notes "Real device connected but not marked disposable. Pass -AllowDisposableRealDeviceConnectedQa only for a disposable install; use emulator Compose UI runs for logged-in Telegram sessions."
            }
        } else {
            $emulatorExe = Find-EmulatorExe
            $avdNote = if ($emulatorExe) {
                $avds = & $emulatorExe -list-avds
                if ($avds) { "No non-Xiaomi device connected. Available AVDs: $($avds -join ', ')" } else { "No non-Xiaomi device connected and no AVDs are configured." }
            } else {
                "No non-Xiaomi device connected and Android emulator binary was not found."
            }
            Add-Step -Name "Connected Compose UI instrumentation" -Status "SKIPPED" -Log "" -Notes $avdNote
        }
    } else {
        Add-Step -Name "Connected Compose UI instrumentation" -Status "SKIPPED" -Log "" -Notes "Skipped by parameter."
    }
} finally {
    $redactionLog = Join-Path $outputDir "99-artifact-redaction.log"
    try {
        $redactionScript = Join-Path $repoRoot "scripts\redact-sensitive-artifacts.ps1"
        $redactionOutput = & (Get-Command powershell.exe).Source `
            -NoProfile `
            -ExecutionPolicy Bypass `
            -File $redactionScript `
            -Paths $outputDir 2>&1
        $redactionExitCode = $LASTEXITCODE
        $checkOutput = & (Get-Command powershell.exe).Source `
            -NoProfile `
            -ExecutionPolicy Bypass `
            -File $redactionScript `
            -Paths $outputDir `
            -CheckOnly 2>&1
        $checkExitCode = $LASTEXITCODE
        @(
            "== Redaction =="
            $redactionOutput
            ""
            "== CheckOnly =="
            $checkOutput
        ) | Set-Content -LiteralPath $redactionLog
        if ($redactionExitCode -eq 0 -and $checkExitCode -eq 0) {
            Add-Step -Name "Artifact redaction" -Status "PASS" -Log $redactionLog -Notes "Generated text artifacts were scrubbed and verified clean."
        } else {
            Add-Step -Name "Artifact redaction" -Status "FAIL" -Log $redactionLog -Notes "Redaction exit $redactionExitCode, check exit $checkExitCode."
        }
    } catch {
        $_ | Out-String | Set-Content -LiteralPath $redactionLog
        Add-Step -Name "Artifact redaction" -Status "FAIL" -Log $redactionLog -Notes "Redaction command failed."
    }

    $steps | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $summaryJson

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Release QA Summary")
    $lines.Add("")
    $lines.Add("- Created: $(Get-Date -Format s)")
    $lines.Add("- Output directory: $outputDir")
    $lines.Add("")
    $lines.Add("| Step | Status | Notes | Log |")
    $lines.Add("| --- | --- | --- | --- |")
    foreach ($step in $steps) {
        $logCell = if ($step.log) { $step.log } else { "" }
        $notesCell = ($step.notes -replace '\|', '/')
        $lines.Add("| $($step.name) | $($step.status) | $notesCell | $logCell |")
    }
    $lines | Set-Content -LiteralPath $summaryMd

    if ($startedEmulator -and -not $KeepEmulatorRunning) {
        try {
            if ($startedEmulatorSerial) {
                & adb -s $startedEmulatorSerial emu kill | Out-Null
            } else {
                & adb emu kill | Out-Null
            }
        } catch {
            Stop-Process -Id $startedEmulator.Id -Force -ErrorAction SilentlyContinue
        }
    }

    Write-Host "Summary: $summaryMd"
}

$failed = $steps | Where-Object { $_.status -eq "FAIL" }
if ($failed) {
    exit 1
}
