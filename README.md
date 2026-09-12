# Child Lock

Child Lock blocks touches. It does not filter or restrict content, and it is
not a substitute for supervision.

Hand your phone to your child with a video, a video call or a game on
screen. They can tap, swipe and poke all they like; nothing happens. The
screen is never covered or dimmed; the only sign is a small padlock badge
in one corner. You unlock with a quick gesture that an
adult does without thinking and a toddler will not stumble into.

`PLAN.md` holds the full design. This file is the practical guide.
`REVIEW.md` records the September 2026 expert review and the grouped work it
produced. `RELEASING.md` covers signing and Play submission, `store/listing.md` the
store copy and declarations, `PRIVACY.md` the privacy policy, and
`MONETISATION.md` the business plan.

## Build and install

Requirements: Android Studio (or a JDK 17 plus the Android SDK with platform 35)
and a phone on Android 8.0 or newer.

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run. Every push to GitHub also
builds a debug APK in the Actions tab under the `childlock-debug-apk` artifact.

The app has no third-party dependencies at all: only the Android framework and
the Kotlin standard library. That keeps it small and easy to audit for something
that sits over every other app.

## First-time setup

### In the app

The first time Child Lock opens it runs a short setup assistant: one step at
a time, one tap each, and it detects completion when you come back. Two steps
are required (Display over other apps, Accessibility guard), two are optional
(notifications, the Quick Settings tile, which Android adds after a single
confirmation). If Android shows "App was denied access" for a sideloaded
install, the assistant explains the one-time "Allow restricted settings" fix.

### Even less effort: one script over adb

Enable USB debugging on the phone (Settings → About phone → tap Build number
seven times, then Settings → System → Developer options → USB debugging),
plug it in, and run:

```
scripts/setup-phone.sh path/to/childlock.apk      # macOS / Linux
scripts\setup-phone.cmd path\to\childlock.apk     # Windows
```

It installs the APK, grants "Display over other apps", lifts Android's
restricted-settings gate, enables the accessibility guard, adds the Quick
Settings tile and opens the app. Nothing to toggle by hand. Apps installed
through adb are exempt from the sideload restrictions, so any later changes
in Settings work without the "Allow restricted settings" dance too.

Only `adb` is needed on the computer: it ships with Android Studio, or
download "SDK Platform-Tools" from developer.android.com.

### By hand

Open Child Lock and work down the "Needs attention" card:

1. **Display over other apps** (required). This is the whole mechanism.
   On Android 13 and newer a sideloaded app gets *"App was denied access"*
   here the first time. That is expected: tap Close, open App info, tap the
   three-dot menu in the top right, choose *Allow restricted settings*, then
   come back and flip the switch again. (The menu item only appears after
   the first denied attempt.) Installing with `adb install` avoids this.
2. **Notifications** (recommended). A quiet notification shows the lock state.
3. **Accessibility guard** (optional, strongly recommended). Blocks the back
   and volume buttons, closes the notifications panel if it is dragged down, and
   brings the app you handed over straight back if the child reaches the home screen.
   Same restricted-settings dance as above if the switch is greyed out.

Then add the **Child Lock** tile to Quick Settings: pull the shade down twice,
tap the pencil, and drag the tile into the active area.

## Using it

**Default: the volume-button pattern.** With whatever you are handing over on
screen, press volume up then volume down, quickly (within about a second). A message says "Child Lock
on" and touch is frozen. Press the same pattern again to unlock. No screen
touching, no shade, no app switching. It needs the accessibility guard, which
the setup script enables. In the app you can flip the order to down-then-up,
or require the pattern twice in a row if your child has a knack for the volume
rocker.

**Other ways to lock:** pull down Quick Settings and tap *Child Lock* (locks
about two seconds later, so you can close the shade), or open the app and tap
the lock button, which returns you to the previous app and locks after the
arm delay (10 s by default).

**Auto-lock.** Choose apps and the moment that should lock them: when a
video call connects (WhatsApp, Meet, Teams and the like; a voice call is a
separate choice, as is "any call"), when a video goes full screen (YouTube,
iPlayer, Netflix), when anything plays, or simply when the app opens.
Well-known apps get the right options and default; the rest are classified
by the category they declare to Android. Child Lock reads only Android's
audio mode, whether a camera is in use, whether media is playing, and whether
the status bar is hidden; never screen content. Two more automatic options:
"Lock again by itself" re-locks if the same moment happens again after you
unlock (the video goes full screen again, another call connects), and "Skip
ads for them" taps a "Skip ad" button when one appears in the app you handed
over, using each app's own wording ("Skip intro" and "Skip recap" are never
tapped). Most streaming services make their ads unskippable, so in practice
this helps on YouTube, YouTube Kids, Dailymotion and a few others.

**Skip ads only exists in the sideload build.** The project builds two
flavours: `sideload`, which has it, and `play`, which is compiled without the
capability at all. Tapping another app's skip button conflicts with Google
Play's Device and Network Abuse policy and with YouTube's terms, and it is
the only thing that would make Child Lock read screen content. Build
`assembleSideloadDebug` for your own phone and `bundlePlayRelease` for the
store. Skip ads is off by default and is the one feature that reads anything
on screen: button labels, in that one app, while locked. The automatic
features are Child Lock Pro; the lock, the volume buttons and every safety
feature are free. A countdown (5 s by default) follows;
switching away or pressing the volume pattern cancels it. After you unlock,
it will not re-arm for that app until you have left it and come back. Needs
the accessibility guard.

