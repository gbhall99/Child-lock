# Child Lock privacy policy

_Last updated: 18 September 2026_

Child Lock is a parental touch lock for Android. This policy describes what
the app does with information, which is: nothing that leaves your phone.

## Data collection

Child Lock collects no personal data, no usage data and no analytics. It
contains no advertising and no accounts. The app does not request the
internet permission, so it has no way to transmit anything.

The Google Play version is free for 30 days and then sold as a one-time
purchase through Google Play's billing system. The payment is handled
entirely by Google Play under Google's privacy policy; the app receives only
whether the purchase exists and stores that as a yes/no flag on the device.
The date of first launch is stored on the device for the trial. The same
version may once show Google Play's own "rate this app" sheet; that sheet is
Google Play's, and the app learns nothing from it. These two Google Play
libraries are the only third-party code in the app. The version built from
source contains neither and sells nothing.

## Permissions and what they are used for

- **Display over other apps.** Draws the transparent layer that blocks touches
  and the small badge that shows the lock is on. Nothing is captured.
- **Accessibility service (Child Lock helper).** Used as a parental control,
  not as an accessibility aid. While the lock is on it observes volume-button
  presses for the lock/unlock pattern, blocks the back and volume buttons and
  home/back swipes, closes the notification shade, and brings back the app you
  handed over. While the lock is off it only observes which app is in the
  foreground, for the auto-lock feature and to remember what to return to. It
  never reads, stores or transmits screen content, text, or any other data,
  with a single opt-in exception described below.
- **Notifications.** A silent notification while the lock is on, required by
  Android for a foreground service.
- **Foreground service (special use).** Keeps the touch lock alive while it is
  on.

## The one exception, and only outside the Play Store

Child Lock is built in two versions. **The version on Google Play cannot read
screen content at all**: the capability is compiled out of it, so the
paragraph above is unconditional there.

A separate version, built from source and installed by hand, has an optional
"Skip ads for them" feature. It is off unless you switch it on. When it is
on, and only while the lock is on and only inside the app you handed over, it
looks at that app's on-screen button labels for one that says "Skip ad" and
taps it. It looks at nothing else, taps nothing else, and stores and
transmits nothing.

## Data storage

Your settings (chosen gesture, chosen apps, options) are stored only on your
device in the app's private storage. If you set a PIN, only a salted hash of
it is stored. Uninstalling the app removes all of it. The lock state itself is
never stored, so a restart always leaves the phone unlocked.

## Children

Child Lock is used by parents and carers. It is not directed at children and
collects no information from anyone.

## Changes and contact

The data controller is Gareth Brandon Hall, the trader named in the terms of
use, `TERMS.md`, published alongside this policy. His postal address is shown
under the trader details on the app's Google Play listing, and he is reachable
at inovata.ai@gmail.com. Changes to this policy are published at the
same address as this document. Questions: that email address, or an issue on
the repository.
