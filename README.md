# Block

An Android app that locks the apps you pick for a set amount of time, and gives you no button to cancel it.

I built it to stop myself from reopening a trading app right after a losing trade. It works for any app you choose, though, so use it on whatever you tend to reach for when you shouldn't.

## How it works

Pick the apps, pick how long (15 minutes, 1 hour, until midnight, or anything from 1 minute to 24 hours), and press Lock.

From then on, opening a locked app shows a full-screen cover with the countdown and a reminder you wrote yourself. After 7 seconds it sends you back to the home screen. The small × in the corner does that immediately.

There is no stop button and no way to shorten the timer. Closing Block or rebooting the phone doesn't end the lock, and neither does setting the clock back.

Blocking runs in two independent layers, because phone vendors like to kill background services:

1. An accessibility service that notices when a locked app comes to the front.
2. A foreground service that checks the foreground app every 300 ms using usage access.

Both draw the same cover, and either one alone is enough to block.

Block never locks itself, System UI, Settings, the phone/dialer/emergency apps, contacts, or your home launcher, so you can't lock yourself out of making a call.

### Other things in the app

- A reminder text of your own, shown on the cover.
- A log of your last 100 locks, with a note you can add to each one ("lost two trades in a row", etc.).
- A day-by-day streak: after each day you mark whether you kept to your rules.
- Share or copy the whole log as text, e.g. to save it in Notion.
- Light, dark or automatic theme, and a background that either follows the lock state or slowly cycles colours.
- The countdown is a glass of water that drains as time passes and tilts with the phone.

The app's text is in Vietnamese only. There's a short Vietnamese guide in [HUONG_DAN.md](HUONG_DAN.md).

## Install

There are no releases. Build it yourself:

- **With GitHub Actions:** fork this repo, push, open the Actions tab, and download the `Block-apk` artifact from the latest run. It's a debug APK, so you'll need to allow installing from unknown sources.
- **Locally:** JDK 17, the Android SDK (compileSdk 34) and Gradle 8.9, then `gradle assembleDebug`. The APK ends up in `app/build/outputs/apk/debug/`. There's no Gradle wrapper in the repo.

Requires Android 8.0 or newer.

After installing, open Settings inside the app and grant the five permissions listed there: usage access, display over other apps, notifications, unrestricted battery, and the accessibility service. On some phones the accessibility switch is greyed out for sideloaded apps. Go to App info, open the ⋮ menu, and choose "Allow restricted settings", then try again.

## What's missing

- It's not tamper-proof. Settings is deliberately left unlocked, so you can still uninstall Block or turn off its permissions. It's meant to stop an impulse, not a determined you.
- I've only used it on one phone (ColorOS). Other vendors may need autostart and battery settings changed so the background service survives.
- It's signed with the debug key, so each new build has to be installed after uninstalling the old one, which wipes the log. Use Share on the log screen to back it up first.
- Locking an app doesn't close anything inside it. An open trade stays open, so set a stop loss before you walk away.

## Code

Plain Kotlin with no AndroidX and no XML layouts; the UI is built in code. `BlockOverlay` is the shared cover, `LockService` and `WatcherService` are the two blocking layers, and `LockStore` keeps everything in SharedPreferences.
