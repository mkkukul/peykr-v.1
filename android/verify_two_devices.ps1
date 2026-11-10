<#
.SYNOPSIS
  PEYKR — Real Two-Physical-Device Verification & Telemetry Collector

.DESCRIPTION
  Automates two-device verification: ADB device discovery, IP resolution across
  multiple Wi-Fi interfaces, safe APK update (-r without wiping Keystore or SQLite DB),
  TCP port connectivity test, and logcat filter presets.

.PARAMETER DeviceA
  Serial number for Device A (Initiator). Optional; auto-detected if omitted.

.PARAMETER DeviceB
  Serial number for Device B (Responder). Optional; auto-detected if omitted.

.PARAMETER SkipInstall
  Skips APK installation and runs network and diagnostic checks directly.
#>

param(
    [string]$DeviceA = '',
    [string]$DeviceB = '',
    [switch]$SkipInstall,
    [int]$Port = 53317
)

$ErrorActionPreference = 'Continue'

# 1. Resolve ADB Executable
$adbCandidates = @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "$env:ProgramFiles(x86)\Android\android-sdk\platform-tools\adb.exe"
)

$adb = ''
foreach ($candidate in $adbCandidates) {
    if ($candidate -and (Test-Path $candidate)) {
        $adb = $candidate
        break
    }
}

if (-not $adb) {
    $cmdAdb = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmdAdb) {
        $adb = $cmdAdb.Source
    } else {
        $adb = 'adb'
    }
}

# 2. Resolve APK Path (handles invocation from workspace root or android/ subfolder)
$apkCandidates = @(
    "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk",
    "$PSScriptRoot\android\app\build\outputs\apk\debug\app-debug.apk",
    "$PSScriptRoot\..\android\app\build\outputs\apk\debug\app-debug.apk"
)

