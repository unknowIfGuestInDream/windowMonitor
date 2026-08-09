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
    Configures the windowMonitor Windows Service to run as a real user account
    so that it can access the interactive desktop, capture screenshots, and
    monitor application windows.

.DESCRIPTION
    When a Windows service runs under the LocalSystem / SYSTEM account it is
    placed in Session 0, an isolated non-interactive session.  In that context:

      * GetForegroundWindow() returns NULL — no foreground window exists in
        Session 0, so the monitor never detects WeChat / QQ windows.
      * java.awt.Robot.createScreenCapture() fails or captures a blank desktop
        because it has no access to the user's graphical session.

    The solution is to make the service log on as the same Windows account that
    is actively using the machine.  That account already has an interactive
    desktop session (Session 1+) where GetForegroundWindow and Robot work
    correctly.

    This script reconfigures the existing "windowMonitor" service — installed
    via the MSI produced by build-service.ps1 — to use the supplied credentials.
    It also grants the account the "Log on as a service" privilege that Windows
    requires for service accounts.

.PARAMETER ServiceName
    Name of the Windows service to configure. Defaults to "windowMonitor".

.PARAMETER Username
    The Windows account under which the service should run.
    Accepts both local accounts (".\username" or "COMPUTERNAME\username") and
    domain accounts ("DOMAIN\username").

.PARAMETER Password
    The password for the account. If omitted the script prompts securely.

.EXAMPLE
    .\configure-service.ps1 -Username ".\Alice"******

.EXAMPLE
    .\configure-service.ps1 -Username "CORP\bob"
    # Prompts for password interactively.

.NOTES
    * Must be run as Administrator.
    * Restart the service after running this script for the change to take effect.
#>

[CmdletBinding()]
param(
    [string]$ServiceName = 'windowMonitor',

    [Parameter(Mandatory = $true)]
    [string]$Username,

    [string]$Password
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ── Privilege check ────────────────────────────────────────────────────────────
$currentPrincipal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'This script must be run as Administrator.'
}

# ── Verify the service exists ─────────────────────────────────────────────────
$svc = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($null -eq $svc) {
    throw "Service '$ServiceName' not found. Install it first via the MSI produced by build-service.ps1."
}

# ── Prompt for password if not provided ───────────────────────────────────────
if (-not $Password) {
    $securePassword = Read-Host -Prompt "Password for $Username" -AsSecureString
    $cred = [System.Management.Automation.PSCredential]::new($Username, $securePassword)
    $Password = $cred.GetNetworkCredential().Password
}

# ── Grant "Log on as a service" right ─────────────────────────────────────────
Write-Host "Granting 'SeServiceLogonRight' to $Username ..."
$tempInf = [System.IO.Path]::GetTempFileName() + '.inf'
$tempDb  = [System.IO.Path]::GetTempFileName() + '.db'
$tempCfg = [System.IO.Path]::GetTempFileName() + '.cfg'

try {
    # Export current local security policy
    secedit /export /cfg $tempCfg /quiet

    $content = Get-Content $tempCfg -Raw

    if ($content -match 'SeServiceLogonRight\s*=\s*(.*)') {
        $existing = $matches[1].Trim()
        if ($existing -notmatch [regex]::Escape($Username)) {
            $updated = $content -replace '(SeServiceLogonRight\s*=\s*.*)', "`$1,$Username"
            Set-Content -Path $tempCfg -Value $updated -Encoding Unicode
        }
    } else {
        $content += "`r`n[Privilege Rights]`r`nSeServiceLogonRight = $Username`r`n"
        Set-Content -Path $tempCfg -Value $content -Encoding Unicode
    }

    # Import the updated policy
    $infContent = @"
[Unicode]
Unicode=yes
[Version]
signature="`$CHICAGO`$"
Revision=1
[Privilege Rights]
SeServiceLogonRight = $Username
"@
    Set-Content -Path $tempInf -Value $infContent -Encoding Unicode
    secedit /configure /db $tempDb /cfg $tempInf /areas USER_RIGHTS /quiet
    Write-Host "  'SeServiceLogonRight' granted."
} finally {
    Remove-Item -Path $tempInf, $tempDb, $tempCfg -Force -ErrorAction SilentlyContinue
}

# ── Change the service logon account ──────────────────────────────────────────
Write-Host "Configuring service '$ServiceName' to run as '$Username' ..."

# sc.exe config is the most reliable cross-version way to change the logon account
$result = sc.exe config $ServiceName obj= $Username password= $Password
if ($LASTEXITCODE -ne 0) {
    throw "sc.exe config failed (exit code $LASTEXITCODE): $result"
}

Write-Host ''
Write-Host "Service '$ServiceName' has been reconfigured successfully."
Write-Host ''
Write-Host 'IMPORTANT: The service now runs as a real user account, which means:'
Write-Host '  * It will have access to the interactive desktop (Session 1+).'
Write-Host '  * GetForegroundWindow() will return the correct foreground window.'
Write-Host '  * java.awt.Robot can capture the screen normally.'
Write-Host ''
Write-Host 'Restart the service to apply the changes:'
Write-Host "    Restart-Service -Name $ServiceName"
Write-Host ''
Write-Host 'If the machine is restarted the service will also start under this'
Write-Host 'account automatically, provided the user is logged in at startup.'
