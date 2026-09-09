@echo off
rem One-shot install + permission setup over adb (Windows).
rem Usage: scripts\setup-phone.cmd [path\to\childlock.apk]
rem Needs: adb on this computer, USB debugging enabled on the phone.
setlocal
set PKG=com.gbhall.childlock
set APK=%~1
if "%APK%"=="" set APK=app\build\outputs\apk\debug\app-debug.apk
set GUARD=%PKG%/%PKG%.guard.GuardAccessibilityService
set TILE=%PKG%/.tile.LockTileService

where adb >nul 2>nul || (echo adb not found. Install Android platform-tools first. & exit /b 1)
if not exist "%APK%" (echo APK not found: %APK% & exit /b 1)

echo Waiting for a device (accept the USB debugging prompt on the phone)...
adb wait-for-device

echo Installing %APK%
adb install -r -g "%APK%" || exit /b 1

echo Allowing restricted settings and display over other apps
adb shell appops set %PKG% ACCESS_RESTRICTED_SETTINGS allow >nul 2>nul
adb shell appops set %PKG% SYSTEM_ALERT_WINDOW allow

echo Enabling the accessibility guard
for /f "usebackq delims=" %%s in (`adb shell settings get secure enabled_accessibility_services`) do set CURRENT=%%s
if "%CURRENT%"=="null" set CURRENT=
echo %CURRENT% | find "%GUARD%" >nul
if errorlevel 1 (
  if "%CURRENT%"=="" (
    adb shell settings put secure enabled_accessibility_services "%GUARD%"
  ) else (
    adb shell settings put secure enabled_accessibility_services "%CURRENT%:%GUARD%"
  )
)
adb shell settings put secure accessibility_enabled 1

echo Adding the Quick Settings tile
adb shell cmd statusbar add-tile %TILE% >nul 2>nul

echo Opening Child Lock
adb shell monkey -p %PKG% -c android.intent.category.LAUNCHER 1 >nul 2>nul

echo.
echo Done. Permissions card should show everything granted.
endlocal
