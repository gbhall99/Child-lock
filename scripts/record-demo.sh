#!/usr/bin/env bash
# Drives the play-debug build on a running emulator and records the video the
# Play Console declarations ask for: the setup guide and its disclosure,
# enabling the helper, arming from the app, the badge while locked, swipes
# and taps achieving nothing, the volume pattern unlocking.
#
# Usage: scripts/record-demo.sh [apk] [out-dir]
# Needs adb on PATH and one booted emulator (see .github/workflows/store-media.yml).
# Writes out-dir/declaration-video.mp4 plus PNG stills of the key moments and
# demo.log with what happened, so a silent failure never passes as a clip.
#
# Three emulator facts shape this script. A fresh CI emulator is slow: the
# app's first start can take 15 s and the launcher throws "isn't responding"
# dialogs, so every tap waits for its text and the app is warmed up before
# recording starts. Keys injected with `input keyevent` bypass the
# accessibility key filter, so the volume pattern is written to the input
# device as raw evdev events (needs adb root; the emulator console is tried
# first and has not worked on API 34). And the main activity redirects to the
# setup guide once per process, so the process is restarted before each
# on-camera visit to the guide.
set -uo pipefail
APK=${1:-app/build/outputs/apk/play/debug/app-play-debug.apk}
OUT=${2:-store/media}
PKG=com.gbhall.childlock
GUARD="$PKG/$PKG.guard.GuardAccessibilityService"
mkdir -p "$OUT"
LOG="$OUT/demo.log"
: > "$LOG"
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }

# ---- helpers ---------------------------------------------------------------

still() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null && log "still $1"; }

FIND_NODE='
import re, sys
import xml.etree.ElementTree as ET
needle = sys.argv[1]
data = sys.stdin.read()
try:
    root = ET.fromstring(data[data.index("<hierarchy"):])
except Exception:
    sys.exit(1)
for node in root.iter("node"):
    if needle in node.get("text", "") or needle in node.get("content-desc", ""):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
'
# Centre of the first node whose text or description contains $1, or nothing.
find_text() {
  adb shell rm -f /sdcard/ui.xml
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml 2>/dev/null | python3 -c "$FIND_NODE" "$1"
}
# Waits up to $2 seconds (default 25) for text $1 to be on screen.
wait_text() {
  local t=0 max=${2:-25} c=""
  while [ "$t" -lt "$max" ]; do
    c=$(find_text "$1"); [ -n "$c" ] && { echo "$c"; return 0; }
    sleep 2; t=$((t + 2))
  done
  return 1
}
# Waits for text $1, taps it. Logs the texts on screen when it never shows.
tap_text() {
  local centre
  centre=$(wait_text "$1" "${2:-25}")
  if [ -z "$centre" ]; then
    log "no node with text '$1' after ${2:-25}s; on screen: $(adb exec-out cat /sdcard/ui.xml 2>/dev/null | grep -o 'text="[^"]\+"' | head -8 | tr '\n' ' ')"
    return 1
  fi
  adb shell input tap $centre
  log "tapped '$1' at $centre"
}
# The shield window is titled "ChildLock"; it exists only while locked or arming.
shield_up() { adb shell dumpsys window windows | grep -q 'ChildLock[^B]'; }
guard_on() { adb shell dumpsys accessibility | grep -q GuardAccessibilityService; }
# Volume presses. `input keyevent` bypasses the accessibility key filter, so
# the pattern is tried through the emulator console (named, then numeric
# Linux codes) and as raw evdev writes; "inject" is the last resort.
KEY_DEV=""
console_key() { adb emu event send "EV_KEY:$1:1" >/dev/null 2>&1; adb emu event send "EV_KEY:$1:0" >/dev/null 2>&1; }
raw_key() {
  [ -n "$KEY_DEV" ] || KEY_DEV=$(adb shell getevent -pl 2>/dev/null | tr -d '\r' | awk '/^add device/ {dev=$NF} /KEY_VOLUMEUP/ {print dev; exit}')
  [ -n "$KEY_DEV" ] || return 1
  adb shell sendevent "$KEY_DEV" 1 "$1" 1; adb shell sendevent "$KEY_DEV" 0 0 0
  adb shell sendevent "$KEY_DEV" 1 "$1" 0; adb shell sendevent "$KEY_DEV" 0 0 0
}
# Up then down, through one delivery path: console | numeric | raw | inject.
pattern_via() {
  case "$1" in
    console) console_key KEY_VOLUMEUP; sleep 0.4; console_key KEY_VOLUMEDOWN ;;
    numeric) console_key 115; sleep 0.4; console_key 114 ;;
    raw) raw_key 115 || return 1; sleep 0.4; raw_key 114 ;;
    inject) adb shell input keyevent KEYCODE_VOLUME_UP; sleep 0.4; adb shell input keyevent KEYCODE_VOLUME_DOWN ;;
  esac
}
# Tries each path until the shield state flips; remembers what worked.
PATTERN_PATH=""
pattern() {
  local want_up=$1 m   # 1: expecting the shield to come up; 0: to go away
  for m in ${PATTERN_PATH:-console raw numeric inject}; do
    pattern_via "$m" || { log "pattern via $m unavailable"; continue; }
    sleep 3
    if { [ "$want_up" = 1 ] && shield_up; } || { [ "$want_up" = 0 ] && ! shield_up; }; then
      log "pattern via $m worked"; PATTERN_PATH=$m; return 0
    fi
    log "pattern via $m did nothing"
  done
  return 1
}
# The setup activity is not exported, so it is reached through the main
# activity, which redirects to it until the guide is finished.
launch() { adb shell am start -W -n "$PKG/$1" >/dev/null 2>&1; }

