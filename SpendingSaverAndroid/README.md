# SpendingSaver Android

The Android app is a local-first personal finance app built with Kotlin,
Jetpack Compose, Room, and Hilt. Accounts, categories, transactions, and budgets
are stored on the device. The app does not connect to a companion server or
perform background network sync.

## Data backup and restore

Open **Settings → Data & backups** to export or restore a JSON backup using the
Android document picker.

- Export includes active accounts, categories, transactions, and budgets.
- Restore replaces those four local datasets with the contents of the selected
  backup. Export a backup first if you need to preserve the current data.
- Deleted rows, app settings, and other device state are not included.
- Backup files are user-managed files; keep them somewhere safe.

## Build

Requirements: JDK 17 and Android SDK platform 34.

From this directory, build a debug APK with the Gradle wrapper:

```bat
gradlew.bat :app:assembleDebug
```

The project uses Gradle 8.2 and Android Gradle Plugin 8.1.0. Do not replace the
wrapper with a newer Gradle version without checking Android Gradle Plugin
compatibility.

The built APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Local database

Room database schema version 3 removes the former sync outbox and sync metadata.
The `1 → 2` migration converts legacy numeric identifiers to strings, and the
`2 → 3` migration removes sync-only columns while preserving finance records.
