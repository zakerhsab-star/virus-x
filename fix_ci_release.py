import subprocess
from pathlib import Path

root = Path.cwd()
wf_path = root / ".github/workflows/build-apk.yml"

try:
    prev_wf = subprocess.check_output(
        ["git", "show", "HEAD~1:.github/workflows/build-apk.yml"],
        text=True,
        encoding="utf-8"
    )
except Exception:
    prev_wf = r'''name: Build VirusX APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

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

      - name: Build Debug APK
        run: gradle assembleDebug

      - name: Upload VirusX Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: VirusX-Debug-APK
          path: app/build/outputs/apk/debug/app-debug.apk
'''

if "permissions:" not in prev_wf:
    prev_wf = prev_wf.replace("jobs:", "permissions:\n  contents: write\n\njobs:", 1)

release_steps = r'''
      - name: Prepare Release APK
        run: |
          VER_NAME=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)
          cp app/build/outputs/apk/debug/app-debug.apk "VirusX-v${VER_NAME}.apk"
          echo "VER_NAME=${VER_NAME}" >> $GITHUB_ENV

      - name: Publish GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v${{ env.VER_NAME }}
          name: VirusX v${{ env.VER_NAME }}
          files: VirusX-v${{ env.VER_NAME }}.apk
          generate_release_notes: true
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
'''

final_wf = prev_wf.rstrip() + "\n" + release_steps.lstrip("\n")
wf_path.write_text(final_wf, encoding="utf-8")
print("[+] Restored working CI build steps and attached GitHub Release publisher!")