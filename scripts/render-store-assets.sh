#!/usr/bin/env bash
# Renders Play Store assets with headless Chromium from HTML templates.
# Inputs: store/assets/src/settings-light.png, setup-light.png (from the
# Robolectric ScreenshotTest). Outputs: store/assets/*.png
set -euo pipefail
cd "$(dirname "$0")/.."
CHROME=${CHROME:-$(command -v chromium || command -v google-chrome || ls /opt/pw-browsers/chromium-*/chrome-linux/chrome 2>/dev/null | head -1)}
[ -x "$CHROME" ] || { echo "No Chromium found; set CHROME=/path/to/chrome"; exit 1; }
OUT=store/assets; SRC=$PWD/store/assets/src; TMP=$(mktemp -d)
LOCK='M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z'

shot() { "$CHROME" --headless=new --no-sandbox --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
  --window-size="$2" --screenshot="$OUT/$1" "file://$TMP/$1.html" >/dev/null 2>&1; echo "wrote $OUT/$1"; }

CSS='<style>*{margin:0;box-sizing:border-box}body{font-family:Roboto,"Segoe UI",Helvetica,Arial,sans-serif;background:#0072B2;overflow:hidden}
.bg{position:absolute;inset:0;background:linear-gradient(160deg,#0B5C8F 0%,#0072B2 55%,#2C8FCB 100%)}
.cap{position:absolute;left:72px;right:72px;top:112px;color:#fff;font-size:64px;font-weight:700;line-height:1.12;letter-spacing:-.5px}
.sub{position:absolute;left:72px;right:72px;top:290px;color:rgba(255,255,255,.85);font-size:30px;line-height:1.35}
.phone{position:absolute;left:120px;right:120px;top:400px;bottom:-90px;border-radius:64px;background:#111;padding:16px;box-shadow:0 40px 80px rgba(0,0,0,.35)}
.screen{width:100%;height:100%;border-radius:48px;overflow:hidden;background:#F2F4F8;position:relative}
.screen img{width:100%;display:block}
.chip{position:absolute;left:50%;transform:translateX(-50%);top:56px;white-space:nowrap;display:flex;align-items:center;gap:14px;background:rgba(32,33,36,.95);border-radius:36px;padding:14px 24px 14px 18px;box-shadow:0 8px 24px rgba(0,0,0,.4)}
.chip .t{color:#fff;font-size:22px;font-weight:600}.chip .d{color:#BDC1C6;font-size:19px;margin-top:2px}
.badge{position:absolute;left:28px;top:150px;width:52px;height:52px;border-radius:50%;background:#0072B2;border:3px solid rgba(255,255,255,.8);display:flex;align-items:center;justify-content:center}
</style>'
ICON() { echo "<svg width=\"$1\" height=\"$1\" viewBox=\"0 0 24 24\"><path fill=\"$2\" d=\"$LOCK\"/></svg>"; }

# 1. Icon 512
cat > "$TMP/icon-512.png.html" <<H
$CSS<body style="width:512px;height:512px;background:#fff"><div style="width:512px;height:512px;border-radius:112px;background:linear-gradient(160deg,#0B5C8F,#0072B2 60%,#2C8FCB);display:flex;align-items:center;justify-content:center">$(ICON 300 '#fff')</div></body>
H
shot icon-512.png 512,512

# 2. Feature graphic 1024x500
cat > "$TMP/feature-graphic.png.html" <<H
$CSS<body style="width:1024px;height:500px"><div class="bg"></div>
<div style="position:absolute;left:80px;top:130px;width:240px;height:240px;border-radius:56px;background:rgba(255,255,255,.14);display:flex;align-items:center;justify-content:center">$(ICON 150 '#fff')</div>
<div style="position:absolute;left:370px;top:140px;color:#fff;font-size:84px;font-weight:700;letter-spacing:-1px">Child Lock</div>
<div style="position:absolute;left:374px;top:250px;color:rgba(255,255,255,.9);font-size:36px;line-height:1.3">Tap all they like.<br>Nothing happens.</div>
</body>
H
shot feature-graphic.png 1024,500

# 3. Screenshot 1: locked scene (synthetic video)
cat > "$TMP/screenshot-1-locked.png.html" <<H
$CSS<body style="width:1080px;height:1920px"><div class="bg"></div>
<div class="cap">Hand it over.<br>Taps do nothing.</div>
<div class="sub">They can tap, swipe and poke. The screen stays exactly as it was; only a small badge shows the lock is on.</div>
<div class="phone"><div class="screen" style="background:#1a1a1a">
  <div style="position:absolute;inset:0;background:radial-gradient(circle at 50% 45%,#3a3f47 0%,#1a1a1a 70%)"></div>
  <div style="position:absolute;left:50%;top:44%;transform:translate(-50%,-50%);width:150px;height:150px;border-radius:50%;background:rgba(255,255,255,.14);display:flex;align-items:center;justify-content:center"><div style="margin-left:14px;width:0;height:0;border-left:56px solid #fff;border-top:34px solid transparent;border-bottom:34px solid transparent"></div></div>
  <div style="position:absolute;left:48px;right:48px;bottom:120px;height:8px;border-radius:4px;background:rgba(255,255,255,.25)"><div style="width:38%;height:100%;border-radius:4px;background:#fff"></div></div>
  <div class="chip">$(ICON 30 '#8AB4F8')<div><div class="t">Child Lock on</div><div class="d">Touches do nothing. Volume up then down to unlock.</div></div></div>
  <div class="badge">$(ICON 26 '#fff')</div>
</div></div></body>
H
shot screenshot-1-locked.png 1080,1920

# 4. Screenshot 2: volume pattern
cat > "$TMP/screenshot-2-volume.png.html" <<H
$CSS<body style="width:1080px;height:1920px"><div class="bg"></div>
<div class="cap">Volume up, then down.<br>Locked. Again: unlocked.</div>
<div class="sub">No buttons to find on screen while a small hand is reaching for the phone.</div>
<div class="phone"><div class="screen" style="background:#1a1a1a">
  <div style="position:absolute;inset:0;background:radial-gradient(circle at 50% 45%,#3a3f47 0%,#1a1a1a 70%)"></div>
  <div class="chip">$(ICON 30 '#BDC1C6')<div><div class="t">Child Lock off</div><div class="d">Touch works again.</div></div></div>
  <div style="position:absolute;right:-30px;top:520px;width:14px;height:120px;border-radius:7px;background:#5f6368"></div>
  <div style="position:absolute;right:-30px;top:660px;width:14px;height:120px;border-radius:7px;background:#5f6368"></div>
  <div style="position:absolute;right:40px;top:545px;color:#fff;font-size:44px;font-weight:700;text-align:right">1 &nbsp;▲</div>
  <div style="position:absolute;right:40px;top:685px;color:#fff;font-size:44px;font-weight:700;text-align:right">2 &nbsp;▼</div>
</div></div></body>
H
shot screenshot-2-volume.png 1080,1920

# 5. Screenshot 3: settings; 6. Screenshot 4: setup
for pair in "screenshot-3-settings.png|settings-light.png|One screen. Six tiles.|Colour-blind-safe. Open source. No ads, no accounts, no internet permission." \
            "screenshot-4-setup.png|setup-light.png|Three quick steps.|Two permissions, then how to unlock and how to force a restart if nothing works."; do
  IFS='|' read -r name img cap sub <<< "$pair"
  cat > "$TMP/$name.html" <<H
$CSS<body style="width:1080px;height:1920px"><div class="bg"></div>
<div class="cap">$cap</div><div class="sub">$sub</div>
<div class="phone"><div class="screen"><img src="file://$SRC/$img"></div></div></body>
H
  shot "$name" 1080,1920
done
rm -rf "$TMP"
