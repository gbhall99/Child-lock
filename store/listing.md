# Play Store listing

## App details

- **App name** (30 max): Child Lock: Touch Freeze
- **Short description** (80 max): Hand your phone to your child. Tap all they like, nothing happens.
- **Category**: Parenting (secondary: Tools)
- **Contact email**: _your address_ (required)
- **Trader details (required for EEA distribution)**: registered name, postal address, phone and email. Play blocks EEA distribution without the Digital Services Act trader declaration.
- **Terms URL**: publish a short EULA covering the right to withdraw within 14 days, refunds (Google's 48-hour window sits alongside statutory rights, it does not replace them), and the trader identity. Name that trader as the data controller in PRIVACY.md.
- **Privacy policy URL**: https://github.com/gbhall99/Child-lock/blob/main/PRIVACY.md
- **Target audience**: 18 and over (the app is for parents; it is not designed for children). Answer "No" to "Is your app designed for children?".
- **Ads**: No.
- **In-app purchases**: declare them at Pro launch; today the app sells nothing and the paywall never offers a purchase.
- **Content rating questionnaire**: Utility; no violence, no user content, no sharing of location, no purchases (until Pro is added). Expect "Everyone".

## Full description (4000 max)

Hand your phone to your child with a video, a video call or a game on screen. They can watch, listen, tap, swipe and poke; nothing happens.

Child Lock blocks touches. It does not filter or restrict content, and it is not a substitute for supervision. The screen is never covered or dimmed. The only sign is a small padlock badge in a corner.

LOCK AND UNLOCK WITHOUT TOUCHING THE SCREEN
Press volume up, then volume down. That is it. The same pattern switches the lock on and off, so you never have to find a button while a small hand is reaching for the phone. A short notice confirms each change.

WHAT STOPS WORKING FOR THE CHILD
• Taps, swipes, pinches and scrolls
• The back button and the volume buttons
• Pulling down the notifications panel
• Swiping home or opening recent apps
The power button still works, and an incoming call unlocks the phone so you can answer it.

AUTO-LOCK
Choose the apps you hand over and the moment that should lock them: a video call connecting, a video going full screen, anything playing, or simply opening. A five-second countdown gives you time to hand over; switch away or press the pattern to cancel. Unlock, and it locks again by itself only if the same moment ends and happens again.

MADE TO BE TRUSTED
• No ads, no accounts, no analytics, no internet permission
• Open source
• Nothing on screen is ever read or stored
• The lock is never saved: a restart always leaves the phone unlocked
• Colour-blind-safe design: every state has a shape and a word, never colour alone

OTHER WAYS TO UNLOCK
Prefer touch? Hold one finger in each of two opposite corners for a moment, hold the badge and type a PIN, or hold both volume buttons. Allow any combination; only what you switch on can unlock.

SETUP
A short assistant walks you through three steps: "Display over other apps", which is how touches are blocked; the Child Lock helper accessibility service, which handles the volume pattern, swipe blocking and auto-lock; and a page that shows how to unlock and how to force a restart if nothing else works. Every permission is explained before you enable it.

Child Lock uses Android's accessibility service as a parental control, not as an accessibility aid. It observes volume-button presses and which app is in front, and while locked it blocks navigation. It never reads screen content.

## Declarations in Play Console

### Accessibility service (App content → Sensitive permissions / AccessibilityService)

Purpose: parental touch lock. Core functionality: observe hardware volume-key
presses to switch the lock on and off; while locked, filter navigation keys,
block system gestures via touch-exploration mode, dismiss the notification
shade, and return to the foreground app the parent chose; while unlocked,
observe the foreground package for the auto-lock feature. The service reads no
window content: this build is the `play` product flavour, which is compiled
without any node-reading capability, so it cannot read window content at all.
An in-app prominent disclosure with explicit consent is shown before the user
is sent to enable the service (see `Disclosures.kt`). The app's core touch
lock also works with the service disabled.

Mitigations to state in the declaration, alongside the required screen
recording: the lock is always started by the user, it shows a visible badge
for its whole duration, it is never persisted so a restart always ends it, it
releases itself when the phone rings and after at most 90 minutes, and it
refuses to start when the way out would not work.

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