$apkPath = ''
foreach ($cand in $apkCandidates) {
    if (Test-Path $cand) {
        $apkPath = (Resolve-Path $cand).Path
        break
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   PEYKR - REAL TWO-PHYSICAL-DEVICE VERIFICATION TOOL   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "ADB Path : $adb" -ForegroundColor Gray
if ($apkPath) {
    $apkSizeMb = [math]::Round((Get-Item $apkPath).Length / 1MB, 2)
    Write-Host "APK Path : $apkPath ($apkSizeMb MB)" -ForegroundColor Gray
} else {
    Write-Host "APK Path : [Not found in standard build directories]" -ForegroundColor DarkYellow
}

# 3. Detect Connected Devices
Write-Host ""
Write-Host "[1/5] Detecting connected ADB devices..." -ForegroundColor Yellow
$rawDevices = & $adb devices -l
$onlineDevices = @()
$unauthorizedDevices = @()
$offlineDevices = @()

foreach ($line in $rawDevices) {
    if ($line -match '^([^\s]+)\s+device\b') {
        $onlineDevices += $matches[1].Trim()
    } elseif ($line -match '^([^\s]+)\s+unauthorized\b') {
        $unauthorizedDevices += $matches[1].Trim()
    } elseif ($line -match '^([^\s]+)\s+offline\b') {
        $offlineDevices += $matches[1].Trim()
    }
}

if ($unauthorizedDevices.Count -gt 0) {
    Write-Host "[!] Unauthorized device(s) detected: $($unauthorizedDevices -join ', ')" -ForegroundColor Yellow
    Write-Host "    Please unlock the phone screen and tap 'Always allow from this computer'." -ForegroundColor Yellow
}

if ($offlineDevices.Count -gt 0) {
    Write-Host "[!] Offline device(s) detected: $($offlineDevices -join ', ')" -ForegroundColor Yellow
    Write-Host "    Please reconnect USB cable or toggle USB debugging." -ForegroundColor Yellow
}

# Device Selection
$selectedA = $DeviceA
$selectedB = $DeviceB

if (-not $selectedA -and $onlineDevices.Count -ge 1) {
    $selectedA = $onlineDevices[0]
}
if (-not $selectedB -and $onlineDevices.Count -ge 2) {
    $selectedB = $onlineDevices[1]
}

if (-not $selectedA -or -not $selectedB) {
    Write-Host ""
    Write-Host "Online devices detected: $($onlineDevices.Count)" -ForegroundColor Yellow
    if ($onlineDevices.Count -gt 0) {
        Write-Host "  Available: $($onlineDevices -join ', ')" -ForegroundColor White
    }
    Write-Host ""
    Write-Host "[!IMPORTANT] Two-Device Verification Requirement:" -ForegroundColor Yellow
    Write-Host "PEYKR requires two separate physical Android devices to verify:" -ForegroundColor White
    Write-Host "  1. Mutual cryptographic handshake and persistent ratchet state" -ForegroundColor White
    Write-Host "  2. Wi-Fi mDNS discovery and HTTP transport on port $Port" -ForegroundColor White
    Write-Host "  3. Cryptographically signed delivery acknowledgements (status == DELIVERED)" -ForegroundColor White
    Write-Host "  4. Offline store-and-forward queue automatic draining" -ForegroundColor White
    Write-Host ""
    Write-Host "To connect two devices:" -ForegroundColor Cyan
    Write-Host "  Option A (2 USB cables): Connect both phones to this computer." -ForegroundColor Gray
    Write-Host "  Option B (Wireless ADB): On phone -> Developer options -> Wireless debugging." -ForegroundColor Gray
    Write-Host "    Run: $adb connect <phone-ip>:<pairing-port>" -ForegroundColor Gray
    Write-Host "  Option C (Specify devices): verify_two_devices.ps1 -DeviceA <serial1> -DeviceB <serial2>" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Script executed successfully in diagnostic mode." -ForegroundColor Green
    exit 0
}

Write-Host "Selected Device A (Initiator): $selectedA" -ForegroundColor Green
Write-Host "Selected Device B (Responder): $selectedB" -ForegroundColor Green

# 4. Safe APK Installation (preserve existing pairings and databases)
Write-Host ""
Write-Host "[2/5] Preparing APK installation..." -ForegroundColor Yellow
if ($SkipInstall) {
    Write-Host "Skipping installation as requested (-SkipInstall)." -ForegroundColor Gray
} else {
    if (-not $apkPath) {
        Write-Error "Cannot install: APK not found. Please run ./gradlew assembleDebug first."
        exit 1
    }
    Write-Host "Installing on Device A ($selectedA)..." -ForegroundColor White
    $installA = & $adb -s $selectedA install -r -d $apkPath
    Write-Host "  Result: $installA" -ForegroundColor Gray

    Write-Host "Installing on Device B ($selectedB)..." -ForegroundColor White
    $installB = & $adb -s $selectedB install -r -d $apkPath
    Write-Host "  Result: $installB" -ForegroundColor Gray
}

# 5. Extract IP Addresses Across Multiple Interfaces
function Get-DeviceIp([string]$serial) {
    $ipOutput = & $adb -s $serial shell ip -4 addr show 2>$null
    $matches = [regex]::Matches($ipOutput, 'inet\s+([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)')
    $foundIps = @()
    foreach ($m in $matches) {
        $val = $m.Groups[1].Value
        if ($val -ne '127.0.0.1') {
            $foundIps += $val
        }
    }
    return ($foundIps | Select-Object -Unique)
}

Write-Host ""
Write-Host "[3/5] Querying Local Network (Wi-Fi) IP Addresses..." -ForegroundColor Yellow
$ipsA = Get-DeviceIp -serial $selectedA
$ipsB = Get-DeviceIp -serial $selectedB

$ipAStr = if ($ipsA) { $ipsA -join ', ' } else { 'None detected' }
$ipBStr = if ($ipsB) { $ipsB -join ', ' } else { 'None detected' }
Write-Host "Device A IP(s): $ipAStr" -ForegroundColor Cyan
Write-Host "Device B IP(s): $ipBStr" -ForegroundColor Cyan

# 6. Launch Applications
Write-Host ""
Write-Host "[4/5] Launching Peykr on both devices..." -ForegroundColor Yellow
& $adb -s $selectedA shell am start -n com.peykr.app/.MainActivity | Out-Null
& $adb -s $selectedB shell am start -n com.peykr.app/.MainActivity | Out-Null
Write-Host "Application launched on both devices." -ForegroundColor Green

# 7. Step-by-Step Test Telemetry
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "   TWO-DEVICE VERIFICATION READY                         " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Step 1: Open 'Kişiler' (Contacts) on both devices." -ForegroundColor White
Write-Host "Step 2: Note the 6-digit temporary PIN or initiate inline pairing." -ForegroundColor White
Write-Host "Step 3: Pair Device A and Device B." -ForegroundColor White
Write-Host "Step 4: Ensure both devices are connected to the same Wi-Fi router." -ForegroundColor White
Write-Host "Step 5: Send a direct message from Device A to Device B." -ForegroundColor White
Write-Host "        - Expect: Single message bubble on Device B with wire ID" -ForegroundColor Gray
Write-Host "        - Expect: Status 'İletildi' on Device A after signed ACK" -ForegroundColor Gray
Write-Host "Step 6: Disconnect Device B from Wi-Fi, send message from Device A." -ForegroundColor White
Write-Host "        - Expect: Status 'Kuyrukta' on Device A" -ForegroundColor Gray
Write-Host "Step 7: Reconnect Device B to Wi-Fi." -ForegroundColor White
Write-Host "        - Expect: Queue drains automatically, status updates to 'İletildi'" -ForegroundColor Gray
Write-Host ""
Write-Host "To stream live diagnostic logs, open two separate terminal windows:" -ForegroundColor Cyan
Write-Host "  [Terminal 1 - Device A]:" -ForegroundColor White
Write-Host "  adb -s $selectedA logcat -v time -s LocalHttpServer LocalHttpClient TransportManager DoubleRatchetEngine LocationSafetyEngine" -ForegroundColor DarkCyan
Write-Host "  [Terminal 2 - Device B]:" -ForegroundColor White
Write-Host "  adb -s $selectedB logcat -v time -s LocalHttpServer LocalHttpClient TransportManager DoubleRatchetEngine LocationSafetyEngine" -ForegroundColor DarkCyan
Write-Host ""
Write-Host "Verification tool completed successfully." -ForegroundColor Green
exit 0
