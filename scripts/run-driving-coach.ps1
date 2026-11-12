param(
    [ValidateSet('Demo','Live','Verify','Actions','Quests','Cockpit')][string]$Mode='Demo',
    [string]$Avd='Pleos_Home_2_1',
    [int]$Port=5574,
    [switch]$Build
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$sdk=if($env:ANDROID_HOME) {$env:ANDROID_HOME} else {Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
if(!$env:JAVA_HOME) {
    $jdk=Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE 'DevTools') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if($jdk) {$env:JAVA_HOME=$jdk.FullName}
}
$adb=Join-Path $sdk 'platform-tools\adb.exe'
$emulator=Join-Path $sdk 'emulator\emulator.exe'
if(!(Test-Path -LiteralPath $adb)) {throw "Android SDK not found: $sdk"}
$env:ANDROID_HOME=$sdk
$serial="emulator-$Port"
$app='com.hmgchallenge.pleosstarter'
$output=Join-Path $repo 'app\build\home-validation'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$apk=Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
$testApk=Join-Path $repo 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
if($Build -or !(Test-Path -LiteralPath $apk) -or ($Mode -in @('Verify','Actions') -and !(Test-Path -LiteralPath $testApk))) {
    Push-Location $repo
    try {
        & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
        if($LASTEXITCODE) {throw 'Build failed'}
    } finally {Pop-Location}
}
if(!((& $adb devices) -match "^$serial\s+device")) {
    if(Get-Process -Name 'qemu-system-x86_64' -ErrorAction SilentlyContinue) {throw 'Another emulator is running. Close it or use its port explicitly.'}
    Start-Process -FilePath $emulator -WindowStyle Normal -ArgumentList @('-avd',$Avd,'-port',"$Port",'-no-snapshot','-gpu','host','-memory','4096','-cores','4','-scale','0.55') `
        -RedirectStandardOutput (Join-Path $output 'emulator.stdout.log') -RedirectStandardError (Join-Path $output 'emulator.stderr.log') | Out-Null
}
$deadline=(Get-Date).AddMinutes(4)
do {
    $boot=(& $adb -s $serial shell getprop sys.boot_completed 2>$null)
    if($boot -eq '1') {break}
    Start-Sleep -Seconds 2
} while((Get-Date) -lt $deadline)
if($boot -ne '1') {throw 'Emulator boot did not complete. Existing AVD data was preserved.'}
$userId=((& $adb -s $serial shell am get-current-user) -join '').Trim()
& $adb -s $serial install -r $apk
if($LASTEXITCODE) {throw 'APK installation failed. Do not uninstall existing data to bypass a signature mismatch.'}
& $adb -s $serial shell pm grant --user $userId $app android.car.permission.CAR_SPEED
& $adb -s $serial forward tcp:8088 tcp:8088
if($Mode -in @('Verify','Actions')) {
    & $adb -s $serial install -r $testApk
    if($LASTEXITCODE) {throw 'Test APK installation failed'}
    $instrumentArgs=@('-s',$serial,'shell','am','instrument','--user',$userId,'-w','-r')
    if($Mode -eq 'Verify') {
        Write-Host 'Start an actual route in the Pleos Navigation app before verifying TBT.'
        $instrumentArgs+=@('-e','live_probe','1','-e','strict_live','1','-e','seconds','120')
    } else {$instrumentArgs+=@('-e','action_demo','1')}
    $instrumentArgs+="$app.test/com.hmgchallenge.pleosstarter.driving.CoachSmokeRunner"
    $result=& $adb @instrumentArgs
    $result | Tee-Object -FilePath (Join-Path $output "$Mode-result.txt")
    if($Mode -eq 'Verify') {
        & $adb -s $serial shell run-as $app --user $userId cat files/pleos-runtime-proof.json |
            Set-Content -LiteralPath (Join-Path $output 'pleos-runtime-proof.json') -Encoding UTF8
    }
    if(!(($result -join "`n") -match 'result=PASS_(ACTUAL_CALLBACKS|DEMO_ACTIONS)')) {throw "Verification incomplete; inspect $output"}
} elseif($Mode -eq 'Quests') {
    & $adb -s $serial shell am start --user $userId -W -n "$app/.quests.QuestActivity" --ez quest_demo true
} elseif($Mode -eq 'Cockpit') {
    & $adb -s $serial shell am start --user $userId -S -W -n "$app/.driving.DrivingCoachActivity" --ez live_mode false --ez open_cockpit true
} else {
    $live=if($Mode -eq 'Live') {'true'} else {'false'}
    & $adb -s $serial shell am start --user $userId -S -W -n "$app/.driving.DrivingCoachActivity" --ez live_mode $live
}
Write-Host "Ready: $Mode on $serial. Virtual Console: http://127.0.0.1:8088/"
Write-Host 'Open Virtual Console in a browser before changing emulated vehicle values.'
