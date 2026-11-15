# SpendingSaver Next-Gen Finance App — Implementation Roadmap

> **For Hermes:** Use subagent-driven-development skill to implement this plan phase-by-phase (one subagent per task/phase, two-stage review).

**Goal:** Transform the verified self-contained Android finance app into a production-grade, next-generation personal finance app via an optimally-ordered, local-first roadmap (no server, no companion desktop app, no network sync by default).

**Architecture:** Single self-contained Android app (Kotlin + Jetpack Compose + Hilt + Room). All features run on-device. Cloud/bank-feed features are explicitly gated behind an optional *Sync & Bank Feeds* toggle that is off by default and ships no telemetry. State is the local Room DB with explicit migrations; all exports/import are user-initiated file operations.

**Target tech stack:** Kotlin 1.8.22, Compose + Material 3, Hilt DI, Room 2.5.2 (KSP), SQLCipher (encryption), WorkManager (scheduling), AndroidX Security/Keystore, TensorFlow Lite (on-device ML), App Widgets, Glance, Speech-to-text (on-device), `cmd.exe /c` Gradle wrapper 8.2 (never system Gradle 9.2.1), AGP 8.1, `compileSdk/targetSdk 34`. Items not present in the baseline are planned, not current dependencies.

**Ordering rationale:** Phases are dependency- and risk-ordered — foundations (data integrity, undo, reconciliation) before analytics that depend on them; ML intelligence before the convenience surfaces that expose it; production hardening interleaved so each shipped increment is testable. Each feature lists its blockers so nothing is built out of order.

---

## PHASE 0 — BASELINE VERIFIED (current state)

The app already compiles, installs, and runs on the Nokia 7.2.

- `assembleDebug` → `BUILD SUCCESSFUL in 36s` (2026-10-22).
- APK: `app/build/outputs/apk/debug/app-debug.apk` (15.5 MB, valid `PK` zip magic).
- Device: Nokia 7.2, installs after uninstalling stale signature-mismatched copy, `MainActivity` resumed, no `FATAL EXCEPTION` in logcat.
- Verified render: Settings screen (dark mode, teal accent, Appearance / Privacy & display / Currency & dates sections).
- Current local-first features present: Account/Category/Transaction/Budget entities + DAOs (soft-delete `deleted` flag, `getAllOnce`, `deleteAll`), repositories (no SyncQueue), Hilt DI, AppDatabase v3 with MIGRATION_2_3, MainActivity (no server nav/connect), MainViewModel + DataBackupRepository (JSON export/import), SettingsScreen "Data & backups" section, AndroidManifest network-free (no INTERNET, no cleartext).
- Environment locked: `local.properties` points to the local Android SDK, `platforms;android-34` installed, Gradle wrapper 8.2.

P0 — nothing to do; this is the verified launch pad.

---

## PHASE 1 — CORE UX & DATA INTEGRITY (foundations)

*Dependencies: none. Must come first — everything else reads/writes this data.*

| Feature | Priority | Effort | Files (approx) |
|---|---|---|---|
| 1.1 Reconciliation engine | P0 | M | `data/Reconciliation.kt`, `dao/TransactionDao.kt` (add `reconciled:Boolean`, `statementId`), `repository/TransactionRepository.kt`, `ui/screen/ReconciliationScreen.kt` |
| 1.2 Full transaction edit + undo/redo stack | P0 | M | `data/TransactionDao.kt` (update soft-delete to versioned), `ui/viewmodel/MainViewModel.kt` (undo stack), `ui/components/TransactionEditor.kt` |
| 1.3 Tags/labels on transactions | P1 | S | `data/Tag.kt` entity, `TransactionTag` cross-ref, `dao/TagDao.kt`, `ui/components/TagChips.kt`, `ui/screen/TransactionsScreen.kt` |
| 1.4 Rich search (full-text + facets) | P1 | M | `data/AppDatabase.kt` (FTS table for payee/notes), `repository/SearchRepository.kt`, `ui/screen/SearchScreen.kt`, add to `SearchScreen` |
| 1.5 Attachments: receipt photos, PDFs, per-transaction notes | P1 | M | `data/Attachment.kt`, `ui/components/ReceiptCapture.kt` (CameraX + file picker), `ui/nav/` route |
| 1.6 Split transactions | P2 | L | `data/Split.kt`, `TransactionDao` relation, `ui/components/SplitEditor.kt`, update backup serializer |
| 1.7 Duplicate-transaction detection (by amount+date+payer) | P2 | S | `repository/TransactionRepository.kt` (`findDuplicates`), flag in editor |
| 1.8 Encrypted local storage (SQLCipher + Keystore) | P0 | M | `di/AppModule.kt` (SQLCipher `SupportFactory`), `util/CryptoUtil.kt`, migrate `MIGRATION_2_3→4` re-encrypt |

