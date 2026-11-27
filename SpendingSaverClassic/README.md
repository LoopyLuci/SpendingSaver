# SpendingSaver Classic

Release file: `SpendingSaver-<version>-kindle-fire-kfot-android-4.0.3.apk`. It was built and tested on the Kindle Fire (KFOT) running Android 4.0.3 and MAY work on similar devices and Android versions.

The version of SpendingSaver for **very old Android devices** (Android 4.0.3 and newer), such as the Kindle Fire 2nd
generation. The main Android app needs Android 5 or newer, so this one exists for the devices below that.

It is a small native app written with plain Android views and no libraries (about 35 KB). It has its own application
id, `com.spendingsaver.classic`, so it installs next to the main app and never touches its data. It is signed with the
same release key as the other SpendingSaver apps.

## What it does

- Accounts (with a type and a currency), transactions (add, edit, delete, with suggestions from past descriptions),
  categories, and budgets with a progress bar.
- A backup file in the **same format** as the other SpendingSaver apps (`version` 1). You can save a backup to the
  Downloads folder and restore one from there, so data can be copied between apps by hand. This is a file you move
  yourself; it is **not sync**.
- Everything stays on the device. There is no sign-in, no network access and no sync.

It leaves out the tax estimate, the assistant, recurring items, goals, debts, the updater and phone-to-computer sync;
use the main app on a device that can run it.

## Where the data lives

One JSON file in the app's private folder (`files/data.json`), in the backup layout. Rows are only flagged deleted,
never removed, like the other apps. Saving writes a temporary copy and renames it, so a crash cannot leave half a file.

## Build and test

```powershell
cd SpendingSaverClassic
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug     # tests and a debug APK
.\gradlew.bat :app:assembleRelease                           # signed with ../SpendingSaverAndroid/keystore.properties
```

`scripts/check.ps1 -Only classic` runs the tests and a debug build. `scripts/build-release.ps1` builds the release file
`SpendingSaver-<version>-kindle-fire-kfot-android-4.0.3.apk`.

## Tested on

- Kindle Fire 2nd generation (KFOT), Android 4.0.3, 32-bit ARM: installs, runs, adds an account and a transaction,
  saves a backup, restores it. The backup it writes is read correctly by the web app's parser, and a backup from the real
  Android app is read correctly by this app's tests.
- Not tested on other old devices, on Android 6 to 9 (where Downloads access asks for permission at first use), or
  with other keyboards. On Android 10 and newer, saving to Downloads is not available to this app; use the main app there.
