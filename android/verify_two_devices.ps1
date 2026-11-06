# Peykr — Two Physical Device Verification & Telemetry Collector
# Usage: powershell -ExecutionPolicy Bypass -File .\verify_two_devices.ps1

$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "adb"
}

$apkPath = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Error "APK not found at $apkPath. Please run gradlew assembleDebug first."
    exit 1
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   PEYKR — REAL TWO-PHYSICAL-DEVICE VERIFICATION TOOL   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Detect Connected Devices
Write-Host "`n[1/5] Detecting connected ADB devices..." -ForegroundColor Yellow
$deviceLines = & $adb devices | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of" }
$devices = @()
foreach ($line in $deviceLines) {
    $serial = ($line -split "\s+")[0].Trim()
    if ($serial) { $devices += $serial }
}

if ($devices.Count -lt 2) {
    Write-Host "Found $($devices.Count) device(s) connected: $($devices -join ', ')" -ForegroundColor Yellow
    Write-Host "`n[!IMPORTANT] Physical Verification Notice:" -ForegroundColor Red
    Write-Host "Two active devices are required for physical Wi-Fi/mesh verification." -ForegroundColor White
    Write-Host "Please connect both Device A and Device B via USB or Wireless ADB:" -ForegroundColor Gray
    Write-Host "  1. Enable USB Debugging on both phones." -ForegroundColor Gray
    Write-Host "  2. Connect both to this PC, or connect via Wi-Fi: adb connect <ip>:5555" -ForegroundColor Gray
    Write-Host "  3. Re-run this script." -ForegroundColor Gray
    Write-Host "`nListing current device state..." -ForegroundColor Cyan
    & $adb devices -l
    exit 0
}

$deviceA = $devices[0]
$deviceB = $devices[1]
Write-Host "Device A (Initiator): $deviceA" -ForegroundColor Green
Write-Host "Device B (Responder): $deviceB" -ForegroundColor Green

# 2. Install APK on both devices
Write-Host "`n[2/5] Installing latest debug APK on both devices..." -ForegroundColor Yellow
Write-Host "Installing on Device A ($deviceA)..." -ForegroundColor Gray
& $adb -s $deviceA install -r $apkPath
Write-Host "Installing on Device B ($deviceB)..." -ForegroundColor Gray
& $adb -s $deviceB install -r $apkPath

# 3. Query Wi-Fi IP Addresses
Write-Host "`n[3/5] Querying Local Network (Wi-Fi) IP Addresses..." -ForegroundColor Yellow
$ipA = & $adb -s $deviceA shell ip -4 addr show wlan0 | Select-String "inet\s+(\d+\.\d+\.\d+\.\d+)" | ForEach-Object { $_.Matches.Groups[1].Value }
$ipB = & $adb -s $deviceB shell ip -4 addr show wlan0 | Select-String "inet\s+(\d+\.\d+\.\d+\.\d+)" | ForEach-Object { $_.Matches.Groups[1].Value }

Write-Host "Device A Wi-Fi IP: $ipA" -ForegroundColor Cyan
Write-Host "Device B Wi-Fi IP: $ipB" -ForegroundColor Cyan

# 4. Launch Applications
Write-Host "`n[4/5] Launching Peykr on both devices..." -ForegroundColor Yellow
& $adb -s $deviceA shell am start -n com.peykr.app/.MainActivity
& $adb -s $deviceB shell am start -n com.peykr.app/.MainActivity

# 5. Start Telemetry Logger
Write-Host "`n[5/5] Ready for Step-by-Step Two-Device Testing." -ForegroundColor Green
Write-Host "To collect side-by-side logs during testing, run in separate terminal tabs:" -ForegroundColor White
Write-Host "  Device A Log: adb -s $deviceA logcat -v time -s LocalHttpServer LocalHttpClient TransportManager DoubleRatchetEngine LocationSafetyEngine" -ForegroundColor Cyan
Write-Host "  Device B Log: adb -s $deviceB logcat -v time -s LocalHttpServer LocalHttpClient TransportManager DoubleRatchetEngine LocationSafetyEngine" -ForegroundColor Cyan
Write-Host "`nCompleted installation and configuration check." -ForegroundColor Green