**Validation per feature:** unit test in `core/` (math, dedupe), instrumentation test in `androidTest/` (editor flow, search), `adb install` smoke on Nokia 7.2. Reconciliation and encryption are non-negotiable — block Phase 2.

---

## PHASE 2 — BUDGETING & PLANNING POWER

*Dependencies: Phase 1 entities/tags/search stable.*

| Feature | Priority | Effort | Files |
|---|---|---|---|
| 2.1 Multi-method budgeting (envelope + rolling avg + zero-based) | P0 | M | `data/Budget.kt` (add `method` enum), `ui/screen/BudgetsScreen.kt`, `ui/components/BudgetMethodSelector.kt` |
| 2.2 Savings goals + milestones | P0 | M | `data/Goal.kt`, `dao/GoalDao.kt`, `ui/screen/GoalsScreen.kt`, `GoalViewModel.kt` |
| 2.3 Cash-flow forecast (next 30/90 days) | P1 | L | `data/Forecast.kt`, `repository/ForecastRepository.kt`, `ui/screen/ForecastScreen.kt` |
| 2.4 Bill/subscriptions tracker + due reminders | P1 | M | `data/Bill.kt`, `dao/BillDao.kt`, WorkManager `BillReminderWorker.kt`, `ui/screen/BillsScreen.kt` |
| 2.5 Debt payoff planner (avalanche/snowball + amortization) | P2 | L | `data/Debt.kt`, `ui/screen/DebtPlannerScreen.kt` |
| 2.6 Budget/forecast export to PDF | P2 | S | `util/PdfExportUtil.kt`, share via `Intent` |

**Validation:** Forecast math unit-tested against known datasets; bill reminders verified on-device via `adb shell` time-jump; amortization schedule checked vs. a known loan.

---

## PHASE 3 — ANALYTICS & REPORTING

*Dependencies: Phase 1 data integrity + Phase 2 budgets (reports read them).*

| Feature | Priority | Effort | Files |
|---|---|---|---|
| 3.1 Dashboard charts (spending-over-time, category donut, net-worth) | P0 | M | `ui/screen/DashboardScreen.kt`, `ui/components/` charts (MPAndroidChart or Compose `LineChart`), `repository/ReportingRepository.kt` |
| 3.2 Period comparisons (MoM, YoY, PPL) | P1 | S | `repository/ReportingRepository.kt`, `ui/components/ComparisonCard.kt` |
| 3.3 Custom date ranges + saved report presets | P1 | S | `ui/components/DateRangePicker.kt` |
| 3.4 Report export (CSV + PDF) | P1 | S | reuse `CSVUtil.kt` + `PdfExportUtil.kt` |
| 3.5 Recurring-pattern detection (variable recurring spend) | P2 | M | `service/RecurringPatternDetector.kt` (on-device), surface on dashboard |

**Validation:** Snapshot tests for charts in `androidTest/`; compare report math against a hand-computed CSV fixture in `core/`.

---

## PHASE 4 — AUTOMATION & INTELLIGENCE (next-gen differentiators)

*Dependencies: Phases 1–3 produce clean, categorized data to learn from.*

| Feature | Priority | Effort | Files |
|---|---|---|---|
| 4.1 On-device ML auto-categorization (TFLite) | P0 | L | `ml/Categorizer.kt` (TFLite ModelMaker/SelectOpSupport), `service/CategorizationService.kt`, `data/TrainingExample.kt` |
| 4.2 Natural-language transaction entry ("$40 grocery today") | P0 | M | `service/NlpParser.kt` (on-device grammar, not a cloud call), `ui/components/NleEditor.kt` |
| 4.3 Anomaly / unusual-spend detection + alerts | P0 | M | `service/AnomalyDetector.kt`, WorkManager worker, notification channel |
| 4.4 Predictive cash flow ("you'll be short $X before payday") | P1 | M | build on Phase 2 forecast + ML trend; `service/RiskAlertWorker.kt` |
| 4.5 Smart recurring detection (learn undeclared bills) | P1 | M | reuse 3.5 detector + ML |
| 4.6 On-device LLM assistant ("explain my September spending") | P2 | L | local LLM via lmstudio/gguf on-device OR a gated optional local LLM runtime; privacy-first by design (never sends data) |

