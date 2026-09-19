#!/usr/bin/env bash
# Drives the play-debug build on a running emulator and records the video the
# Play Console declarations ask for: the setup guide and its disclosure,
# enabling the helper, the volume pattern locking, the badge while locked,
# taps and swipes achieving nothing, the volume pattern unlocking.
#
# Usage: scripts/record-demo.sh [apk] [out-dir]
# Needs adb on PATH and one booted emulator (see .github/workflows/store-media.yml).
# Writes out-dir/declaration-video.mp4 (the scenes, cut tight and captioned,
# when ffmpeg is available), raw-recording.mp4, PNG stills of the key
# moments, and demo.log with what happened, so a silent failure never passes
# as a clip.
#
# Three emulator facts shape this script. A fresh CI emulator is slow: the
# app's first start can take 15 s and the launcher throws "isn't responding"
# dialogs, so every tap waits for its text and the app is warmed up before
# recording starts. Keys injected with `input keyevent` bypass the
# accessibility key filter, so the volume pattern is written to the input
# device as raw evdev events (needs adb root; the emulator console is tried
# next and has not worked on API 34). And the main activity redirects to the
# setup guide once per process, so the process is restarted before each
# on-camera visit to the guide.
set -uo pipefail
APK=${1:-app/build/outputs/apk/play/debug/app-play-debug.apk}
OUT=${2:-store/media}
PKG=com.gbhall.childlock
GUARD="$PKG/$PKG.guard.GuardAccessibilityService"
mkdir -p "$OUT"
LOG="$OUT/demo.log"
SCENES="$OUT/scenes.txt"
: > "$LOG"; : > "$SCENES"
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }

# ---- helpers ---------------------------------------------------------------

still() { adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null && log "still $1"; }

FIND_NODE='
import re, sys
import xml.etree.ElementTree as ET
needles = sys.argv[1:]
data = sys.stdin.read()
try:
    root = ET.fromstring(data[data.index("<hierarchy"):])
except Exception:
    sys.exit(1)
