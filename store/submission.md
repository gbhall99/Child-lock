# Play Console submission pack

Everything to paste into Play Console, in the order the Console asks for it.
The listing text itself is in `listing.md`; this file is the rest. Where a
form wants a value, it is in **bold** or a quoted block.

## 0. Before the Console

- The repository is public, so these two links open without signing in:
  - Privacy policy: https://github.com/gbhall99/Child-lock/blob/main/PRIVACY.md
  - Terms: https://github.com/gbhall99/Child-lock/blob/main/TERMS.md
- Actions → **Store media** → Run workflow. When it finishes, the
  `store-media` pre-release on the Releases page has the screenshots zip,
  the declaration video (`declaration-video.mp4`) and both debug APKs.
  Upload the video to YouTube as *Unlisted* (or Google Drive, link shared
  with "anyone with the link") and keep the URL: two declarations ask for it.
- A signed bundle: the newest `v*` release has `app-play-release.aab`.

## 1. Account (Setup → Developer account)

1. Identity verification: wait for Google's email.
2. Phone verification: unblocks once identity is approved.
3. Payments profile (Setup → Payments profile): needed before a paid product
   can be created. Country **United Kingdom**, individual, your bank account
   for payouts. Complete the US tax form (W-8BEN for an individual outside
   the US).
4. Trader details (Setup → Developer account → Trader details, or wherever
   the Console puts the Digital Services Act declaration): legal name as on
   your ID, the postal address Google verified, the phone number, email
   **inovata.ai@gmail.com**. This is what `TERMS.md` and `PRIVACY.md` point
   to for the address and phone.

## 2. Create the app

All apps → Create app:

- App name: **Child Lock: Touch Freeze**
- Default language: **English (United Kingdom) – en-GB**
- App or game: **App**
- Free or paid: **Free** (the purchase is in-app; a "paid" app cannot be made
  free later, a free app can sell in-app products)
- Declarations: tick both.

Package name is fixed by the first bundle: `com.gbhall.childlock`.

## 3. Test and release → Internal testing

Create a release, upload `app-play-release.aab`, release name is filled from
the bundle. Release notes (en-GB):

```
<en-GB>
First release. Hand your phone to your child with a video, a call or a game on screen; they can tap and swipe all they like and nothing happens. Lock and unlock with volume up then down. Free for 30 days, then one purchase.
</en-GB>
```

Add yourself as a tester (an email list), save, and install from the opt-in
link on your phone. Everything in section 5 can be filled while the bundle
processes.

## 4. Monetise → Products → In-app products

Create product:

- Product ID: **childlock_full** (must match `FeatureGate.PRODUCT_ID`; cannot change)
- Name: **Child Lock**
- Description: **Unlocks Child Lock for good. One purchase, no subscription.**
- Price: **£2.99** default, let Play convert the other currencies.
- **Activate** it. An inactive product makes the Buy button report "store
  unavailable".

Setup → Licence testing: add every tester's Google account (yours included)
and set the licence response to **RESPOND_NORMALLY**. Licence testers buy for
free and their purchases refund automatically, so the whole trial-to-purchase
journey can be tested for nothing.

## 5. App content (Policy → App content)

Work down the list. Each heading is one form.

### Privacy policy

`https://github.com/gbhall99/Child-lock/blob/main/PRIVACY.md`

### Ads

**No, my app does not contain ads.**

### App access

**All or some functionality is restricted** is the honest answer, because a
reviewer needs two Android settings switched on. Add one instruction set:

- Name: **Grant the two permissions**
- Instructions:

```
No login. Grant "Display over other apps" and enable "Child Lock helper" under Settings > Accessibility when the in-app setup guide asks (it opens the right settings page for each). Then open any app, press volume up then volume down: the phone locks and a small padlock badge appears in a corner. Taps and swipes do nothing. Press volume up then volume down again to unlock. A forced restart (hold power for 10 to 30 seconds) always ends the lock.
```

### Content rating

Start questionnaire. Email **inovata.ai@gmail.com**. Category **Utility,
Productivity, Communication, or Other**. Then:

- Violence, sexuality, language, controlled substances: **No** to all.
- Miscellaneous: user-generated content **No**; shares location **No**;
  purchases of digital goods **Yes**; gambling **No**.

Expected rating: Everyone / PEGI 3.

### Target audience and content

- Target age groups: **18 and over** only.
- Appeal to children: **No**. (The app is for parents. It is not designed
  for children and it collects nothing.)

### News apps: **No.**   Data safety: see below.   Government apps: **No.**
### Financial features: **My app doesn't provide any financial features.**
### Health: **My app does not have any health features.**

### Data safety

- Does your app collect or share any of the required user data types? **No.**
- Is all of the user data collected by your app encrypted in transit? Not asked once "No" is given.
- Do you provide a way for users to request that their data is deleted? Not asked.

That is the whole form. The purchase is Google Play's data, not the app's.

### Advertising ID

**No, my app does not use advertising ID.**

### Sensitive app permissions: Accessibility service

