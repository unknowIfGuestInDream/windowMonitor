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

[CmdletBinding()]
param(
    [string]$OutputEnvPath = $env:GITHUB_ENV,
    [string]$DistDir = 'dist',
    [string]$StagingDir = 'staging'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$versionOutput = & mvn -q -DforceStdout 'help:evaluate' -Dexpression='project.version' 2>$null
$version = $versionOutput.Trim()
if (-not $version) {
    throw 'Failed to extract version from Maven.'
}

$jarName = "windowMonitor-$version.jar"
$jarPath = Join-Path -Path 'target' -ChildPath $jarName
if (-not (Test-Path $jarPath -PathType Leaf)) {
    throw "No jar file found matching path $jarPath"
}

if ($OutputEnvPath) {
    Add-Content -Path $OutputEnvPath -Value "APP_VERSION=$version" -Encoding utf8
    Add-Content -Path $OutputEnvPath -Value "APP_JAR=$jarName" -Encoding utf8
}

if (-not (Test-Path $DistDir)) {
    New-Item -ItemType Directory -Path $DistDir | Out-Null
}
if (-not (Test-Path $StagingDir)) {
    New-Item -ItemType Directory -Path $StagingDir | Out-Null
}

Copy-Item -Path $jarPath -Destination $StagingDir -Force
Copy-Item -Path 'README.md' -Destination $StagingDir -Force
Copy-Item -Path (Join-Path -Path 'scripts' -ChildPath 'win\*') -Destination $StagingDir -Recurse -Force