def centre(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    return ((int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2) if m else None
nodes = list(root.iter("node"))
for n in needles:
    for node in nodes:
        if n in (node.get("text", "").strip(), node.get("content-desc", "").strip()):
            c = centre(node)
            if c: print(*c); sys.exit(0)
for n in needles:
    for node in nodes:
        if n in node.get("text", "") or n in node.get("content-desc", ""):
            c = centre(node)
            if c: print(*c); sys.exit(0)
sys.exit(1)
'
# Centre of the first node whose text or description contains any argument.
find_text() {
  adb shell rm -f /sdcard/ui.xml
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml 2>/dev/null | python3 -c "$FIND_NODE" "$@"
}
# One dump; taps the first of the given texts that is on screen, if any.
tap_any() {
  local centre
  centre=$(find_text "$@")
  [ -n "$centre" ] || return 1
  adb shell input tap $centre
  log "dismissed a prompt at $centre"
}
# Waits up to $2 seconds (default 25) for text $1 to be on screen.
wait_text() {
  local t=0 max=${2:-25} c=""
  while [ "$t" -lt "$max" ]; do
    c=$(find_text "$1"); [ -n "$c" ] && { echo "$c"; return 0; }
    sleep 1; t=$((t + 1))
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
tap_at() { adb shell input tap $1; log "tapped at $1"; }
# The shield window is titled "ChildLock"; it exists only while locked or arming.
shield_up() { adb shell dumpsys window windows | grep -q 'ChildLock[^B]'; }
guard_on() { adb shell dumpsys accessibility | grep -q GuardAccessibilityService; }

# Volume presses. `input keyevent` bypasses the accessibility key filter, so
# the pattern goes in as raw evdev writes first, then the emulator console
# (named, then numeric Linux codes); "inject" is the last resort.
KEY_DEV=""
console_key() { adb emu event send "EV_KEY:$1:1" >/dev/null 2>&1; adb emu event send "EV_KEY:$1:0" >/dev/null 2>&1; }
find_key_dev() {
  [ -n "$KEY_DEV" ] || KEY_DEV=$(adb shell getevent -pl 2>/dev/null | tr -d '\r' | awk '/^add device/ {dev=$NF} /KEY_VOLUMEUP/ {print dev; exit}')
  [ -n "$KEY_DEV" ]
}
raw_key() {
  find_key_dev || return 1
  adb shell "K=$KEY_DEV; sendevent \$K 1 $1 1; sendevent \$K 0 0 0; sleep 0.08; sendevent \$K 1 $1 0; sendevent \$K 0 0 0"
}
# Volume up, then down, 300 ms apart, as one command on the device.
raw_pattern() {
  find_key_dev || return 1
  adb shell "K=$KEY_DEV; sendevent \$K 1 115 1; sendevent \$K 0 0 0; sleep 0.08; sendevent \$K 1 115 0; sendevent \$K 0 0 0; sleep 0.3; sendevent \$K 1 114 1; sendevent \$K 0 0 0; sleep 0.08; sendevent \$K 1 114 0; sendevent \$K 0 0 0"
}
# Touches the same way: written to the touchscreen device (multitouch
# protocol B), so touch exploration and the shield see them as a finger.
# `input tap/swipe` would bypass that layer and reach the launcher.
TOUCH_DEV=""; TMAXX=0; TMAXY=0
find_touch() {
  local info
  info=$(adb shell getevent -pl 2>/dev/null | tr -d '\r')
  TOUCH_DEV=$(echo "$info" | awk '/^add device/ {dev=$NF} /ABS_MT_POSITION_X/ {print dev; exit}')
  [ -n "$TOUCH_DEV" ] || return 1
  TMAXX=$(echo "$info" | awk -v d="$TOUCH_DEV" '/^add device/ {cur=$NF} cur==d && /ABS_MT_POSITION_X/ {for (i=1;i<=NF;i++) if ($i=="max") {print $(i+1)+0; exit}}')
  TMAXY=$(echo "$info" | awk -v d="$TOUCH_DEV" '/^add device/ {cur=$NF} cur==d && /ABS_MT_POSITION_Y/ {for (i=1;i<=NF;i++) if ($i=="max") {print $(i+1)+0; exit}}')
  [ "${TMAXX:-0}" -gt 0 ] && [ "${TMAXY:-0}" -gt 0 ]
}
tx() { awk "BEGIN { printf \"%d\", $1 * $TMAXX / ($W - 1) }"; }
ty() { awk "BEGIN { printf \"%d\", $1 * $TMAXY / ($H - 1) }"; }
# tap X Y (screen pixels)
tap() {
  if [ -z "$TOUCH_DEV" ]; then adb shell input tap "$1" "$2"; return; fi
  local x y; x=$(tx "$1"); y=$(ty "$2")
  adb shell "D=$TOUCH_DEV; sendevent \$D 3 47 0; sendevent \$D 3 57 $RANDOM; sendevent \$D 3 53 $x; sendevent \$D 3 54 $y; sendevent \$D 1 330 1; sendevent \$D 0 0 0; sleep 0.08; sendevent \$D 3 57 4294967295; sendevent \$D 1 330 0; sendevent \$D 0 0 0"
}
# swipe X1 Y1 X2 Y2 (screen pixels), about 400 ms
swipe() {
  if [ -z "$TOUCH_DEV" ]; then adb shell input swipe "$1" "$2" "$3" "$4" 400; return; fi
  local x1 y1 x2 y2 n=12; x1=$(tx "$1"); y1=$(ty "$2"); x2=$(tx "$3"); y2=$(ty "$4")
  adb shell "D=$TOUCH_DEV; sendevent \$D 3 47 0; sendevent \$D 3 57 $RANDOM; sendevent \$D 3 53 $x1; sendevent \$D 3 54 $y1; sendevent \$D 1 330 1; sendevent \$D 0 0 0; i=1; while [ \$i -le $n ]; do x=\$(( $x1 + ($x2 - $x1) * \$i / $n )); y=\$(( $y1 + ($y2 - $y1) * \$i / $n )); sendevent \$D 3 53 \$x; sendevent \$D 3 54 \$y; sendevent \$D 0 0 0; sleep 0.03; i=\$((i + 1)); done; sendevent \$D 3 57 4294967295; sendevent \$D 1 330 0; sendevent \$D 0 0 0"
}
back_key() { raw_key 158 || adb shell input keyevent KEYCODE_BACK; }

# Up then down, through one delivery path: raw | console | numeric | inject.
pattern_via() {
  case "$1" in
    raw) raw_pattern || return 1 ;;
    console) console_key KEY_VOLUMEUP; sleep 0.5; console_key KEY_VOLUMEDOWN ;;
    numeric) console_key 115; sleep 0.5; console_key 114 ;;
    inject) adb shell input keyevent KEYCODE_VOLUME_UP; sleep 0.5; adb shell input keyevent KEYCODE_VOLUME_DOWN ;;
  esac
}
# Tries each path until the shield state flips; remembers what worked.
PATTERN_PATH=""
pattern() {
  local want_up=$1 m   # 1: expecting the shield to come up; 0: to go away
  for m in ${PATTERN_PATH:+$PATTERN_PATH raw raw} ${PATTERN_PATH:-raw raw console numeric inject}; do
    pattern_via "$m" || { log "pattern via $m unavailable"; continue; }
    sleep 2
    if { [ "$want_up" = 1 ] && shield_up; } || { [ "$want_up" = 0 ] && ! shield_up; }; then
      log "pattern via $m worked"; PATTERN_PATH=$m; return 0
    fi
    log "pattern via $m did nothing"
  done
  return 1
}
launch() { adb shell am start -W -n "$PKG/$1" >/dev/null 2>&1; }
open_target() { adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }
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

