# Compila, instala y abre TripPlanner en el emulador (lo arranca si no está encendido).
# Uso: .\run.ps1
$ErrorActionPreference = "Stop"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
# The SDK was installed from the Claude desktop app, which is packaged: Windows put it in the
# app's private copy of AppData. From a normal terminal it lives there instead.
if (-not (Test-Path $sdk)) {
    $sdk = (Get-ChildItem "$env:LOCALAPPDATA\Packages\Claude_*\LocalCache\Local\Android\Sdk" -Directory | Select-Object -First 1).FullName
}
$adb = "$sdk\platform-tools\adb.exe"
$env:JAVA_HOME = (Get-ChildItem "$env:ProgramFiles\Microsoft" -Directory -Filter "jdk-17*" | Select-Object -First 1).FullName
$env:ANDROID_HOME = $sdk
# En este PC la JVM no puede abrir su conexión loopback en el TEMP por defecto; un directorio propio lo evita.
$tmp = "$env:USERPROFILE\.gradle\jtmp"
New-Item -ItemType Directory -Force $tmp | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$tmp -Djava.io.tmpdir=$tmp"

if (-not (& $adb devices | Select-String "device$")) {
    Write-Host "Arrancando el emulador..."
    Start-Process "$sdk\emulator\emulator.exe" -ArgumentList "-avd", "TripPlanner", "-no-boot-anim"
    & $adb wait-for-device
    while ((& $adb shell getprop sys.boot_completed 2>$null) -ne "1") { Start-Sleep 2 }
}

Set-Location $PSScriptRoot
.\gradlew.bat installDebug
& $adb shell am start -n com.cuchieman.tripplanner/.MainActivity
