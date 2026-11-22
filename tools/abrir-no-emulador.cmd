@echo off
setlocal EnableExtensions
title NexTasks - Emulador Android

rem Caminhos absolutos para comandos do sistema: evita que outra instalacao
rem (Git Bash, por exemplo) sombreie findstr.exe no PATH.
rem A espera usa PING e nao timeout.exe: o timeout do Windows aborta quando o
rem stdin esta redirecionado, o que acontece ao rodar o script com log.
set "SYS=%SystemRoot%\System32"

if not defined ANDROID_HOME set "ANDROID_HOME=%USERPROFILE%\Android\Sdk"
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
set "EMU=%ANDROID_HOME%\emulator\emulator.exe"
set "AVD=NexTasks_API36"
set "PROJ=%~dp0.."
set "PKG=com.daniel.nextasks"

if not exist "%ADB%" (
  echo [erro] adb nao encontrado em:
  echo        %ADB%
  pause
  exit /b 1
)
if not exist "%EMU%" (
  echo [erro] emulador nao encontrado em:
  echo        %EMU%
  pause
  exit /b 1
)

echo ==========================================
echo    NexTasks - abrir no emulador Android
echo ==========================================
echo.

rem O emulador precisa de espaco para crescer o disco da instancia. Com o disco
rem cheio ele nao sobe, e o sintoma parece "emulador quebrado".
for /f "usebackq delims=" %%f in (`powershell -NoProfile -Command "[math]::Round((Get-PSDrive C).Free/1GB,1)"`) do set "LIVRE=%%f"
if defined LIVRE (
  echo [0/3] Espaco livre em C: %LIVRE% GB
  for /f "delims=" %%g in ('powershell -NoProfile -Command "if ((Get-PSDrive C).Free -lt 2GB) { 'pouco' } else { 'ok' }"') do set "ESPACO=%%g"
  if /i "%ESPACO%"=="pouco" (
    echo.
    echo [erro] menos de 2 GB livres no disco C:.
    echo        O emulador precisa de espaco para ligar.
    echo        Libere espaco e rode de novo.
    echo.
    pause
    exit /b 1
  )
)

"%ADB%" start-server >nul 2>&1

"%ADB%" devices 2>nul | "%SYS%\findstr.exe" /c:"emulator-" >nul
if errorlevel 1 (
  echo [1/3] Ligando o emulador %AVD% ...
  rem -no-snapshot: sem carregar E sem gravar snapshot. Gravar gerava ~2,5 GB
  rem por sessao, e o snapshot antigo ia para a lixeira (foi o que encheu o disco).
  start "Emulador Android" "%EMU%" -avd %AVD% -no-snapshot -gpu auto
) else (
  echo [1/3] Emulador ja esta rodando.
)

echo [2/3] Aguardando o Android terminar de ligar ...
"%ADB%" wait-for-device >nul 2>&1

set /a TENT=0
:esperar_boot
"%ADB%" shell getprop sys.boot_completed 2>nul | "%SYS%\findstr.exe" /c:"1" >nul
if not errorlevel 1 goto boot_ok
set /a TENT+=1
if %TENT% GEQ 240 (
  echo.
  echo [erro] o emulador nao terminou de ligar em 8 minutos.
  echo        Se a janela do emulador fechou sozinha, o erro esta no console.
  echo        Falta de espaco em disco e a causa mais comum.
  pause
  exit /b 1
)
"%SYS%\PING.EXE" -n 3 127.0.0.1 >nul
goto esperar_boot

:boot_ok
echo [3/3] Instalando a build mais recente ...

rem Prefere o APK mais novo da raiz (ordenado por data), depois a saida do build.
set "APK="
for /f "delims=" %%f in ('dir /b /o-d "%PROJ%\NexTasks-*-debug.apk" 2^>nul') do if not defined APK set "APK=%PROJ%\%%f"
if not defined APK set "APK=%PROJ%\app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
  echo.
  echo [erro] nenhum APK encontrado. Gere a build com:
  echo        gradlew assembleDebug
  pause
  exit /b 1
)
echo       %APK%
"%ADB%" install -r "%APK%"

echo Abrindo o app ...

rem Desde a 3.22 o app e aberto por um activity-alias (o icone segue a cor de
rem destaque). Iniciar .MainActivity direto nao funciona mais: ela nao e
rem exportada e nao e mais o ponto de entrada.
rem A saida vai para um arquivo temporario: montar esse comando dentro de
rem for /f mistura as aspas do adb com as do proprio for e nao executa.
set "TMPC=%TEMP%\nextasks_launcher.txt"
"%ADB%" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER %PKG% 2>nul | "%SYS%\findstr.exe" /c:"%PKG%/" > "%TMPC%"
set "COMP="
for /f "usebackq delims=" %%c in ("%TMPC%") do set "COMP=%%c"
del "%TMPC%" >nul 2>&1
if not defined COMP set "COMP=%PKG%/.LauncherGreen"
"%ADB%" shell am start -n %COMP% >nul 2>&1

echo.
echo Pronto ^(%COMP%^).
"%SYS%\PING.EXE" -n 4 127.0.0.1 >nul
exit /b 0