# Scenes: what the final clip keeps, with a caption each. Times are seconds
# since the recording started; everything between scenes is cut.
REC_T0=0
now_rel() { awk "BEGIN { printf \"%.2f\", $(date +%s.%N) - $REC_T0 }"; }
SCENE_T=0; SCENE_CAP=""
scene() { SCENE_CAP=$1; SCENE_T=$(now_rel); log "scene: $1"; }
scene_end() { echo "$SCENE_T $(now_rel) $SCENE_CAP" >> "$SCENES"; }

# ---- prepare ---------------------------------------------------------------

adb wait-for-device
adb root >/dev/null 2>&1 && adb wait-for-device && sleep 2   # raw key writes need it
# Error dialogs from a slow launcher would sit over everything and break the
# UI dumps; the screen must not go off mid-clip; taps and swipes draw a dot.
adb shell settings put global hide_error_dialogs 1
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 1800000
adb shell settings put system show_touches 1
adb shell settings put system time_12_24 24
adb shell input keyevent KEYCODE_WAKEUP >/dev/null
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
read -r W H < <(adb shell wm size | sed -E 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/' | tail -1)
log "display ${W}x${H}"

log "install $APK"
adb install -r -g -i com.android.vending "$APK" >>"$LOG" 2>&1 || { log "install failed"; exit 1; }   # -i: installer is Play
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
# Android 13+ gates the accessibility toggle for apps installed by adb; a
# Play install is not gated, so lift it to show what a Play user sees.
adb shell appops set "$PKG" ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null || true
find_touch && log "touchscreen $TOUCH_DEV (${TMAXX}x${TMAXY})" || log "WARNING: no touchscreen device found; using input injection"

# What the phone is handed over with: a cartoon playing in Google Photos'
# player. YouTube is out: from a CI address it demands a sign-in "to confirm
# you're not a bot", and YouTube Kids is not on the image. Big Buck Bunny is
# CC BY 3.0 (Blender Foundation). Chrome's native player, then Settings, are
# the fallbacks.
CARTOON_URLS="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4 https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4 https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_640x360.m4v"
CARTOON_URL=""
CARTOON=/sdcard/Movies/Big_Buck_Bunny.mp4
CARTOON_ID=""
TARGET=com.android.settings
HANDOVER=settings
focus_is() { adb shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -qi "$1"; }
dismiss_prompts() {
  tap_any "Allow all" "Allow" "No thanks" "NO THANKS" "Not now" "Skip" "SKIP" "Use without an account" "Accept & continue" "Dismiss" "Got it" "OK" || true
}
show_video() {
  case "$HANDOVER" in
    photos) adb shell am start -a android.intent.action.VIEW -d "content://media/external/video/media/$CARTOON_ID" -t video/mp4 -p com.google.android.apps.photos >/dev/null 2>&1 ;;
    chrome) adb shell am start -a android.intent.action.VIEW -d "$CARTOON_URL" -p com.android.chrome >/dev/null 2>&1 ;;
    *) open_target ;;
  esac
}
choose_handover() {
  local tmp="${RUNNER_TEMP:-/tmp}/bunny.mp4" try u
  for u in $CARTOON_URLS; do
    rm -f "$tmp"
    if curl -fsSL -m 600 -o "$tmp" "$u" && [ "$(stat -c %s "$tmp" 2>/dev/null || echo 0)" -gt 1000000 ]; then
      CARTOON_URL=$u; log "cartoon from $u ($(du -h "$tmp" | cut -f1))"; break
    fi
    log "cartoon not available at $u"
  done
  if [ -s "$tmp" ] && adb shell pm list packages | grep -q com.google.android.apps.photos; then
    adb shell mkdir -p /sdcard/Movies
    adb push "$tmp" "$CARTOON" >/dev/null 2>&1
    if command -v ffmpeg >/dev/null 2>&1; then
      local light="${RUNNER_TEMP:-/tmp}/bunny-light.mp4"
      if ffmpeg -loglevel error -y -ss 60 -t 150 -i "$tmp" -vf scale=480:-2,fps=24 -c:v libx264 -profile:v baseline -preset veryfast -crf 30 -an -movflags +faststart "$light" 2>/dev/null && [ -s "$light" ]; then
        log "cartoon transcoded to 480x270 for a responsive emulator ($(du -h "$light" | cut -f1))"
        adb push "$light" "$CARTOON" >/dev/null 2>&1
      fi
    fi
    log "on device: $(adb shell ls -la /storage/emulated/0/Movies/ 2>/dev/null | tr -d '\r' | grep -i bunny)"
    cartoon_id() {
      adb shell content query --uri content://media/external/video/media --projection _id:_display_name 2>/dev/null | tr -d '\r' | grep -i Big_Buck_Bunny | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1
    }
    adb shell content call --uri content://media/external/file --method scan_file --arg /storage/emulated/0/Movies/Big_Buck_Bunny.mp4 >/dev/null 2>&1
    sleep 2; CARTOON_ID=$(cartoon_id)
    if [ -z "$CARTOON_ID" ]; then
      adb shell content call --uri content://media/external/file --method scan_volume --arg external_primary >/dev/null 2>&1
      sleep 4; CARTOON_ID=$(cartoon_id)
    fi
    if [ -z "$CARTOON_ID" ]; then
      adb shell content insert --uri content://media/external/video/media --bind _data:s:/storage/emulated/0/Movies/Big_Buck_Bunny.mp4 --bind _display_name:s:Big_Buck_Bunny.mp4 --bind mime_type:s:video/mp4 --bind title:s:Big_Buck_Bunny >/dev/null 2>&1
      sleep 2; CARTOON_ID=$(cartoon_id)
    fi
    [ -n "$CARTOON_ID" ] && log "cartoon is media id $CARTOON_ID"
    if [ -n "$CARTOON_ID" ]; then
      HANDOVER=photos; show_video; sleep 8
      dismiss_prompts; sleep 2; dismiss_prompts; sleep 2
      for try in 1 2 3; do focus_is photos && break; sleep 2; done
      if focus_is photos; then TARGET=com.google.android.apps.photos; log "hand-over: cartoon in Google Photos"; return; fi
      log "Photos did not come up: $(adb shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r')"
    else
      log "the cartoon never appeared in the media store"
    fi
  fi
  if adb shell pm list packages | grep -q com.android.chrome; then
    HANDOVER=chrome; show_video; sleep 10
    dismiss_prompts; sleep 2; dismiss_prompts; sleep 2
    tap $((W / 2)) $((H / 2)); sleep 5   # the native player wants one tap to play
    dismiss_prompts; sleep 2             # Chrome's notifications prompt comes late
    for try in 1 2 3; do focus_is chrome && break; sleep 2; done
    if focus_is chrome; then TARGET=com.android.chrome; log "hand-over: cartoon in Chrome"; return; fi
    log "Chrome did not come up: $(adb shell dumpsys window | grep -m1 mCurrentFocus | tr -d '\r')"
  fi
  HANDOVER=settings; TARGET=com.android.settings; open_target; sleep 2; log "hand-over: Settings"
}

