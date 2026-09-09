# Child Lock

Hand your phone to your child mid video call. They can watch and listen, but
every touch is swallowed. The screen is never covered or dimmed; the only sign
is a small padlock badge in one corner. You unlock with a quick gesture that an
adult does without thinking and a toddler will not stumble into.

`PLAN.md` holds the full design. This file is the practical guide.

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

Open Child Lock and work down the Permissions card:

1. **Display over other apps** (required). This is the whole mechanism.
   On Android 13 and newer a sideloaded app gets *"App was denied access"*
   here the first time. That is expected: tap Close, open App info, tap the
   three-dot menu in the top right, choose *Allow restricted settings*, then
   come back and flip the switch again. (The menu item only appears after
   the first denied attempt.) Installing with `adb install` avoids this.
2. **Notifications** (recommended). A quiet notification shows the lock state.
3. **Accessibility guard** (optional, strongly recommended). Blocks the back
   and volume buttons, closes the notification shade if it is pulled down, and
   brings the call app straight back if the child reaches the home screen.
   Same restricted-settings dance as above if the switch is greyed out.

Then add the **Child Lock** tile to Quick Settings: pull the shade down twice,
tap the pencil, and drag the tile into the active area.

## Using it

**Default: the volume-button pattern.** While on the call, press volume up
then volume down, quickly (within about a second). A message says "Child Lock
on" and touch is frozen. Press the same pattern again to unlock. No screen
touching, no shade, no app switching. It needs the accessibility guard, which
the setup script enables. In the app you can flip the order to down-then-up,
or require the pattern twice in a row if your child has a knack for the volume
rocker.

**Other ways to lock:** pull down Quick Settings and tap *Child Lock* (locks
about two seconds later, so you can close the shade), or open the app and tap
*Arm lock from here*, which returns you to the previous app and locks after
the arm delay (5 s by default).

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
"Child Lock guard shortcut" toggle on the guard's settings page; the app
detects it and offers to take you there to turn it off.

**Knowing the state.** A large "Child Lock ON" or "OFF" banner appears on
screen for a moment at every change, and while locked a solid blue padlock
badge sits in the corner you chose. The badge is the only indicator: the
lock's notification is silent and shows no status-bar icon.

## What the lock does and does not stop

| The child… | Result |
|---|---|
| Taps, swipes, pinches anywhere on screen | Nothing happens |
| Presses back or volume | Swallowed (with the accessibility guard) |
| Pulls down the notification shade | Closed immediately (with the guard) |
| Swipes home, back or recents | Blocked outright (with the guard and a volume gesture); otherwise the call app is brought straight back |
| Presses the power button | Screen turns off; the call continues. The lock is still there after you unlock the phone |
| Receives a real phone call | The system in-call screen appears on top; the lock does not interfere |

Without the accessibility guard, only the first row is enforced. Swipe
blocking uses Android's touch-exploration mode while locked, the same switch
screen readers use, which is why it only combines with the volume gestures:
in that mode the screen no longer receives real touches, so a three-finger
triple tap is the touch fallback to unlock.

## Safety

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
