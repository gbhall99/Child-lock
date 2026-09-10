# Play Store listing

## App details

- **App name** (30 max): Child Lock: Touch Freeze
- **Short description** (80 max): Hand your phone to your child. Tap all they like, nothing happens.
- **Category**: Parenting (secondary: Tools)
- **Contact email**: your address
- **Privacy policy URL**: https://github.com/gbhall99/Child-lock/blob/main/PRIVACY.md
- **Target audience**: 18 and over (the app is for parents; it is not designed for children). Answer "No" to "Is your app designed for children?".
- **Ads**: No.
- **Content rating questionnaire**: Utility; no violence, no user content, no sharing of location, no purchases (until Pro is added). Expect "Everyone".

## Full description (4000 max)

Hand your phone to your child with a video, a video call or a game on screen. They can watch, listen, tap, swipe and poke; nothing happens. The screen is never covered or dimmed. The only sign is a small padlock badge in a corner.

LOCK AND UNLOCK WITHOUT TOUCHING THE SCREEN
Press volume up, then volume down. That is it. The same pattern switches the lock on and off, so you never have to find a button while a small hand is reaching for the phone. A short notice confirms each change.

WHAT STOPS WORKING FOR THE CHILD
• Taps, swipes, pinches and scrolls
• Press back or the volume buttons
• Pull down the notification shade
• Swipe home or open recent apps
The power button still works. Real phone calls still ring.

AUTO-LOCK
Choose the apps you hand over, a video app, a game, a video-call app, and Child Lock switches itself on a few seconds after one of them opens. Switch away or press the pattern to cancel.

MADE TO BE TRUSTED
• No ads, no accounts, no analytics, no internet permission
• Open source
• Nothing on screen is ever read or stored
• The lock is never saved: a restart always leaves the phone unlocked
• Colour-blind-safe design: every state has a shape and a word, never colour alone

OTHER WAYS TO UNLOCK
Prefer touch? Hold one finger in each of two opposite corners for a moment, or hold the badge and type a PIN. Choose what suits you.

SETUP
A short assistant walks you through two permissions: "Display over other apps", which is how touches are blocked, and the Child Lock helper accessibility service, which handles the volume pattern, swipe blocking and auto-lock. Both are explained before you enable them.

Child Lock uses Android's accessibility service as a parental control, not as an accessibility aid. It observes volume-button presses and which app is in front, and while locked it blocks navigation. It never reads screen content, unless you switch on the optional "Skip ads" feature, which reads button labels in the one app you chose.

## Declarations in Play Console

### Accessibility service (App content → Sensitive permissions / AccessibilityService)

Purpose: parental touch lock. Core functionality: observe hardware volume-key
presses to switch the lock on and off; while locked, filter navigation keys,
block system gestures via touch-exploration mode, dismiss the notification
shade, and return to the foreground app the parent chose; while unlocked,
observe the foreground package for the auto-lock feature. The service reads no
window content, with one opt-in exception: the "Skip ads for them" option
(off by default, Pro) reads on-screen button labels inside the single app
the parent chose, while locked, to tap a "Skip ad" button. An in-app
prominent disclosure with explicit consent is shown before the user is sent
to enable the service (see `Disclosures.kt`). The app's core touch lock also
works with the service disabled.

Risk note: tapping "Skip ad" in another app may be read by Google as
interfering with that app (Device and Network Abuse policy) and conflicts
with YouTube's terms. Consider shipping the Play build with
`SkipAdMatcher.supportedPackages` empty (which hides the option) and keeping
the feature for the GitHub build only.

### Foreground service, type "specialUse"

Justification: the touch-blocking overlay must persist for the duration of the
lock, which the user starts and ends explicitly; it has no other lifecycle
owner. A short screen recording of arming, the badge, and unlocking is
required in the form.

### Display over other apps

Used to draw the transparent touch shield and the corner badge.

### Data safety form

- Does the app collect or share any user data? **No.**
- Is data encrypted in transit? Not applicable (no transmission).
- Can users request deletion? Not applicable (nothing collected).
- Security practices: no data collected.

### App access

All functionality is available without login. Provide the reviewer with the
note: "Grant Display over other apps and enable Child Lock helper in
Accessibility settings, then open any app and press volume up then down."

## Assets (in `store/assets/`)

- `icon-512.png`: hi-res icon, 512 × 512, 32-bit PNG
- `feature-graphic.png`: 1024 × 500
- `screenshot-1-locked.png` … `screenshot-4-setup.png`: 1080 × 1920 phone screenshots

Regenerate with `scripts/render-store-assets.sh` (needs headless Chromium).
