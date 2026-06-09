param(
    [string]$DeviceSerial = "3511280d",
    [string]$GradleTask = ":app:connectedOutsidePlayDebugAndroidTest",
    [switch]$DisposableInstall
)

$ErrorActionPreference = "Stop"

if (-not $DisposableInstall) {
    throw "Refusing to run raw connected Gradle tests on Xiaomi/Redmi without -DisposableInstall. Use scripts\run-connected-safe.ps1 or a disposable QA install."
}

function Get-BoundsCenter {
    param([string]$Bounds)
    if ($Bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        return $null
    }
    $left = [int]$Matches[1]
    $top = [int]$Matches[2]
    $right = [int]$Matches[3]
    $bottom = [int]$Matches[4]
    return @{
        X = [int](($left + $right) / 2)
        Y = [int](($top + $bottom) / 2)
    }
}

$watcher = Start-Job -ArgumentList $DeviceSerial -ScriptBlock {
    param([string]$Serial)

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
        adb -s $Serial shell input tap $x $y | Out-Null
        return $true
    }

    for ($i = 0; $i -lt 300; $i++) {
        $xml = adb -s $Serial exec-out uiautomator dump /dev/tty 2>$null
        if ($xml -match 'Install via USB' -and $xml -match 'android:id/button2') {
            if ($xml -match 'Remember my choice') {
                Tap-NodeCenter -Xml $xml -ResourceId 'com.miui.securitycenter:id/do_not_ask_checkbox' | Out-Null
                Start-Sleep -Milliseconds 150
                $xml = adb -s $Serial exec-out uiautomator dump /dev/tty 2>$null
            }
            if (Tap-NodeCenter -Xml $xml -ResourceId 'android:id/button2') {
                Write-Output "Accepted Xiaomi Install via USB dialog at poll $i."
            }
        }
        Start-Sleep -Milliseconds 500
    }
}

try {
    & .\gradlew.bat $GradleTask --console=plain
    $exitCode = $LASTEXITCODE
} finally {
    Stop-Job $watcher -ErrorAction SilentlyContinue | Out-Null
    Receive-Job $watcher -ErrorAction SilentlyContinue
    Remove-Job $watcher -ErrorAction SilentlyContinue
}

exit $exitCode
