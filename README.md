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

Open Child Lock and work down the Permissions card:

1. **Display over other apps** (required). This is the whole mechanism.
2. **Notifications** (recommended). A quiet notification shows the lock state.
3. **Accessibility guard** (optional, strongly recommended). Blocks the back
   and volume buttons, closes the notification shade if it is pulled down, and
   brings the call app straight back if the child reaches the home screen.
   On Android 13 and newer the switch is greyed out for sideloaded apps until
   you open App info, tap the three-dot menu, and choose *Allow restricted
   settings*.

Then add the **Child Lock** tile to Quick Settings: pull the shade down twice,
tap the pencil, and drag the tile into the active area.

## Using it

**Lock, from the call:** pull down Quick Settings, tap *Child Lock*, close the
shade. The lock engages about two seconds later; a short toast confirms it.

**Lock, from the app:** open Child Lock, tap *Arm lock*. You are returned to
the previous app and the lock engages after the arm delay (5 s by default).

**Unlock (default gesture):** press one finger in the top-left corner and one
in the bottom-right corner at the same time and hold for 1.5 s. A ring fills
around the badge, the phone buzzes, and touch is back. Any third finger or a
finger drifting out of its corner restarts the timer.

Other gestures are available in the app:

- **Badge long-press then PIN.** Hold the badge until a keypad appears beside
  it, then type your PIN. Three wrong PINs hide the keypad for 30 seconds.
- **Volume chord.** Hold volume up and volume down together. Needs the
  accessibility guard. The corner hold keeps working as a fallback.

## What the lock does and does not stop

| The child… | Result |
|---|---|
| Taps, swipes, pinches anywhere on screen | Nothing happens |
| Presses back or volume | Swallowed (with the accessibility guard) |
| Pulls down the notification shade | Closed immediately (with the guard) |
| Swipes home or opens recents | The call app is brought straight back (with the guard) |
| Presses the power button | Screen turns off; the call continues. The lock is still there after you unlock the phone |
| Receives a real phone call | The system in-call screen appears on top; the lock does not interfere |

Without the accessibility guard, only the first row is enforced.

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

Run the unit tests with `./gradlew test`.