# Warm everything up off camera: the app's first start, and the hand-over app.
log "warming up"
launch .ui.MainActivity
wait_text "Three quick steps" 60 >/dev/null && log "setup guide up" || log "WARNING: setup guide not seen during warm-up"
adb shell input keyevent KEYCODE_HOME >/dev/null; sleep 1
choose_handover
adb shell input keyevent KEYCODE_HOME >/dev/null; sleep 1

# ---- record ------------------------------------------------------------------

adb shell rm -f /sdcard/demo.mp4
adb shell screenrecord --bit-rate 8000000 --time-limit 180 /sdcard/demo.mp4 &
REC=$!
# Scene times count from when the recorder actually starts writing, which is
# a second or two after it is launched.
for try in $(seq 1 50); do adb shell ls /sdcard/demo.mp4 >/dev/null 2>&1 && break; sleep 0.2; done
REC_T0=$(date +%s.%N)
sleep 0.5

# 1. Setup guide and the accessibility disclosure.
fresh_guide
scene "The setup guide"; sleep 2.5; still 1-setup; scene_end
if tap_text "Open settings" 10 && wait_text "Why Child Lock needs this" 10 >/dev/null; then
  scene "Disclosure before enabling the helper"; sleep 7; still 2-disclosure; scene_end
  c=$(wait_text "Continue" 5)
  scene "Continue to Android's accessibility settings"; [ -n "$c" ] && tap_at "$c"; sleep 2.5; scene_end
  c=$(wait_text "Child Lock helper" 15)
  scene "Open Child Lock helper"; [ -n "$c" ] && tap_at "$c"; sleep 2; scene_end
  still 3-a11y-settings
  c=$(wait_text "Use Child Lock helper" 10)
  scene "Switch it on"; [ -n "$c" ] && tap_at "$c"; sleep 2.5; scene_end
  c=$(wait_text "Allow" 10)
  scene "Allow"; [ -n "$c" ] && tap_at "$c"; sleep 2; scene_end
  still 4-a11y-allowed
