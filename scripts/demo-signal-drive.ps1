# Demo 2: 3 lane changes without turn signal, 1 with signal, 1 right turn without braking (~2 min).
# Expected: 깜빡이 25점 / 회전감속 0점 -> recommends vehicle-signal quests
#   "교차로 우회전 · 감속 먼저" / "차선 변경 · 깜빡이 먼저".
# Before running: app open -> 퀘스트 -> 차량 신호 퀘스트 -> button must read "주행 기록 마치기".
#
# NOTE steering sign: Virtual Console +deg arrives in the app as -deg (left).
# The quest detector treats + as right, so right-hand maneuvers are sent as negative values here.
param([string]$Adb = "C:\Android\Sdk\platform-tools\adb.exe")

& $Adb forward tcp:8088 tcp:8088 | Out-Null
$B = "http://127.0.0.1:8088"
$hb = Start-Job { while ($true) { curl.exe -s -o NUL -X POST "http://127.0.0.1:8088/browser/session?client=demo"; Start-Sleep 1 } }
function Ctl($v, $st) { curl.exe -s -o NUL -X PUT "$B/vehicle/controls?speed=$v&accelerator=0&brake=0&steering=$st" }
function Hold($v, $st, $n) { 1..$n | ForEach-Object { Ctl $v $st; Start-Sleep 1 } }
function Sig($m) { curl.exe -s -o NUL -X PUT "$B/vehicle/turn-signal?mode=$m" }

try {
    Sig Off
    curl.exe -s -o NUL -X PUT "$B/vehicle/gear?gear=Drive"; Hold 0 0 3
    foreach ($v in 4, 8, 12, 16, 20, 24, 28, 32, 36, 40) { Hold $v 0 1 }; Hold 40 0 8
    "lane change x3 without signal"
    1..3 | ForEach-Object { Hold 40 8 3; Hold 40 0 7 }
    "lane change with right signal"
    Sig BlinkRight; Hold 40 0 4; Hold 40 -8 3; Hold 40 0 2; Sig Off; Hold 40 0 6
    foreach ($v in 36, 32, 28, 24, 20) { Hold $v 0 1 }; Hold 20 0 3
    "right turn without braking"
    Sig BlinkRight; Hold 20 0 4; Hold 20 -30 4; Hold 20 0 2; Sig Off; Hold 20 0 6
    foreach ($v in 16, 12, 8, 4, 0) { Hold $v 0 1 }; Hold 0 0 3
    curl.exe -s -o NUL -X PUT "$B/vehicle/gear?gear=Park"
    "done - now press the finish-recording button in the app"
} finally {
    Stop-Job $hb; Remove-Job $hb
}