**Other ways to unlock,** selectable in the app:

- **Two-finger corner hold.** One finger in the top-left corner and one in the
  bottom-right at the same time, held for 1.5 s. A ring fills around the badge
  and the phone buzzes. This always works as a backup, whatever gesture is
  selected.
- **Badge long-press then PIN.** Hold the badge until a keypad appears beside
  it, then type your PIN. Three wrong PINs hide the keypad for 30 seconds.
- **Volume chord.** Hold volume up and volume down together for the hold time.

The screen uses a colour-blind-safe palette (blue, orange, grey; no red or
green) and every status also carries a distinct symbol and word.

**About the floating accessibility button.** Android itself never shows an
icon for the guard. If you see a floating accessibility button, it is the
"Child Lock helper shortcut" toggle on the guard's settings page; the app
detects it and offers to take you there to turn it off.

**Knowing the state.** A large "Child Lock ON" or "OFF" banner appears on
screen for a moment at every change, and while locked a solid blue padlock
badge sits in the corner you chose. The badge is the only indicator: the
lock's notification is silent and shows no status-bar icon.

## What the lock does and does not stop

| The child… | Result |
|---|---|
| Taps, swipes, pokes anywhere on screen | Nothing happens |
| Presses back or volume | Swallowed (with the accessibility guard) |
| Drags down the notifications panel | Closed immediately (with the guard) |
| Swipes home, back or recents | Blocked outright (with the guard and a volume gesture, Android 11 and newer; on by default); otherwise the app you handed over is brought straight back |
| Presses the power button | Screen turns off; whatever was playing continues. The lock is still there after you unlock the phone |
| Receives a real phone call | Child Lock unlocks itself so the call can be answered |

Without the accessibility guard, only the first row is enforced. Swipe
blocking uses Android's touch-exploration mode while locked, the same switch
screen readers use, which is why it only combines with the volume gestures:
in that mode the screen no longer receives real touches, so a three-finger
triple tap, done twice within a few seconds, is the touch fallback to unlock.
"Block swipes", under the Blocking tile, switches it off.

## Compatibility

The overlay touch-block is the most portable part and works from Android 8
up. The accessibility-based features vary more by manufacturer than by
Android version.

| Android | What works |
|---|---|
| 8 to 9 | Lock, corner hold, tile, volume pattern. Home button press falls back to relaunching the call. |
| 10 | As above; gesture navigation exists but the API that blocks it arrived in 11, so the relaunch fallback applies. |
| 11 and up | Everything, including swipe blocking. Sideloaded installs on 13+ need the one-time restricted-settings step, the adb script, or Play distribution. |

Manufacturer notes:

- **Samsung One UI**: set Child Lock's battery use to Unrestricted and add
  it to "Never sleeping apps", or the guard may be stopped in the background.
- **Xiaomi, Huawei, Oppo, Vivo**: aggressive background killing; some
  disable accessibility services after a reboot; Xiaomi needs the extra
  "Display pop-up windows while running in the background" permission. The
  lock fails safe (it simply unlocks) but may be less reliable.
- **Three-button navigation**: a double tap on the home button still goes
  home while swipe blocking is on; the guard brings the call back.
- Swipe blocking has been verified in unit tests only, not on hardware.

## Safety

- While locked the screen orientation is pinned to whatever it was, so a
  full-screen video does not flip when the phone is tilted (switchable in
  More options).
- An incoming or connected phone call always releases the lock.
- No lock outlives 90 minutes, whatever else fails.
- The app refuses to lock when the way out would not work: a volume gesture
  with the helper switched off, or a touch gesture while a screen reader is
  exploring by touch.
- The lock state lives in memory only. It is never saved, so a restart, a
  crash, or a force stop always leaves the phone unlocked.
- The badge is always visible while locked. No badge means no lock.
- The Quick Settings tile can arm the lock but never release it, so finding
  the shade does not help a child.
- If the overlay fails to attach for any reason the app gives up loudly and
  stays unlocked rather than half-locking.

## Project layout

```
app/src/main/java/com/gbhall/childlock/
  gesture/   pure-Kotlin unlock recognisers (unit tested on the JVM)
  lock/      LockController, LockOverlayService, overlay views
  guard/     optional accessibility hardening
  tile/      Quick Settings tile
  settings/  preferences
  ui/        settings screen
```

## Tests

`./gradlew test` runs two kinds of tests on the JVM, no device needed:

- Pure Kotlin tests for the gesture recognisers and guard policy, including a
  randomised "child mashing" sequence that must never unlock.
- Robolectric tests that exercise the real Android layer: the overlay service
  going foreground and attaching the window, unlock through real multi-touch
  `MotionEvent`s, the PIN pad flow and its cooldown, the tile, the settings
  screen, key filtering in the accessibility guard, and settings persistence.
  These run against both the Android 8.0 and Android 15 runtimes.

Install note: the debug APK from CI or from this repo is signed with a debug
key. If you later install a build from Android Studio, which uses your
machine's own debug key, uninstall the old one first.