fi
for try in 1 2 3 4; do adb shell settings get secure enabled_accessibility_services | grep -q "$PKG" && break; sleep 2; done
if ! adb shell settings get secure enabled_accessibility_services | grep -q "$PKG"; then
  log "enabling the helper by settings (UI path did not)"
  adb shell settings put secure enabled_accessibility_services "$GUARD"
  adb shell settings put secure accessibility_enabled 1
fi
ensure_guard && log "helper is bound" || log "WARNING: helper not bound"

# 2. Back to the guide: acknowledge the way out, finish.
fresh_guide
c=$(wait_text "Got it" 10)
if [ -n "$c" ]; then
  scene "The way out is explained first"; sleep 2.5; tap_at "$c"; sleep 1.5; scene_end
  still 5-escape
  tap_text "Done" 10
fi
ensure_guard && log "helper is bound after the guide" || log "WARNING: helper not bound after the guide"

# 3. Hand over with the video playing, then the volume pattern locks.
show_video; sleep 6
[ "$HANDOVER" = chrome ] && { dismiss_prompts; sleep 1; tap $((W / 2)) $((H * 27 / 100)); sleep 2; }
scene "Hand over with a video playing"; sleep 3; scene_end
scene "Volume up then volume down = locked"
if pattern 1; then locked=1; else
  log "pattern did not lock; arming from the app instead"
  launch .ui.MainActivity; tap_text "Lock in" 10 && sleep 13
  if shield_up; then locked=1; else locked=0; log "WARNING: shield is not up"; fi
fi
sleep 1; scene_end
still 7-locked

# 4. Prods at a locked phone, as a finger makes them: taps, swipes from
# every edge (shade, home gesture, back gestures), the back key.
cx=$((W / 2)); cy=$((H / 2))
scene "Taps and swipes do nothing"
tap $((W / 4)) $((H * 3 / 4)); sleep 0.6
tap $((W * 3 / 4)) $((H / 2)); sleep 0.6
swipe $cx 5 $cx $cy; sleep 0.8                 # shade
swipe $cx $((H - 5)) $cx $cy; sleep 0.8        # home gesture
swipe 5 $cy $((W * 2 / 3)) $cy; sleep 0.8      # back gesture, left edge
scene_end
scene "The back key does nothing"
back_key; sleep 1.5
scene_end
still 8-after-swipes
if shield_up; then log "shield still up after the prods"; else log "WARNING: shield gone after the prods"; fi

# 5. The volume pattern unlocks.
scene "Volume up then volume down = unlocked"
if pattern 0; then unlocked=1; else
  unlocked=0; log "WARNING: shield still up after the pattern; unlocking through the service so the clip ends unlocked"
  adb shell am start-foreground-service -n "$PKG/.lock.LockOverlayService" -a com.gbhall.childlock.action.UNLOCK >/dev/null 2>&1
  sleep 2
