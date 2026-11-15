# Demo 1: harsh start/accel + 2 harsh brakes (~2.5 min) via Pleos Virtual Console.
# Expected: feedback "smooth start / braking" scores drop -> recommends
#   "앞차와 3초 거리 두기" / "3초 세며 천천히 출발하기".
# Open the map. Vehicle quest recording starts automatically on fresh moving speed.
param([string]$Adb = "C:\Android\Sdk\platform-tools\adb.exe")

& $Adb forward tcp:8088 tcp:8088 | Out-Null
$B = "http://127.0.0.1:8088"
# Virtual Console only accepts control while a browser session is alive.
$hb = Start-Job { while ($true) { curl.exe -s -o NUL -X POST "http://127.0.0.1:8088/browser/session?client=demo"; Start-Sleep 1 } }
function Ctl($v) { curl.exe -s -o NUL -X PUT "$B/vehicle/controls?speed=$v&accelerator=0&brake=0&steering=0" }
function Hold($v, $n) { 1..$n | ForEach-Object { Ctl $v; Start-Sleep 1 } }

try {
    curl.exe -s -o NUL -X PUT "$B/vehicle/turn-signal?mode=Off"
    curl.exe -s -o NUL -X PUT "$B/vehicle/gear?gear=Drive"; Start-Sleep 2
    Hold 0 3
    "harsh start 0 -> 18 km/h"
    foreach ($v in 18, 22, 26, 30, 34, 38, 42) { Hold $v 1 }; Hold 42 40
    "harsh accel 42 -> 58";  Hold 58 20
    "harsh brake 58 -> 38";  Hold 38 20
    "harsh brake 38 -> 18";  Hold 18 20
    foreach ($v in 12, 6, 0) { Hold $v 1 }; Hold 0 2
    curl.exe -s -o NUL -X PUT "$B/vehicle/gear?gear=Park"
    "parked - open driving feedback or personalized quests"
} finally {
    Stop-Job $hb; Remove-Job $hb
}