The Console form has a text box and a video URL.

- Is the accessibility service used for accessibility purposes? **No** (it is
  a parental control). Purpose text:

```
Child Lock is a parental touch lock. The parent hands the phone to a child with a video or a call on screen; the app blocks every touch so the child can watch but not change anything. The accessibility service is used only for this and never as an accessibility aid.

Core functionality that needs the service:
1. Observe hardware volume-key presses so the parent can lock and unlock with a volume up, volume down pattern without touching the screen (the screen is what the child is holding).
2. While locked: filter the back and volume keys, block the home, back and recents system gestures via touch-exploration mode, dismiss the notification shade if it opens, and return to the app the parent handed over if it is left.
3. While unlocked: observe which package is in the foreground, so the optional auto-lock can lock when a chosen app starts a call or a video.

It never reads window content. The Play build is compiled without any node-reading code path, so it cannot read text on screen at all.

User protections: a prominent disclosure with explicit consent is shown in the app before the user is sent to enable the service. The lock is always started by the user, shows a visible badge for its whole duration, is never persisted (a restart always ends it), releases itself when the phone rings and after at most 90 minutes, and refuses to start if the way out would not work. The core touch lock also works with the service disabled; the service only hardens it.
```

- Video: the URL from section 0.

### Foreground service permissions (FOREGROUND_SERVICE_SPECIAL_USE)

- Task the foreground service performs: 

```
Keeps the transparent touch-blocking overlay and its corner badge alive for the duration of a lock the parent started and will end explicitly. No other foreground service type fits: it is not media, location, a call or data sync; it is a user-visible, user-controlled parental lock that must survive the app leaving the foreground, because the child is holding the phone with another app on screen.
```

- Video: the same URL.

### Display over other apps

Not a separate form in most Console versions; if asked: draws the
transparent touch shield and the corner badge over the app the parent handed
over. Nothing is captured.

## 6. Store presence → Main store listing

Paste from `listing.md`: app name, short description, full description.
Upload from `store/assets/`: `icon-512.png`, `feature-graphic.png`, the four
`screenshot-*.png` (phone). Category **Parenting**, tags as offered.
Contact email **inovata.ai@gmail.com**. Store settings → App category
**Parenting**, and tick **contains in-app purchases** if it is not set
automatically once the product exists.

## 7. Closed testing (the 14-day rule)

New personal accounts must run a closed test with **at least 12 testers
opted in for 14 continuous days** before they can apply for production.

1. Test and release → Closed testing → Create track (Alpha is fine).
2. Promote the internal release into it, or upload the same bundle.
3. Testers → create an email list, paste the 12+ addresses, save. Copy the
   opt-in link.
4. Send the message below. The 14 days count from when 12 have opted in
   and stayed in, so chase stragglers on day one.
5. After 14 days the Dashboard offers **Apply for production**. Answer the
   short questions honestly (how testers were recruited, what was found).

Message to testers:

```
Subject: Help me test Child Lock (14 days, five minutes of effort)

I have built a small Android app for parents: Child Lock lets you hand your phone to a child with a video or a call on screen, and every tap or swipe does nothing until you press volume up then volume down. Google requires 12 testers to keep a new app installed for 14 days before it can go live, and I would be grateful if you were one of them.

What to do:
1. Open this link on your Android phone, signed in with your Google account: <opt-in link>
2. Tap "Become a tester", then "Download it on Google Play" and install.
3. Open it once and run the three-step setup. Try locking with volume up, volume down, and unlocking the same way.
4. Keep it installed for two weeks. You do not have to use it again, but if you have a child handy, do, and tell me what happened.

It has no ads, no accounts and no internet permission. It is free for 30 days; if you see a price after that, ignore it, the test does not need it. Reply with anything odd, especially on Samsung or Xiaomi phones. Thank you.
```

Cover at least one Samsung, one Pixel and one Xiaomi if you can: those three
customise gesture navigation the most.

## 8. Production

After the application is approved: Production → Create release → the same
bundle → countries (all, or exclude none) → review → roll out. First review
takes from a few hours to about a week; the accessibility declaration is the
part most likely to draw a question, so keep the video URL live.

## 9. Automated uploads (optional, after the app exists)

1. Google Cloud console → create a project → IAM → Service accounts → create
   one, JSON key.
2. Play Console → Users and permissions → Invite new users → the service
   account's email → app permissions on Child Lock → **Release manager**
   (release to testing tracks, manage store presence).
3. GitHub → repository Settings → Secrets → Actions → `PLAY_SERVICE_ACCOUNT_JSON`
   = the JSON file's contents.

From then on every `v*` tag (or manual run of the Android workflow) uploads
the bundle to Internal testing and pushes the listing text and images from
`store/`. Declarations, data safety and content rating stay manual.

## 10. Hardware pass

Still needed, once per release, on a real phone with gesture navigation.
The emulator video shows the flow; it does not prove Samsung's or Xiaomi's
gesture layer stays out. The list is in `RELEASING.md`.