fi
sleep 1; scene_end
still 9-unlocked
launch .ui.MainActivity; sleep 1.5
# screenrecord only writes frames when the screen changes, so the file ends
# at the last change: the closing scene has to move, or it falls off the end.
scene "Unlocked - touch works again"
adb shell input swipe $cx $((H * 7 / 10)) $cx $((H * 4 / 10)) 400; sleep 1
adb shell input swipe $cx $((H * 4 / 10)) $cx $((H * 7 / 10)) 400; sleep 1
still 10-home-after; scene_end
adb shell input tap $cx $((H / 3)); sleep 1

# ---- collect -----------------------------------------------------------------

sleep 4   # the recording must outlast the last scene
adb shell pkill -2 screenrecord || true
wait $REC 2>/dev/null || true
sleep 3
adb pull /sdcard/demo.mp4 "$OUT/raw-recording.mp4" >>"$LOG" 2>&1 && log "raw video $(du -h "$OUT/raw-recording.mp4" | cut -f1)"
adb shell settings put system show_touches 0

# Cuts the raw recording down to the scenes in one ffmpeg run (the concat
# filter keeps the timing honest; stream-copy concat did not). $1 is "yes"
# for captions, "no" for a plain cut when drawtext is unavailable.
cut_scenes() {
  local raw="$OUT/raw-recording.mp4" rawdur font n=0 fc="" a b cap start end dur i
  local -a args=()
  rawdur=$(ffmpeg -i "$raw" 2>&1 | sed -n 's/.*Duration: \([0-9]*\):\([0-9]*\):\([0-9.]*\).*/\1 \2 \3/p' | awk '{print $1*3600+$2*60+$3}')
  [ -n "$rawdur" ] || return 1
  font=$(fc-match -f '%{file}' DejaVuSans 2>/dev/null); [ -f "$font" ] || font=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf
  while read -r a b cap; do
    start=$(awk "BEGIN { v = $a - 0.4; print (v < 0) ? 0 : v }")
    end=$(awk "BEGIN { v = $b; m = $rawdur - 0.3; print (v > m) ? m : v }")
    dur=$(awk "BEGIN { print $end - $start }")
    awk "BEGIN { exit !($dur > 0.5) }" || { log "scene '$cap' fell off the end of the recording"; continue; }
    args+=(-ss "$start" -t "$dur" -i "$raw")
    if [ "$1" = yes ]; then
      fc+="[$n:v]pad=iw:ih+180:0:0:black,drawtext=fontfile=$font:text='$cap':fontcolor=white:fontsize=40:x=(w-text_w)/2:y=h-118,setpts=PTS-STARTPTS[v$n];"
    else
      fc+="[$n:v]pad=iw:ih+180:0:0:black,setpts=PTS-STARTPTS[v$n];"
    fi
    n=$((n + 1))
  done < "$SCENES"
  [ "$n" -gt 0 ] || return 1
  for i in $(seq 0 $((n - 1))); do fc+="[v$i]"; done
  fc+="concat=n=$n:v=1:a=0[out]"
  ffmpeg -loglevel error -y "${args[@]}" -filter_complex "$fc" -map "[out]" \
    -c:v libx264 -preset veryfast -crf 22 -pix_fmt yuv420p -movflags +faststart "$OUT/declaration-video.mp4" \
    && log "cut video: $n scenes, captions=$1, $(du -h "$OUT/declaration-video.mp4" | cut -f1)"
}
if command -v ffmpeg >/dev/null 2>&1 && [ -s "$SCENES" ] && [ -s "$OUT/raw-recording.mp4" ]; then
  cut_scenes yes || cut_scenes no || { log "WARNING: could not cut the recording; using the raw one"; cp "$OUT/raw-recording.mp4" "$OUT/declaration-video.mp4"; }
else
  log "ffmpeg or scenes missing; the raw recording is the only video"
  cp "$OUT/raw-recording.mp4" "$OUT/declaration-video.mp4" 2>/dev/null || true
fi
if [ "${locked:-0}" = 1 ] && [ "${unlocked:-0}" = 1 ]; then log "RESULT: lock and unlock both shown"; else log "RESULT: incomplete, see warnings above"; fi
ls -la "$OUT" | tee -a "$LOG"
