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
set -uo pipefail
APK=${1:-app/build/outputs/apk/play/debug/app-play-debug.apk}
OUT=${2:-store/media}
PKG=com.gbhall.childlock
GUARD="$PKG/$PKG.guard.GuardAccessibilityService"
mkdir -p "$OUT"
LOG="$OUT/demo.log"
: > "$LOG"
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP >/dev/null
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
read -r W H < <(adb shell wm size | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/' | tail -1)
log "display ${W}x${H}"

still() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null && log "still $1"; }

# Taps the centre of the first on-screen node whose text or description
# contains $1. Returns 1, and logs, when nothing matches.
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
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local centre
  centre=$(adb exec-out cat /sdcard/ui.xml | python3 -c "$FIND_NODE" "$1")
  if [ -z "$centre" ]; then log "no node with text '$1'"; return 1; fi
  adb shell input tap $centre
  log "tapped '$1' at $centre"
}

log "install $APK"
adb install -r -g "$APK" >>"$LOG" 2>&1 || { log "install failed"; exit 1; }
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# A target app to hand over: the calculator shows taps doing nothing better
# than most. Fall back to Settings on an image without one.
TARGET=$(adb shell pm list packages | tr -d '\r' | sed -n 's/^package://p' | grep -m1 -E 'calculator' || true)
[ -n "$TARGET" ] || TARGET=com.android.settings
log "target app $TARGET"

# Start recording (host side stops it with SIGINT; 3 minutes is the hard cap).
adb shell rm -f /sdcard/demo.mp4
adb shell screenrecord --bit-rate 8000000 --time-limit 170 /sdcard/demo.mp4 &
REC=$!
sleep 2

# 1. Setup guide and the accessibility disclosure.
adb shell am start -n "$PKG/.ui.SetupActivity" >/dev/null
sleep 3; still 1-setup
if tap_text "Open settings"; then
  sleep 4; still 2-disclosure
  if tap_text "Continue"; then
    # Android's accessibility settings: open the service, switch it on, allow.
    sleep 3
    tap_text "Child Lock helper" && sleep 2 && still 3-a11y-settings
    tap_text "Use Child Lock helper" && sleep 2
    tap_text "Allow" && sleep 2 && still 4-a11y-allowed
  fi
fi
if ! adb shell settings get secure enabled_accessibility_services | grep -q "$PKG"; then
  log "enabling the helper by settings (UI path did not)"
  adb shell settings put secure enabled_accessibility_services "$GUARD"
  adb shell settings put secure accessibility_enabled 1
fi
sleep 1

# 2. Back to the guide: acknowledge the way out, finish.
adb shell am start -n "$PKG/.ui.SetupActivity" >/dev/null
sleep 2
tap_text "Got it" && sleep 3 && still 5-escape
tap_text "Done" && sleep 2

# 3. Hand over: open the target app, then arm from Child Lock.
adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3
adb shell am start -n "$PKG/.ui.MainActivity" >/dev/null
sleep 3; still 6-home
before=$(adb shell dumpsys window windows | grep -c "$PKG")
if tap_text "Lock in"; then
  sleep 13
else
  log "no Lock button; arming with the volume pattern instead"
  adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 2
  adb shell input keyevent KEYCODE_VOLUME_UP; sleep 0.4; adb shell input keyevent KEYCODE_VOLUME_DOWN
  sleep 13
fi
locked=$(adb shell dumpsys window windows | grep -c "$PKG")
log "windows for $PKG before arming: $before, now: $locked (more means the shield is up)"
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
after=$(adb shell dumpsys window windows | grep -c "$PKG")
log "windows for $PKG after the prods: $after"

# 5. The volume pattern unlocks.
adb shell input keyevent KEYCODE_VOLUME_UP; sleep 0.4; adb shell input keyevent KEYCODE_VOLUME_DOWN
sleep 4
unlocked=$(adb shell dumpsys window windows | grep -c "$PKG")
log "windows for $PKG after the pattern: $unlocked (back to the arming count means unlocked)"
still 9-unlocked
adb shell am start -n "$PKG/.ui.MainActivity" >/dev/null
sleep 3; still 10-home-after

# Stop and collect.
adb shell pkill -2 screenrecord || true
wait $REC 2>/dev/null || true
sleep 3
adb pull /sdcard/demo.mp4 "$OUT/declaration-video.mp4" >>"$LOG" 2>&1 && log "video $(du -h "$OUT/declaration-video.mp4" | cut -f1)"
if [ "$locked" -le "$before" ]; then log "WARNING: the shield never appeared; the clip does not show a lock"; fi
if [ "$unlocked" -ge "$locked" ] && [ "$locked" -gt "$before" ]; then log "WARNING: the volume pattern did not unlock"; fi
ls -la "$OUT" | tee -a "$LOG"