# ---- prepare ---------------------------------------------------------------

adb wait-for-device
adb root >/dev/null 2>&1 && adb wait-for-device && sleep 2   # raw key writes need it
log "console says: $(adb emu event send EV_KEY:KEY_VOLUMEUP:0 2>&1 | tr -d '\r' | tr '\n' ' ')"
# Error dialogs from a slow launcher would sit over everything and break the
# UI dumps; the screen must not go off mid-clip.
adb shell settings put global hide_error_dialogs 1
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent KEYCODE_WAKEUP >/dev/null
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
read -r W H < <(adb shell wm size | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/' | tail -1)
log "display ${W}x${H}"

log "install $APK"
adb install -r -g "$APK" >>"$LOG" 2>&1 || { log "install failed"; exit 1; }
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# A target app to hand over. Settings is on every image; a calculator is nicer.
TARGET=$(adb shell pm list packages | tr -d '\r' | sed -n 's/^package://p' | grep -m1 -E 'calculator' || true)
[ -n "$TARGET" ] || TARGET=com.android.settings
log "target app $TARGET"

# Warm everything up off camera: the app's first start, and the target app.
log "warming up"
launch .ui.MainActivity
wait_text "Three quick steps" 60 >/dev/null && log "setup guide up" || log "WARNING: setup guide not seen during warm-up"
adb shell input keyevent KEYCODE_HOME >/dev/null; sleep 2
adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
adb shell input keyevent KEYCODE_HOME >/dev/null; sleep 2

# The main activity only redirects to the guide once per process, so the
# process is restarted before each on-camera visit to the guide.
fresh_guide() {
  adb shell am force-stop "$PKG"; sleep 1
  launch .ui.MainActivity
  wait_text "Three quick steps" 20 >/dev/null
}
# A force-stop unbinds the helper; the system rebinds it on the next launch,
# and toggling the setting hurries that along when it does not.
ensure_guard() {
  local try
  for try in 1 2 3 4 5; do guard_on && return 0; sleep 2; done
  adb shell settings put secure enabled_accessibility_services ""
  sleep 1
  adb shell settings put secure enabled_accessibility_services "$GUARD"
  adb shell settings put secure accessibility_enabled 1
  for try in 1 2 3 4 5; do guard_on && return 0; sleep 2; done
  return 1
}

# ---- record ------------------------------------------------------------------

adb shell rm -f /sdcard/demo.mp4
adb shell screenrecord --bit-rate 8000000 --time-limit 180 /sdcard/demo.mp4 &
REC=$!
sleep 2

# 1. Setup guide and the accessibility disclosure.
fresh_guide; sleep 2; still 1-setup
if tap_text "Open settings" 10; then
  wait_text "Why Child Lock needs this" 10 >/dev/null; sleep 4; still 2-disclosure
  if tap_text "Continue" 10; then
    # Android's accessibility settings: open the service, switch it on, allow.
    tap_text "Child Lock helper" 15 && sleep 2 && still 3-a11y-settings
    tap_text "Use Child Lock helper" 10 && sleep 2
    tap_text "Allow" 10 && sleep 2 && still 4-a11y-allowed
  fi
fi
if ! adb shell settings get secure enabled_accessibility_services | grep -q "$PKG"; then
  log "enabling the helper by settings (UI path did not)"
  adb shell settings put secure enabled_accessibility_services "$GUARD"
  adb shell settings put secure accessibility_enabled 1
fi
ensure_guard && log "helper is bound" || log "WARNING: helper not bound"

# 2. Back to the guide: acknowledge the way out, finish.
fresh_guide
tap_text "Got it" 10 && sleep 3 && still 5-escape
tap_text "Done" 10 && sleep 2
ensure_guard && log "helper is bound after the guide" || log "WARNING: helper not bound after the guide"

# 3. Hand over: open the target app, then arm from Child Lock.
adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4
launch .ui.MainActivity
wait_text "Child Lock" 20 >/dev/null; sleep 2; still 6-home
if tap_text "Lock in" 10; then
  sleep 4; still 6b-countdown; sleep 9
else
  log "no Lock button; arming with the volume pattern instead"
  adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 3
  pattern 1
fi
if shield_up; then locked=1; log "shield is up"; else locked=0; log "WARNING: shield is not up"; fi
still 7-locked

# 4. Prods at a locked phone: taps, swipes from every edge, back, the shade.
cx=$((W / 2)); cy=$((H / 2))
adb shell input tap $((W / 4)) $((H * 3 / 4)); sleep 1
adb shell input tap $((W * 3 / 4)) $((H * 3 / 4)); sleep 1
adb shell input swipe $cx 5 $cx $cy 300; sleep 1.5                # shade
adb shell input swipe $cx $((H - 5)) $cx $cy 300; sleep 1.5       # home
adb shell input swipe 5 $cy $((W * 2 / 3)) $cy 300; sleep 1.5     # back gesture
adb shell input swipe $((W - 5)) $cy $((W / 3)) $cy 300; sleep 1.5
adb shell input keyevent KEYCODE_BACK; sleep 1.5
adb shell input keyevent KEYCODE_HOME; sleep 2
still 8-after-swipes
if shield_up; then log "shield still up after the prods"; else log "WARNING: shield gone after the prods"; fi

# 5. The volume pattern unlocks.
pattern 0
if shield_up; then unlocked=0; log "WARNING: shield still up after the pattern"; else unlocked=1; log "shield down: unlocked"; fi
still 9-unlocked
launch .ui.MainActivity
sleep 3; still 10-home-after

# ---- collect -----------------------------------------------------------------

adb shell pkill -2 screenrecord || true
wait $REC 2>/dev/null || true
sleep 3
adb pull /sdcard/demo.mp4 "$OUT/declaration-video.mp4" >>"$LOG" 2>&1 && log "video $(du -h "$OUT/declaration-video.mp4" | cut -f1)"
if [ "$locked" = 1 ] && [ "$unlocked" = 1 ]; then log "RESULT: lock and unlock both shown"; else log "RESULT: incomplete, see warnings above"; fi
ls -la "$OUT" | tee -a "$LOG"
