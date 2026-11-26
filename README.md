# FitOS

Food, workouts, tasks and progress in one simple app. Works on **iPhone and Android**, offline, with no app store.

## Install

**iPhone (Safari):** open **https://shreyfaldu08-dot.github.io/fitos/** → tap **📲 Add FitOS to your Home Screen** → Share → **Add to Home Screen** → **Add**.

**Android (Chrome):** open **https://shreyfaldu08-dot.github.io/fitos/** → tap **📲 Install FitOS on this phone**.

**Android app (APK, most features):** [download FitOS.apk](download/FitOS.apk) → open it on your phone → allow "Install unknown apps" if asked.

Or scan:

<img src="install-qr.png" width="180" alt="QR code for https://shreyfaldu08-dot.github.io/fitos/">

## What works where

| Feature | Web app (iPhone & Android) | Android APK |
|---|---|---|
| Food log, barcode scanning, meal suggestions | ✅ | ✅ |
| Tasks, calendar, milestones | ✅ | ✅ |
| Workout schedules, exercise photos, progress coach | ✅ | ✅ |
| Weekly report, backups, spreadsheet export | ✅ | ✅ |
| Works offline | ✅ | ✅ |
| Automatic steps, home-screen widget, reminders, daily auto-backup | ❌ | ✅ |

Each person's data stays private on their own phone. To move data between devices:
**Me → Backup and your data → Save a backup**, then **Restore from a backup file** on the other device.

## Repository layout

- `index.html`, `manifest.webmanifest`, `sw.js`, `icons/`: the installable web app (served by GitHub Pages)
- `download/FitOS.apk`: the Android app
- `android/`: Android app source (the signing key is intentionally not included)

Exercise photos: Free Exercise DB (public domain). Food product data: Open Food Facts. Barcode reading: ZXing (MIT).
