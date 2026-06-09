param(
    [string[]]$Paths = @(),
    [switch]$IncludeLocalProperties,
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

if ($Paths.Count -eq 0) {
    $Paths = @(
        (Join-Path $repoRoot "build"),
        (Join-Path $repoRoot "app\build"),
        $repoRoot
    )
}

$textExtensions = @(
    ".txt",
    ".log",
    ".xml",
    ".md",
    ".html",
    ".json",
    ".properties"
)

$rootOnlyArtifactPatterns = @(
    "*.txt",
    "*.log",
    "*.xml",
    "*.html"
)

$redactions = @(
    @{
        Pattern = '(api_hash\s*=\s*)"[^"]*"'
        Replacement = '${1}"<REDACTED_TELEGRAM_API_HASH>"'
    },
    @{
        Pattern = '(api_id\s*=\s*)[0-9]+'
        Replacement = '${1}<REDACTED_TELEGRAM_API_ID>'
    },
    @{
        Pattern = '(phone_number\s*=\s*)"\+[0-9]{8,15}"'
        Replacement = '${1}"<REDACTED_PHONE_NUMBER>"'
    },
    @{
        Pattern = '(telegramApiHash\s*=\s*).+'
        Replacement = '${1}<REDACTED_TELEGRAM_API_HASH>'
    },
    @{
        Pattern = '(telegramApiId\s*=\s*)[0-9]+'
        Replacement = '${1}<REDACTED_TELEGRAM_API_ID>'
    }
)

function Resolve-CandidateFiles {
    param([string]$Path)

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
    if (-not $resolved) {
        return @()
    }

    $item = Get-Item -LiteralPath $resolved.Path
    if (-not $item.PSIsContainer) {
        return @($item)
    }

    $isRepoRoot = $item.FullName.TrimEnd("\") -eq $repoRoot.TrimEnd("\")
    if ($isRepoRoot) {
        return @(
            foreach ($pattern in $rootOnlyArtifactPatterns) {
                Get-ChildItem -LiteralPath $item.FullName -File -Filter $pattern
            }
        )
    }

    return Get-ChildItem -LiteralPath $item.FullName -Recurse -File |
        Where-Object { $textExtensions -contains $_.Extension.ToLowerInvariant() }
}

$changed = New-Object System.Collections.Generic.List[string]
$scanned = New-Object System.Collections.Generic.HashSet[string]

foreach ($path in $Paths) {
    foreach ($file in Resolve-CandidateFiles -Path $path) {
        if (-not $file) {
            continue
        }
        if (-not $IncludeLocalProperties -and $file.Name -eq "local.properties") {
            continue
        }
        if (-not $scanned.Add($file.FullName)) {
            continue
        }

        $original = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
        if ($null -eq $original) {
            continue
        }

        $redacted = $original
        foreach ($rule in $redactions) {
            $redacted = [regex]::Replace($redacted, $rule.Pattern, $rule.Replacement)
        }

        if ($redacted -ne $original) {
            if (-not $CheckOnly) {
                Set-Content -LiteralPath $file.FullName -Value $redacted -NoNewline
            }
            $changed.Add($file.FullName.Substring($repoRoot.Length + 1))
        }
    }
}

Write-Host "Scanned $($scanned.Count) text artifact files."
if ($CheckOnly) {
    Write-Host "Files requiring redaction: $($changed.Count)"
} else {
    Write-Host "Redacted $($changed.Count) files."
}
foreach ($file in $changed) {
    Write-Host " - $file"
}

if (-not $IncludeLocalProperties) {
    Write-Host "Skipped local.properties. Pass -IncludeLocalProperties only when preparing a shareable copy."
}

if ($CheckOnly -and $changed.Count -gt 0) {
    exit 1
}
