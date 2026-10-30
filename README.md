# Focus Lock

A simple app blocker built around your own modes.

- **Modes** – as many as you like. Each has an emoji icon, a name, a set of apps, the days it runs, and either
  a time range (e.g. 💼 Work, Mon–Fri 09:00–18:00) or "after I wake up" (blocks for a set time after the first
  unlock of the day).
- **Quick blocks** – like a timer: pick apps and block them from now until a time you choose (up to 24 hours).
  It disappears when it ends.
- **On/off switch** on every mode. Turning one off asks: *Just for today* (handy for public holidays;
  it comes back on tomorrow) or *Until I turn it back on*.
- **Emergency unlock** – from the block screen: type the sentence shown on screen (capital letters don't matter,
  pasting is disabled), wait for a countdown (30 seconds by default; each mode can set 0–120 seconds),
  then unlock that app for 5, 15 or 30 minutes. Turning off a mode *while it's blocking* goes through the same steps.
  Turning it off before it starts is instant.
- A mode can't be edited while it's blocking.

## Build the APK

**Option A – GitHub (no computer needed):** every push to `main` builds the APK and publishes it as a release.
Newest build: [download FocusLock.apk](https://github.com/alenashilina/App-blocker/releases/latest/download/FocusLock.apk)

**Option B – Android Studio:** File → Open → this folder. Wait for it to sync, then
Build → Build App Bundle(s) / APK(s) → Build APK(s).

## Install and turn on

1. Open the APK on your phone and allow installing from that source when asked.
2. Open Focus Lock → **Turn on blocker** → Installed apps → Focus Lock → turn on.
3. If that switch is greyed out (Android does this for apps not from the Play Store):
   Settings → Apps → Focus Lock → ⋮ (top right) → **Allow restricted settings**, then repeat step 2.
4. Create your modes, or start a quick block.

A full guide in English and Russian is built into the app (📖 How to use · Как пользоваться),
and opens on first launch.

## Good to know

- It needs the Accessibility permission only to see which app is opening. It reads nothing on screen and has no
  internet access.
- An app that's already open when a block starts (or when an emergency unlock runs out) is closed within 30 seconds.
- Notifications from blocked apps still arrive; only opening the app is blocked.
- You can change the emergency sentence in `Model.kt` (`EMERGENCY_PHRASE`).
