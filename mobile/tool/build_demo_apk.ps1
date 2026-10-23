# Builds the LAN demo APK: a release build that may call one backend machine
# on the local network over plain HTTP. Never use it for a real deployment.
#
# Usage (from mobile/):  .\tool\build_demo_apk.ps1 -HostIp 192.168.1.20 [-Port 8080]
param(
    [Parameter(Mandatory = $true)][string]$HostIp,
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

# Gradle checks again that HostIp is a private IPv4 address.
flutter build apk --release `
    "--dart-define=API_BASE_URL=http://${HostIp}:${Port}" `
    "--dart-define=DEMO_CLEARTEXT_HOST=$HostIp" `
    "--android-project-arg=demoCleartextHost=$HostIp"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

# Keep the demo APK apart from a real release build.
$outDir = 'build\app\outputs\flutter-apk'
Move-Item -Force "$outDir\app-release.apk" "$outDir\app-demo-lan.apk"
Remove-Item -Force -ErrorAction SilentlyContinue "$outDir\app-release.apk.sha1"
Write-Host "Demo APK: $outDir\app-demo-lan.apk (server http://${HostIp}:${Port})"
