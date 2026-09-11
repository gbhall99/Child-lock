#!/usr/bin/env bash
# One-shot install + permission setup over adb, so nothing needs to be
# toggled by hand on the phone. Apps installed through adb are exempt from
# Android's "restricted settings" gate for sideloaded apps.
#
# Usage: scripts/setup-phone.sh [path/to/childlock.apk]
# Needs: adb on this computer, USB debugging enabled on the phone.
set -euo pipefail

PKG=com.gbhall.childlock
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
GUARD="$PKG/$PKG.guard.GuardAccessibilityService"
TILE="$PKG/.tile.LockTileService"

command -v adb >/dev/null || { echo "adb not found. Install Android platform-tools first."; exit 1; }
[ -f "$APK" ] || { echo "APK not found: $APK"; exit 1; }

echo "Waiting for a device (accept the USB debugging prompt on the phone)..."
adb wait-for-device
adb devices | grep -q "device$" || { echo "No authorised device."; exit 1; }

echo "Installing $APK"
adb install -r -g "$APK"

echo "Allowing restricted settings and display over other apps"
adb shell appops set "$PKG" ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null || true   # Android 13+
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow

echo "Enabling the accessibility guard"
current=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$current" in
  *"$GUARD"*) ;;
  null|"") adb shell settings put secure enabled_accessibility_services "$GUARD" ;;
  *) adb shell settings put secure enabled_accessibility_services "$current:$GUARD" ;;
esac
adb shell settings put secure accessibility_enabled 1
# Make sure Android's floating accessibility button is not pointed at the helper.
# Exact, element-wise edit: substring matching here could corrupt a real
# screen reader's entry in the same list.
targets=$(adb shell settings get secure accessibility_button_targets | tr -d '\r')
case "$targets" in
  null|"") targets="" ;;
esac
if [ -n "$targets" ]; then
  kept=""
  old_ifs=$IFS
  IFS=':'
  for entry in $targets; do
    [ -z "$entry" ] && continue
    [ "$entry" = "$GUARD" ] && continue
    if [ -z "$kept" ]; then kept="$entry"; else kept="$kept:$entry"; fi
  done
  IFS=$old_ifs
  if [ "$kept" != "$targets" ]; then
    if [ -z "$kept" ]; then
      adb shell settings delete secure accessibility_button_targets
    else
      adb shell settings put secure accessibility_button_targets "$kept"
    fi
  fi
fi

echo "Adding the Quick Settings tile"
adb shell cmd statusbar add-tile "$TILE" 2>/dev/null || echo "  (could not add the tile automatically; add it from the Quick Settings editor)"

echo "Opening Child Lock"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true

echo
echo "Done. Permissions card should show everything granted."
