#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import shutil
import subprocess
from pathlib import Path

root = Path.cwd()

# 1. Generate persistent signing keystore so OTA updates install cleanly over existing app
keystore_path = root / "app/virusx-signing.jks"
if not keystore_path.exists():
    candidates = [
        shutil.which("keytool"),
        os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "keytool.exe"),
        r"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe",
        r"C:\Program Files\Android\Android Studio\jre\bin\keytool.exe",
    ]
    keytool_bin = next((c for c in candidates if c and Path(c).exists()), None)
    if keytool_bin:
        subprocess.run([
            keytool_bin, "-genkeypair", "-v",
            "-keystore", str(keystore_path),
            "-alias", "virusx",
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
            "-storepass", "virusx123", "-keypass", "virusx123",
            "-dname", "CN=VirusX, OU=Dev, O=VirusX, L=Cairo, S=Cairo, C=EG"
        ], check=True)
        print("[+] Created persistent signing keystore: app/virusx-signing.jks")
    else:
        print("[!] keytool not found in PATH; skipping local keystore generation.")

# 2. Configure app/build.gradle.kts to use the persistent keystore
gradle_path = root / "app/build.gradle.kts"
if gradle_path.exists():
    g_txt = gradle_path.read_text(encoding="utf-8")
    if "virusx-signing.jks" not in g_txt and "android {" in g_txt:
        signing_block = """android {
    signingConfigs {
        getByName("debug") {
            val ks = file("virusx-signing.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "virusx123"
                keyAlias = "virusx"
                keyPassword = "virusx123"
            }
        }
    }"""
        g_txt = g_txt.replace("android {", signing_block, 1)
        gradle_path.write_text(g_txt, encoding="utf-8")
        print("[+] Configured unified signingConfig in app/build.gradle.kts")

# 3. Update GitHub Actions workflow to auto-publish GitHub Release with direct APK link
workflow_path = root / ".github/workflows/build-apk.yml"
workflow_path.parent.mkdir(parents=True, exist_ok=True)
workflow_yaml = """name: Build VirusX APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build Debug APK
        run: ./gradlew assembleDebug

      - name: Prepare Release APK
        run: |
          VER_NAME=$(sed -n 's/.*versionName *= *"\\([^"]*\\)".*/\\1/p' app/build.gradle.kts | head -n 1)
          cp app/build/outputs/apk/debug/app-debug.apk "VirusX-v${VER_NAME}.apk"
          echo "VER_NAME=${VER_NAME}" >> $GITHUB_ENV

      - name: Upload VirusX Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: VirusX-Debug-APK
          path: app/build/outputs/apk/debug/app-debug.apk

      - name: Publish GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v${{ env.VER_NAME }}
          name: VirusX v${{ env.VER_NAME }}
          files: VirusX-v${{ env.VER_NAME }}.apk
          generate_release_notes: true
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
"""
workflow_path.write_text(workflow_yaml, encoding="utf-8")
print("[+] Updated .github/workflows/build-apk.yml with auto-release publishing")

# 4. Create one-click PowerShell release script: release_update.ps1
ps_script = r'''param(
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
'''
(root / "release_update.ps1").write_text(ps_script.strip() + "\n", encoding="utf-8")
print("[✓] Created one-click script: release_update.ps1")