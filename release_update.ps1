param(
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"
$gradleFile = Join-Path (Get-Location) "app\build.gradle.kts"
$content = [System.IO.File]::ReadAllText($gradleFile)

if ($content -match 'versionCode\s*=\s*(\d+)') {
    $oldCode = [int]$Matches[1]
    $newCode = $oldCode + 1
    $content = [regex]::Replace($content, 'versionCode\s*=\s*\d+', "versionCode = $newCode", 1)
} else {
    throw "Could not find versionCode in app/build.gradle.kts"
}

if ($content -match 'versionName\s*=\s*"([^"]+)"') {
    $oldVer = $Matches[1]
    if ($Version -ne "") {
        $newVer = $Version.TrimStart('v')
    } else {
        $parts = $oldVer.Split('.')
        if ($parts.Length -eq 3 -and ($parts[2] -as [int]) -ne $null) {
            $parts[2] = ([int]$parts[2] + 1).ToString()
            $newVer = $parts -join '.'
        } else {
            $newVer = "1.0.$newCode"
        }
    }
    $content = [regex]::Replace($content, 'versionName\s*=\s*"[^"]+"', "versionName = `"$newVer`"", 1)
} else {
    throw "Could not find versionName in app/build.gradle.kts"
}

[System.IO.File]::WriteAllText($gradleFile, $content, [System.Text.UTF8Encoding]::new($false))

git add .
git commit -m "Release v$newVer (versionCode $newCode)"
git push

$apkUrl = "https://github.com/zakerhsab-star/virus-x/releases/download/v$newVer/VirusX-v$newVer.apk"
Set-Clipboard -Value $apkUrl

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " [OK] Update pushed to GitHub Actions!" -ForegroundColor Green
Write-Host " Version Code : $newCode" -ForegroundColor Yellow
Write-Host " Version Name : $newVer" -ForegroundColor Yellow
Write-Host " Direct Link  : $apkUrl" -ForegroundColor White
Write-Host " (Link copied to your Clipboard automatically!)" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Wait ~3 minutes for GitHub Actions to finish, then paste the link in Super Admin -> OTA Update." -ForegroundColor Gray