**Hard constraint:** *All* of 4.1–4.6 run on-device. No network calls. 4.6 (LLM) is the one place to double-check the local runtime is bundled or optional; if no reliable on-device LLM runtime exists at shipping quality, defer to v1.1 with a "Local LLM" feature flag (off).

**Validation:** categorization accuracy ≥90% on a held-out fixture; NLE parses 20 canonical expressions; anomaly detector fires on a crafted outlier and stays quiet on a flat series.

---

## PHASE 5 — SURFACE & CONVENIENCE (stickiness)

*Dependencies: Phase 4 intelligence exists to surface; Phase 1 attachments exist to capture.*

| Feature | Priority | Effort | Files |
|---|---|---|---|
| 5.1 Home-screen widget + Glance quick-add | P0 | M | `ui/widget/SpendingWidget.kt` (Glance), `ui/widget/QuickAddReceiver.kt` |
| 5.2 Notification-shade quick entry | P0 | S | `ui/widget/QuickEntryActivity.kt` |
| 5.3 App shortcuts + adaptive icon + predictive back | P1 | S | `AndroidManifest.xml` shortcuts, `res/mipmap-*` |
| 5.4 Biometric/PIN app lock | P0 | M | `ui/screen/LockScreen.kt`, `util/BiometricPromptWrapper.kt`, `AppLockViewModel.kt` |
| 5.5 Receipt scanning via on-device OCR (ML Kit) | P1 | L | `service/ReceiptOcrAnalyzer.kt`, `CameraX` + `TextRecognizer` |
| 5.6 Voice entry (on-device STT) | P1 | S | `util/VoiceInputWrapper.kt`, trigger from NLE |
| 5.7 Dark mode + Material 3 dynamic color | already present | XS | confirm `res/values-night`, `DynamicColor` |

**Validation:** widget updates with live balance without killing the app; lock screen survives process death; OCR extracts totals ≥85% on test receipts.

---

## PHASE 6 — INVESTMENT & POWER-USER IMPORT

*Dependencies: Phase 1 multi-currency + reconciliation foundation.*

| Feature | Priority | Effort | Files |
|---|---|---|---|
| 6.1 Investment/portfolio tracking (holdings, cost basis, GL) | P1 | L | `data/Holding.kt`, `dao/HoldingDao.kt`, `service/CostBasis.kt`, `ui/screen/PortfolioScreen.kt` |
| 6.2 Market data (offline cache + optional live) | P1 | S | `service/MarketCache.kt`; live fetch is the *only* network path, gated + disabled by default |
| 6.3 OFX/QFX/QIF import (bank statement files) | P1 | M | `util/OfxParser.kt`, `repository/ImportRepository.kt`, `ui/screen/ImportScreen.kt` |
| 6.4 Multi-currency with base + reporting currency, historical rates | P0 | M | extend `Account`/`Transaction` currency fields, `service/FxRateStore.kt`, migration |
| 6.5 Receipts + expense claims export | P2 | XS | reuse attachments + PDF export |

**Validation:** OFX parses the standard test fixture suite (banktested.com samples); cost-basis matches a hand-computed FIFO/LIFO; portfolio value tracks a known price series.

---

## PHASE 7 — PRODUCTION HARDENING (quality floor)

*Interleaved throughout, but locked down before v1.0.*

| Feature | Priority | Effort | Files |
|---|---|---|---|
| 7.1 Local-first a11y (TalkBack labels, dynamic type, contrast) | P0 | M | every `ui/` component — labels + `semantics {}` |
| 7.2 i18n: strings, RTL layout, locale-aware fmt | P0 | M | `res/values-*/`, `util/Formatter.kt` |
| 7.3 Encrypted scheduled local backups (SAF + ZIP+AES) | P0 | M | `service/BackupScheduler.kt` (WorkManager), `util/EncryptedZip.kt` |
| 7.4 Unit tests (`core/`) — ≥80% coverage of repositories/math | P0 | L | `core/repository/*Test.kt`, `core/service/*Test.kt` |
| 7.5 Instrumentation tests (`androidTest/`) for main flows | P0 | L | `androidTest/UiTestCase.kt` (Hilt + compose-test) |
| 7.6 CI/CD: build + tests + lint + signed release (GitHub Actions) | P0 | M | `.github/workflows/build.yml`; `assembleRelease` needs a signing config (currently none — add debug signing for internal builds) |
| 7.7 Crash/error reporting that respects privacy (opt-in, self-contained logs) | P1 | S | `util/PrivacyLogger.kt` → user-exported log file; no third-party SDK |
| 7.8 Performance budget: cold start <2s, 100k txns no jank | P0 | M | `util/PerfMetrics.kt`, benchmarks in `androidTest/` |
| 7.9 Legal/privacy: no-data-sold stance, GDPR export/delete | P0 | XS | `privacy_policy.md` in repo, in-app "Export all data" + "Delete all data" |
| 7.10 Release signing (internal/prod keys) + staged rollout setup | P1 | M | `gradle.properties` + `signingConfigs` (prod key in secret store) |

