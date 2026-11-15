param(
    [string]$Adb = 'C:\Android\Sdk\platform-tools\adb.exe',
    [string]$Serial = 'emulator-5574'
)
$ErrorActionPreference = 'Stop'
# Short integration check using the same endpoints and steering convention as demo-signal-drive.ps1.
$hardware = (& $Adb -s $Serial shell getprop ro.hardware).Trim()
if ($Serial -notlike 'emulator-*' -or $hardware -notin @('ranchu','goldfish')) { throw 'Emulator-only verification' }
& $Adb -s $Serial forward tcp:8088 tcp:8088 | Out-Null
$consoleBase = 'http://127.0.0.1:8088'
$heartbeat = Start-Job { while ($true) {
    try { Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8088/browser/session?client=integration-check' -TimeoutSec 2 | Out-Null } catch {}
    Start-Sleep -Seconds 1
} }
function Set-TestControl([double]$Speed, [double]$Steering=0) {
    Invoke-RestMethod -Method Put -Uri "$consoleBase/vehicle/controls?speed=$Speed&accelerator=0&brake=0&steering=$Steering" -TimeoutSec 5 | Out-Null
}
function Hold-TestControl([double]$Speed, [double]$Steering, [int]$Seconds) {
    1..$Seconds | ForEach-Object { Set-TestControl $Speed $Steering; Start-Sleep -Seconds 1 }
}
function Set-TestSignal([string]$Mode) {
    Invoke-RestMethod -Method Put -Uri "$consoleBase/vehicle/turn-signal?mode=$Mode" -TimeoutSec 5 | Out-Null
}
try {
    $ready=$false
    1..20 | ForEach-Object {
        if (!$ready) {
            $state=Invoke-RestMethod -Uri "$consoleBase/vehicle/state" -TimeoutSec 5
            $ready=$state.browser.active -and $state.controls.dds_ready.all
            if (!$ready) { Start-Sleep -Seconds 1 }
        }
    }
    if (!$ready) { throw 'Virtual Console bridge not ready' }
    Set-TestSignal Off
    Invoke-RestMethod -Method Put -Uri "$consoleBase/vehicle/gear?gear=Drive" -TimeoutSec 5 | Out-Null
    Hold-TestControl 0 0 3
    foreach ($speed in @(10,20,30,40)) { Hold-TestControl $speed 0 1 }
    Hold-TestControl 40 0 3
    'No-signal lane change'
    Hold-TestControl 40 8 3; Hold-TestControl 40 0 3
    'RIGHT signal then lane change'
    Set-TestSignal BlinkRight; Hold-TestControl 40 0 3
    Hold-TestControl 40 -8 3; Hold-TestControl 40 0 3
    Set-TestSignal Off; Hold-TestControl 40 0 2
    foreach ($speed in @(30,20)) { Hold-TestControl $speed 0 1 }
    'RIGHT turn without brake'
    Set-TestSignal BlinkRight; Hold-TestControl 20 0 3
    Hold-TestControl 20 -30 3; Hold-TestControl 20 0 3
    Set-TestSignal Off; Hold-TestControl 0 0 5
} finally {
    try { Set-TestControl 0 0; Set-TestSignal Off
        Invoke-RestMethod -Method Put -Uri "$consoleBase/vehicle/gear?gear=Park" -TimeoutSec 5 | Out-Null
    } finally { Stop-Job $heartbeat; Remove-Job $heartbeat }
}
