# Block

An Android app that locks the apps you pick for a set amount of time, and gives you no button to cancel it.

I built it to stop myself from reopening a trading app right after a losing trade. It works for any app you choose, though, so use it on whatever you tend to reach for when you shouldn't.

## How it works

Pick the apps, pick how long (15 minutes, 1 hour, until midnight, or anything from 1 minute to 24 hours), and press Lock.

From then on, opening a locked app shows a full-screen cover with the countdown and a reminder you wrote yourself. After 5 seconds it sends you back to the home screen. The small × in the corner does that immediately.

There is no stop button and no way to shorten the timer. Closing Block or rebooting the phone doesn't end the lock, and neither does setting the clock back.

Blocking runs in two independent layers, because phone vendors like to kill background services:

1. An accessibility service that notices when a locked app comes to the front.
2. A foreground service that checks the foreground app every 300 ms using usage access.

Both draw the same cover, and either one alone is enough to block.

While the lock is on, Block also kills the background process of every locked app, every 1.5 seconds, and every 0.4 seconds for 5 seconds after you leave one. That sounds rough, but it's there for a reason: on my phone, going back to an app through the recents screen doesn't report that the app came to the front, so the cover never showed. A killed app has to start from scratch when you tap it, and starting always gets reported.

There's a catch: if you tap the app's card in recents before it has been killed, it comes back without any report. So for 0.4 seconds after you leave a locked app, Block puts an invisible layer over the screen that swallows touches. By the time it goes away, the app is dead, and the next launch gets covered. Gestures still work, only taps on the screen don't.

Block never locks itself, System UI, Settings, the phone/dialer/emergency apps, contacts, or your home launcher, so you can't lock yourself out of making a call.

### Other things in the app

- A reminder text of your own, shown on the cover.
- An app picker with icons and a search box (it ignores accents and capitals).
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
- I've only used it on one phone, a Xiaomi (MIUI/HyperOS). Xiaomi and other vendors kill background apps aggressively, so turn on Autostart, set battery to "No restrictions", and lock Block's card in the recents screen.
- Locked apps lose their state while the lock is on, since their background process keeps getting killed. MT5 reloads its chart when you reopen it (open trades stay, they live on the broker's server), TikTok forgets where you were, half-typed messages are gone. Notifications from locked apps mostly still arrive, but I haven't checked every app and some may come late.
- Android doesn't let an app disable another app, only cover it, so there's always a moment between tapping and the cover showing. Usually about a second.
- On my Xiaomi the accessibility layer shows as enabled but doesn't receive events, so only the usage layer is doing the work there. Settings tells you whether layer 1 is really connected, and has a Diagnostics box showing what each layer saw. I haven't had enough days with the kill workaround to say it never slips. Banking apps (MB, for one) refuse to log in while any accessibility service is on, so on this phone I keep layer 1 switched off; layer 2 does the blocking by itself.
- It's signed with the debug key, so each new build has to be installed after uninstalling the old one, which wipes the log. Use Share on the log screen to back it up first.
- Locking an app doesn't close anything inside it. An open trade stays open, so set a stop loss before you walk away.

## Code

Plain Kotlin with no AndroidX and no XML layouts; the UI is built in code. `BlockOverlay` is the shared cover, `LockService` and `WatcherService` are the two blocking layers, and `LockStore` keeps everything in SharedPreferences.