**Validation gate for v1.0:** `./gradlew :app:assembleDebug` green, `lintDebug` clean, unit + instrumentation tests green on the Nokia 7.2, cold start < 2s with 50k seeded transactions.

---

## CROSS-CUTTING CONVENTIONS (apply to every feature)

- **No network by default.** Any feature touching the network (6.2 market data, bank feeds) is behind an explicit *Sync & Bank Feeds* toggle, off by default, with a runtime permission gate. The default build must pass `AndroidManifest.xml` has `INTERNET` removed (current state) lint.
- **Soft-delete everywhere.** Repositories mark `deleted=true`; never hard-delete user data. Backups preserve `deleted` for restoration.
- **Migrations are explicit.** Every `AppDatabase` schema change ships an `MIGRATION_N_(N+1)`. Never `fallbackToDestructiveMigration` on the user path.
- **TDD bite-sizes.** Each feature → failing test (`core/` unit or `androidTest/` instrumentation) → minimal impl → green → commit. Tasks are ~2–5 min each (e.g., "Add `reconciled:Boolean` column + migration" ≠ "Build reconciliation"). Decompose further than the table rows above when executing.
- **Verification-first.** Every feature is verified: `adb install`+smoke on Nokia 7.2 for UI, unit math for logic, fixture files for importers (OFX/CSV/JSON).
- **Accessibility first.** Every new composables get `semantics { }` + content description before merge.
- **Generative UI note:** Phase 5/7 includes a *Dynamic Dashboard* stretch (v1.1) — let an on-device agent rearrange widgets/generate themes at runtime. Out of scope for v1.0; parked here for visibility.
- **Build env:** run `gradlew.bat` from `SpendingSaverAndroid` with JDK 17 and the locally configured Android SDK. Use the Gradle 8.2 wrapper; AGP 8.1 compatibility with newer Gradle versions is not assumed.

---

## DEPENDENCY GRAPH (build order summary)

```
Phase 0 (baseline)
  └─> Phase 1 (data integrity: encryption, reconciliation, undo, tags, search, attachments)
        └─> Phase 2 (budgets/goals/focus — uses clean data)
              ├─> Phase 4 (ML/automation — needs categorized data)
              └─> Phase 3 (analytics — reads budgets + transactions)
                    └─> Phase 5 (surfaces expose intelligence)
Phase 6 (power import) needs Phase 1 currency + OFX
Phase 7 (hardening) interleaved, gates v1.0
```

## PRIORITY CUT (what ships v1.0 vs v1.1)

**v1.0 must (P0/P1 in Phases 1–5, 7):** reconciliation, undo/redo, tags, search, encrypted storage, attachments, multi-method budgeting, savings goals, forecast, bills+reminders, dashboard charts, period comparisons, ML categorization, NLE entry, anomaly alerts, app lock (biometric/PIN), quick-add widget + QS tile, full a11y, i18n, scheduled encrypted backups, unit+instrumentation tests, CI green, cold-start < 2s, legal/privacy copy.

**v1.1 stretch (P2 / L / optional-runtime):** split transactions, portfolio tracking, OFX/QFX import, market data, on-device LLM assistant, receipt OCR, dynamic dashboard (generative UI).

## EXECUTION HANDOFF

**Plan complete and saved.** Path: `.hermes/plans/2026-10-22_100200-spendingsaver-roadmap.md`.

Ready to execute phase-by-phase using subagent-driven-development — a fresh subagent per phase with spec-compliance + code-quality review, verified against `assembleDebug` and the Nokia 7.2 after each phase. Phase 0 is already green.

Which phase would you like executed first? (Recommended start: Phase 1, task 1.8 encrypted storage → 1.1 reconciliation, since they're P0 and block all downstream data quality.)
