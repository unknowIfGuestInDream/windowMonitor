#
# Copyright (c) 2025 unknowIfGuestInDream.
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#     * Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
#     * Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
#     * Neither the name of unknowIfGuestInDream, any associated website, nor the
# names of its contributors may be used to endorse or promote products
# derived from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
# WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL UNKNOWIFGUESTINDREAM BE LIABLE FOR ANY
# DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
# (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
# ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#

<#
.SYNOPSIS
    Builds the Windows Monitor Windows Service MSI installer using jpackage.

.DESCRIPTION
    This script:
      1. Creates a jpackage resource directory and downloads NSSM (the service installer helper).
      2. Generates the icon file used for the Windows service entry.
      3. Invokes jpackage to produce an MSI that registers the application as a
         Windows service named "Windows Monitor".
      4. Renames the resulting MSI to a versioned filename and moves it to ./dist/.

.PARAMETER AppJar
    The filename of the application fat-jar (e.g. windowMonitor-1.0.0.jar).

.PARAMETER AppVersion
    The semantic version string (e.g. 1.0.0).

.PARAMETER DistDir
    Output directory for the final MSI. Defaults to "dist".

.EXAMPLE
    .\build-service.ps1 -AppJar "windowMonitor-1.0.0.jar" -AppVersion "1.0.0"
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$AppJar,

    [Parameter(Mandatory = $true)]
    [string]$AppVersion,

    [string]$DistDir = 'dist'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── Step 1: Prepare output and resource directories ───────────────────────────
Write-Host '[Step 1/4] Preparing directories...'
$resourceDir   = 'jpackage-resources'
$jpackageInput = 'jpackage-input'
foreach ($dir in @($DistDir, $resourceDir, $jpackageInput)) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
}
Write-Host "  Resource dir : $resourceDir"
Write-Host "  Input dir    : $jpackageInput"
Write-Host "  Output dir   : $DistDir"

# ── Step 2: Copy application jar into jpackage input ─────────────────────────
Write-Host '[Step 2/4] Staging application jar...'
$jarPath = "target\$AppJar"
if (-not (Test-Path $jarPath)) {
    throw "Application jar not found: $jarPath"
}
Copy-Item -Path $jarPath -Destination $jpackageInput
Write-Host "  Copied $AppJar -> $jpackageInput\"

# ── Step 3: Obtain NSSM (service installer helper) and copy icon ──────────────
Write-Host '[Step 3/4] Obtaining NSSM service installer helper...'

# Check if nssm is already available in the environment PATH.
# If so, use it directly and skip the download entirely.
$nssmOnPath = Get-Command nssm -ErrorAction SilentlyContinue
if ($null -ne $nssmOnPath) {
    Write-Host "  nssm found in PATH: $($nssmOnPath.Source) — skipping download"
    Copy-Item -Path $nssmOnPath.Source -Destination "$resourceDir\service-installer.exe"
    Write-Host "  Placed service-installer.exe in $resourceDir\"
} else {
    Write-Host "  nssm not found in PATH — downloading..."

    $expectedHash = '727D1E42275C605E0F04ABA98095C38A8E1E46DEF453CDFFCE42869428AA6743'
    $urls = @(
        'https://github.com/HandSonic/nssm/releases/download/2.24/nssm-2.24.zip',
        'https://nssm.cc/release/nssm-2.24.zip'
    )

    $downloaded = $false
    foreach ($url in $urls) {
        try {
            Write-Host "  Trying: $url"
            Invoke-WebRequest -Uri $url -OutFile nssm.zip -UseBasicParsing
            $actualHash = (Get-FileHash -Path nssm.zip -Algorithm SHA256).Hash
            if ($actualHash -ne $expectedHash) {
                Write-Host "  Hash mismatch (expected $expectedHash, got $actualHash) — skipping"
                Remove-Item -Path nssm.zip -Force -ErrorAction SilentlyContinue
                continue
            }
            $downloaded = $true
            Write-Host "  Download verified (SHA-256 OK)"
            break
        } catch {
            Write-Host "  Failed: $_"
            Remove-Item -Path nssm.zip -Force -ErrorAction SilentlyContinue
        }
    }
    if (-not $downloaded) {
        throw 'Failed to download NSSM from all configured sources.'
    }

    # Extract NSSM and place it in the resource directory as service-installer.exe
    Write-Host '  Extracting NSSM...'
    Expand-Archive -Path nssm.zip -DestinationPath nssm-extract -Force
    $nssmExe = 'nssm-extract\nssm-2.24\win64\nssm.exe'
    if (-not (Test-Path $nssmExe)) {
        throw "nssm.exe not found at expected path: $nssmExe"
    }
    Copy-Item -Path $nssmExe -Destination "$resourceDir\service-installer.exe"
    Remove-Item -Path nssm.zip, nssm-extract -Recurse -Force
    Write-Host "  Placed service-installer.exe in $resourceDir\"
}

# Copy the Windows Monitor service icon into the jpackage resource directory.
# jpackage picks up windowmonitor.ico from --resource-dir and uses it for the
# service entry in the Windows Services console (services.msc).
$iconSource = "scripts\win\windowmonitor.ico"
if (Test-Path $iconSource) {
    Copy-Item -Path $iconSource -Destination "$resourceDir\windowmonitor.ico"
    Write-Host "  Copied service icon -> $resourceDir\windowmonitor.ico"
} else {
    Write-Warning "  Icon not found at $iconSource — service will use default icon."
}

# ── Step 4: Build MSI with jpackage ──────────────────────────────────────────
Write-Host '[Step 4/4] Building Windows Service MSI with jpackage...'
Write-Host "  Service display name : Windows Monitor"
Write-Host "  App version          : $AppVersion"

jpackage `
    --input           $jpackageInput `
    --name            "Windows Monitor" `
    --main-jar        $AppJar `
    --main-class      com.tlcsdm.windowmonitor.WindowMonitorUploader `
    --type            msi `
    --launcher-as-service `
    --resource-dir    $resourceDir `
    --win-dir-chooser `
    --win-menu `
    --win-menu-group  "Windows Monitor" `
    --win-upgrade-uuid "09b6b7b5-7784-4bf4-a638-1fccd8eeab77" `
    --license-file    LICENSE `
    --app-version     $AppVersion `
    --vendor          "Tlcsdm" `
    --description     "Windows Monitor - Monitors active window and uploads screenshots to WebDAV storage." `
    --dest            $DistDir

if ($LASTEXITCODE -ne 0) {
    throw "jpackage exited with code $LASTEXITCODE"
}

# Rename the generated MSI to the versioned name expected by the workflow
$msiFile = Get-ChildItem -Path "$DistDir\*.msi" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -eq $msiFile) {
    throw 'No MSI file found in output directory after jpackage.'
}
$newName = "windowMonitor-windows-service-$AppVersion.msi"
Rename-Item -Path $msiFile.FullName -NewName $newName
Write-Host "  Created $DistDir\$newName"

# ── Cleanup ───────────────────────────────────────────────────────────────────
Write-Host '[Done] Cleaning up temporary directories...'
Remove-Item -Path $jpackageInput -Recurse -Force
Remove-Item -Path $resourceDir   -Recurse -Force

Write-Host "Build complete: $DistDir\$newName"
