param(
    [string]$AuthCycleProofDir = "",
    [string]$ComposeAvdName = "AiTelegram_AOSP_35",
    [switch]$SkipEmulatorQa,
    [switch]$SkipLocalGates
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputDir = Join-Path $repoRoot "build\public-beta-preflight-$timestamp"
$summaryPath = Join-Path $outputDir "summary.md"
$steps = New-Object System.Collections.Generic.List[object]

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

function Add-Step {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Notes = "",
        [string]$Log = ""
    )
    $steps.Add([pscustomobject]@{
        name = $Name
        status = $Status
        notes = $Notes
        log = $Log
    })
}

function Invoke-Logged {
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
    $process = Start-Process `
        -FilePath $FilePath `
        -ArgumentList $Arguments `
        -Wait `
        -PassThru `
        -NoNewWindow `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath

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

    if ($process.ExitCode -eq 0) {
        Add-Step -Name $Name -Status "PASS" -Log $logPath
        return
    }

    Add-Step -Name $Name -Status "FAIL" -Notes "Exit code $($process.ExitCode)" -Log $logPath
    if ($Required) {
        throw "$Name failed with exit code $($process.ExitCode). See $logPath"
    }
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

function Get-AdbDevices {
    $lines = & adb devices
    return @(
        $lines | Where-Object { $_ -match "^\S+\s+device$" } | ForEach-Object {
            ($_ -split "\s+")[0]
        }
    )
}

function Wait-ForBoot {
    param(
        [string]$Serial,
        [int]$TimeoutSeconds = 240
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $booted = (& adb -s $Serial shell getprop sys.boot_completed 2>$null) -join ""
        if ($booted.Trim() -eq "1") {
            return
        }
        Start-Sleep -Seconds 5
    }
    throw "Emulator $Serial did not boot within $TimeoutSeconds seconds."
}

function Start-Avd {
    param([string]$AvdName)
    $emulatorExe = Find-EmulatorExe
    if (-not $emulatorExe) {
        throw "Android emulator binary not found."
    }
    $avds = & $emulatorExe -list-avds
    if (-not ($avds -contains $AvdName)) {
        throw "AVD '$AvdName' not found. Available AVDs: $($avds -join ', ')"
    }

    $before = Get-AdbDevices
    $process = Start-Process `
        -FilePath $emulatorExe `
        -ArgumentList @("-avd", $AvdName, "-no-window", "-no-audio", "-no-snapshot-save", "-wipe-data") `
        -PassThru `
        -WindowStyle Hidden

    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $after = Get-AdbDevices
        $serial = $after | Where-Object { $before -notcontains $_ -and $_ -like "emulator-*" } | Select-Object -First 1
        if ($serial) {
            Wait-ForBoot -Serial $serial
            return [pscustomobject]@{
                serial = $serial
                process = $process
            }
        }
        Start-Sleep -Seconds 2
    }
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    throw "Started AVD '$AvdName' but no emulator device appeared."
}

function Test-AuthCycleProof {
    param([string]$Path)
    if (-not $Path) {
        Add-Step `
            -Name "Disposable TDLib auth-cycle proof" `
            -Status "BLOCKED" `
            -Notes "No -AuthCycleProofDir provided. Run scripts/capture-tdlib-auth-cycle-proof.ps1 with a disposable Telegram QA account."
        return
    }

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if (-not $resolved) {
        Add-Step -Name "Disposable TDLib auth-cycle proof" -Status "FAIL" -Notes "Directory not found: $Path"
        return
    }

    $required = @(
        "01-auth-start.xml",
        "02-auth-ready.xml",
        "03-after-force-stop.xml",
        "04-after-logout-reset.xml",
        "05-second-auth-ready.xml",
        "06-logcat-tail.txt"
    )
    $missing = @(
        foreach ($file in $required) {
            if (-not (Test-Path -LiteralPath (Join-Path $resolved.Path $file))) {
                $file
            }
        }
    )
    if ($missing.Count -gt 0) {
        Add-Step -Name "Disposable TDLib auth-cycle proof" -Status "FAIL" -Notes "Missing files: $($missing -join ', ')"
        return
    }

    Invoke-Logged `
        -Name "Auth-cycle proof redaction check" `
        -FilePath (Get-Command powershell.exe).Source `
        -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            (Join-Path $repoRoot "scripts\redact-sensitive-artifacts.ps1"),
            "-Paths",
            $resolved.Path,
            "-CheckOnly"
        ) `
        -LogName "03-auth-proof-redaction.log" `
        -Required $false

    Add-Step -Name "Disposable TDLib auth-cycle proof" -Status "PASS" -Notes "Required proof files are present in $($resolved.Path)."
}

try {
    $powershell = (Get-Command powershell.exe).Source

    if ($SkipLocalGates) {
        Add-Step -Name "Local release gates" -Status "SKIPPED" -Notes "Skipped by parameter."
    } else {
        Invoke-Logged `
            -Name "Local release gates" `
            -FilePath $powershell `
            -Arguments @(
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                (Join-Path $repoRoot "scripts\run-local-release-gates.ps1"),
                "-PlainConsole"
            ) `
            -LogName "01-local-release-gates.log"
    }

    if ($SkipEmulatorQa) {
        Add-Step -Name "Emulator DeviceSafe QA" -Status "SKIPPED" -Notes "Skipped by parameter."
    } else {
        $started = Start-Avd -AvdName $ComposeAvdName
        try {
            Invoke-Logged `
                -Name "Emulator DeviceSafe QA" `
                -FilePath $powershell `
                -Arguments @(
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    (Join-Path $repoRoot "scripts\run-connected-safe.ps1"),
                    "-DeviceSerial",
                    $started.serial,
                    "-TestGroup",
                    "DeviceSafe",
                    "-SkipBuild"
                ) `
                -LogName "02-emulator-device-safe.log"
        } finally {
            try {
                & adb -s $started.serial emu kill | Out-Null
            } catch {
                Stop-Process -Id $started.process.Id -Force -ErrorAction SilentlyContinue
            }
        }
    }

    Test-AuthCycleProof -Path $AuthCycleProofDir

    Invoke-Logged `
        -Name "Artifact redaction check" `
        -FilePath $powershell `
        -Arguments @(
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            (Join-Path $repoRoot "scripts\redact-sensitive-artifacts.ps1"),
            "-CheckOnly",
            "-Paths",
            "docs",
            "README.md",
            "build",
            "app\build"
        ) `
        -LogName "99-artifact-redaction.log" `
        -Required $false
} finally {
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Public Beta Preflight Summary")
    $lines.Add("")
    $lines.Add("- Created: $(Get-Date -Format s)")
    $lines.Add("- Output directory: $outputDir")
    $lines.Add("")
    $lines.Add("| Step | Status | Notes | Log |")
    $lines.Add("| --- | --- | --- | --- |")
    foreach ($step in $steps) {
        $notes = ($step.notes -replace '\|', '/')
        $lines.Add("| $($step.name) | $($step.status) | $notes | $($step.log) |")
    }
    $lines | Set-Content -LiteralPath $summaryPath
    $steps | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outputDir "summary.json")
    Write-Host "Summary: $summaryPath"
}

$failed = $steps | Where-Object { $_.status -eq "FAIL" }
$blocked = $steps | Where-Object { $_.status -eq "BLOCKED" }
if ($failed) {
    exit 1
}
if ($blocked) {
    exit 2
}
