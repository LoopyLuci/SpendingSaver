# SpendingSaver Android

The Android app is a local-first personal finance app built with Kotlin,
Jetpack Compose, Room, and Hilt. Accounts, categories, transactions, and budgets
are stored on the device and the app is fully usable on its own. It can
optionally pair with the SpendingSaver desktop backend to sync those four
datasets; nothing is sent until you pair and tap **Sync now**.

## Optional desktop sync

**Settings → Desktop sync (optional)**: enter the desktop backend's address and
its pairing key, then **Pair and sync**. Afterwards **Sync now** pushes your
changes and pulls the desktop's. Last write wins per row; deletes sync;
**Unpair** forgets the hub and never deletes data on the phone. Setup, security
(token storage, `http://` limited to local addresses), merge rules and
troubleshooting: [`polyglot-app/docs/sync.md`](../polyglot-app/docs/sync.md).

Stores: the app reads and writes only its own Room database; the sync token and
cursors live in the app-private preferences file `spendingsaver_sync.xml`
(excluded from Android backup). The hub is the desktop backend's SQLite
database. Network use: the `INTERNET` permission and a cleartext-capable network
security config exist for the hub and the user-configured AI service; the app
itself only accepts `http://` hub addresses on local networks.

## Data backup and restore

Open **Settings → Data & backups** to export or restore a JSON backup using the
Android document picker.

- Export includes active accounts, categories, transactions, and budgets.
- Restore replaces the active contents of those four datasets with the selected
  backup. Export a backup first if you need to preserve the current data. A file
  that is not a complete, supported backup is rejected before anything changes.
  Rows missing from the backup are soft-deleted (kept as tombstones) and
  restored rows are stamped with the restore time, so a restore also wins over
  older data when synced with the desktop.
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

To install next to an existing build signed with a different key (without
uninstalling it or losing its data), add an application-id suffix:

```bat
gradlew.bat :app:assembleDebug -PdebugAppIdSuffix=.debug
```

Unit tests (JUnit 5/MockK, JVM only; there are no instrumented tests):

```bat
gradlew.bat :app:testDebugUnitTest
```

Release signing reads `keystore.properties` in this directory (`storeFile`,
`storePassword`, `keyAlias`, `keyPassword`). The file is git-ignored; never
commit it. Without it, release builds are unsigned.

## Local database

Room database schema version 3 removes the former sync outbox and sync metadata.
The `1 → 2` migration converts legacy numeric identifiers to strings, and the
`2 → 3` migration removes sync-only columns while preserving finance records.
Sync needs no schema change: it uses the existing `updatedAt` and `deleted`
columns. The AI and tax features keep separate data (tax data is in its own
database, `taxes.db`, and is neither backed up nor synced).
