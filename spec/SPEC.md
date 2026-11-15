# SpendingSaver Product Specification

| Field | Value |
| --- | --- |
| Status | Draft; implementation-aware, not yet product-approved |
| Version | 1.0 |
| Last reviewed | 2026-11-15 |
| Scope | Android, browser, desktop/backend, and MCP projects in this repository |
| Source of truth | This document for product intent; code and project READMEs for current behavior |

## 1. Purpose

SpendingSaver helps an individual record and understand personal finances:
accounts, income and expenses, categories, budgets, and financial summaries.
It should make routine entry and review understandable, preserve data, and
give the user control over where financial information is stored and exported.

This specification consolidates the useful product intent from the former
`IDEA.md` and replaces its stale source-code examples. It distinguishes:

- **Requirement:** behavior the product is intended to provide.
- **Status:** evidence in the checked-in implementation as of the review date.
- **Gap:** behavior that is partial, inconsistent across apps, or not yet
  implemented.

It does not promise a delivery date or authorize a new cross-app architecture.

## 2. Normative language

**MUST**, **MUST NOT**, **SHOULD**, **SHOULD NOT**, and **MAY** express the
strength of a requirement. Status labels mean:

- **Implemented:** present in the relevant checked-in project.
- **Partial:** some behavior exists, but not all required behavior or
  acceptance criteria are met.
- **Varies:** behavior differs between the separate apps.
- **Planned:** a product requirement with no verified implementation in the
  indicated project.

Status is based on source/configuration review, not a complete feature or
security audit. A link to a project guide is not evidence that every
requirement has been tested.

## 3. Product boundaries

The repository contains four separately built projects:

1. **Android** (`SpendingSaverAndroid/`): Kotlin/Compose UI with a local Room
   database.
2. **Browser** (`spendingsaver-web/`): React/TypeScript UI with Dexie storage
   in browser IndexedDB.
3. **Desktop/backend** (`polyglot-app/`): Tauri/React UI, Python FastAPI and
   SQLite backend, plus a Go HTTP service.
4. **MCP adapter** (`spendingsaver-mcp/`): stdio MCP tools that call the
   desktop/backend HTTP API.

These are not clients of a common account service. The Android and browser
projects do not automatically sync with each other or the desktop backend.
Android JSON backup/restore and browser CSV transaction import/export are
manual, distinct workflows; their files are not currently a shared portable
format. The MCP adapter operates on the desktop/backend API only.

Requirements in this document apply to a project only where that project's
scope includes the corresponding feature. A platform-specific capability must
not be described as available in another app without implementation evidence.

## 4. Goals and non-goals

### 4.1 Goals

- Let the user record and review personal accounts, transactions, categories,
  and budgets.
- Provide useful spending, budget, and net-worth summaries from the data the
  user entered or imported.
- Make data persistence and export behavior explicit.
- Avoid artificial application limits on ordinary personal-finance records;
  performance and storage limits still depend on the platform.
- Keep core local applications useful without requiring account creation or a
  companion server.
- Keep features that access a service, bank connection, or remote endpoint
  explicit about what data leaves the device and why.

### 4.2 Non-goals unless separately approved

- Automatic synchronization between the four projects.
- Treating the Android JSON backup, browser CSV, and backend backup as
  interchangeable.
- Providing financial, tax, investment, or credit advice.
- Guaranteeing bank connectivity, exchange-rate availability, or uptime of
  third-party services.
- Claiming encryption, legal compliance, data deletion, or privacy properties
  that have not been implemented and verified for the relevant app.

## 5. Users and primary workflows

The primary user is an individual who wants to manage day-to-day finances
without requiring a bookkeeping system.

### 5.1 Initial setup

1. Open one of the apps and create or review categories.
2. Create accounts with a name, type, currency, and starting balance where
   supported.
3. Record transactions manually or import a supported transaction file.
4. Create category budgets and review spending summaries.
5. Export data periodically when the app provides an export or backup feature.

### 5.2 Routine transaction review

1. Add or import a transaction with an account, date, description, amount, and
   optional category and notes.
2. Review any suggested category; automated categorization is a suggestion and
   must not silently prevent the user from correcting the result.
3. Edit or remove an incorrect transaction where supported.
4. Review dashboard, category, budget, and net-worth views.

### 5.3 Restore and portability

Before a replacement restore, the UI MUST tell the user which local data will
be replaced. Restore MUST report success or failure and MUST NOT report success
if the selected file could not be read or applied.

The current Android restore replaces the active account, category, transaction,
and budget datasets in one Room transaction. The current Android backup
contains those active rows, not deleted rows or app settings. Browser export
currently exports transactions as CSV; it is not a full database backup.
Portable formats across all apps remain an open decision.

## 6. Core domain model

The following is the conceptual model. Concrete schemas and identifiers may
differ between implementations; migrations and import mappings must account
for those differences.

### 6.1 Account

- Stable identifier within its owning store.
- User-visible name.
- Account type (for example checking, savings, credit, investment, or other).
- Balance and currency.
- The product MUST make clear whether a displayed balance is user-entered,
  transaction-derived, or supplied by an external connection.

### 6.2 Transaction

- Stable identifier within its owning store.
- Owning account.
- Amount, transaction date, and description.
- Optional category and notes.
- Optional creation/update metadata.
- A consistent sign convention: positive values represent income and negative
  values represent expenses in the current browser and Android models.
- Edits, categorization, imports, and deletes MUST preserve the relationship
  to the owning account and the record's original meaning.

### 6.3 Category

- Stable identifier, name, optional color, and optional parent category.
- Categories are user-visible labels used by transactions and budgets.
- A category deletion MUST NOT silently corrupt or reassign associated
  transactions; the exact reassignment/retention interaction needs a
  cross-project decision.

### 6.4 Budget

- Stable identifier, category, amount, period, and start date.
- The period MUST be explicit and interpreted consistently in summaries.
- Current projects expose different period sets; the shared supported set is
  not yet standardized.

### 6.5 Optional/advanced records

The desktop/backend includes additional records and operations for recurring
transactions, savings goals, debts, investments, splits, tags, reminders,
income streams, and other features. These are extensions, not a shared schema
across the Android and browser apps.

## 7. Functional requirements

| ID | Requirement | Priority | Status at review |
| --- | --- | --- | --- |
| FR-01 | Users can create and review accounts with a name, type, balance, and currency where supported. | Core | Varies |
| FR-02 | Users can create and review income and expense transactions associated with an account. | Core | Implemented in all three UIs; capabilities differ |
| FR-03 | Users can assign, change, and review transaction categories. | Core | Implemented; categorization behavior varies |
| FR-04 | Users can create budgets associated with categories and periods. | Core | Implemented; period coverage varies |
| FR-05 | The app provides a dashboard summarizing relevant financial records. | Core | Implemented; content varies |
| FR-06 | The app provides a net-worth view based on account balances and clearly handles currencies. | Core | Partial; currency aggregation is not consistent |
| FR-07 | Where CSV import is offered, the user can choose the target account and receive understandable import feedback. | Core | Partial; browser and Android expose import workflows |
| FR-08 | Where CSV export is offered, the exported records and columns are identified to the user. | Core | Implemented with differing export scope |
| FR-09 | Deletion and restoration behavior is explicit and does not silently lose unrelated data. | Core | Varies; browser hard-deletes some records, Android uses soft-delete for core rows |
| FR-10 | The UI offers a clear appearance preference and readable presentation. | Core | Partial; themes/settings differ |
| FR-11 | Restore from a backup validates input, replaces only the documented scope, and is atomic or otherwise recoverable. | Core | Partial; Android uses a Room transaction but backup validation/tests are absent |
| FR-12 | Automated categorization is reviewable and does not make an irreversible decision on the user's behalf. | Should | Partial; rules/suggestions exist in some paths |
| FR-13 | Cross-app data transfer has a documented, versioned portable format. | Should | Planned |
| FR-14 | Optional remote integrations explain their data flow and require explicit configuration or consent. | Should | Partial; desktop/backend has connection and hosting features |
| FR-15 | Advanced features are clearly scoped to the app that implements them. | Should | Documentation requirement |

### 7.1 Core UI areas

The core navigation areas are:

- Dashboard.
- Transactions.
- Accounts.
- Categories.
- Budgets.
- Net worth.
- Import/export.
- Settings where supported.

An app may combine or rename areas while preserving discoverability. Advanced
desktop pages (such as recurring, debt, investment, insights, diagnostics, and
device connection pages) are optional extensions and do not imply parity in
other apps.

### 7.2 Categorization

Categorization MAY use user-maintained rules or matching suggestions. It MUST:

- Use the user's categories rather than inventing an undisclosed category.
- Leave an unmatched transaction uncategorized or ask the user.
- Allow correction after import or automatic suggestion.
- Avoid changing unrelated transactions when a category or rule is edited.

Rule editing, bulk categorization, and batch transaction editing are not
implemented uniformly and are not baseline acceptance criteria for every app.

### 7.3 Budgets and summaries

Budget progress MUST be calculated from transactions that match the selected
category and period. The UI SHOULD expose the period and date range used in the
calculation. A summary MUST NOT combine values with different currencies
without conversion to a stated reporting currency and rate basis.

The rule for transfers, refunds, deleted transactions, carry-over, and
transactions without a category remains to be standardized across projects.

### 7.4 Net worth and currency

Net worth is a derived summary of assets and liabilities, not a bank-verified
figure. The UI MUST distinguish a manually maintained balance from a
transaction-derived or externally synchronized balance. Mixed-currency totals
MUST either use an explicitly identified conversion or show separate
currency totals; adding raw values across currencies is not acceptable.

This requirement is not met consistently by the current implementations and
requires follow-up before cross-currency totals are relied upon.

## 8. Data, storage, and portability

### 8.1 Data ownership

- Each app MUST disclose its actual data store and persistence boundary.
- A local data store MUST remain usable without a network connection for
  features that do not explicitly require remote services.
- A remote-service feature MUST identify the service and data sent to it.
- No app may imply that data is shared across projects unless a supported
  import or synchronization path exists.

### 8.2 Persistence by project

| Project | Current primary store | Current export/transfer behavior |
| --- | --- | --- |
| Android | Room database on device | User-selected JSON export/restore for active accounts, categories, transactions, and budgets |
| Browser | Dexie in IndexedDB for the browser profile and origin | CSV import into a selected account; CSV export of transactions |
| Desktop/backend | SQLite database under `polyglot-app/backend/data/` | Backend CSV and backup/portable-backup endpoints |
| MCP | No independent finance database | Reads/writes through the configured backend API |

### 8.3 Import and backup safety

- Imports SHOULD validate required fields and report skipped/failed rows.
- Duplicate handling MUST be explicit; silently creating duplicates or
  silently discarding rows is not acceptable.
- A full restore is a replace operation unless the UI explicitly offers a
  separate merge mode.
- A backup SHOULD carry a schema/version identifier and export timestamp.
- Backups SHOULD be treated as sensitive financial data and stored by the
  user in an appropriate location.
- Restore and migration code MUST preserve data on failure. Before release,
  test malformed, incomplete, and older-version inputs.

The Android JSON document currently has a version field and export timestamp,
but restore validation and compatibility behavior need dedicated tests.

## 9. Privacy and security requirements

1. **Data minimization:** collect and transmit only data needed for an enabled
   feature.
2. **No advertising/data sale:** the original product intent is no ads and no
   sale of financial data. Treat that as a product requirement, not as a
   verified legal or implementation claim; any analytics, crash reporting,
   or third-party SDK must be reviewed against it.
3. **Network disclosure:** local-only features MUST NOT require network
   access. Remote features MUST be opt-in or clearly configured and
   documented.
4. **Credential safety:** never store secrets in source control or expose
   backend/MCP services to untrusted networks without appropriate
   authentication and deployment controls.
5. **Sensitive exports:** clearly communicate that CSV/JSON files may expose
   financial information and are not necessarily encrypted.
6. **Access and deletion:** authentication, authorization, account deletion,
   and retention rules MUST be specified for any hosted multi-user deployment.
   The existence of backend auth routes alone does not establish a secure
   deployment.
7. **Encryption claims:** document encryption only where key management,
   storage, migration, and recovery behavior are implemented and verified.

The Android manifest currently does not request the Internet permission. The
desktop/backend and MCP projects have network-facing behavior and must be
assessed separately; do not extend the Android claim to those projects.

## 10. Usability and accessibility

- Primary record creation and review SHOULD be reachable from the main
  navigation.
- Empty states and validation failures SHOULD explain a next step.
- Amounts, dates, currency, and period boundaries SHOULD use readable,
  locale-aware formatting.
- Controls MUST have accessible labels and usable focus/keyboard or assistive
  technology behavior appropriate to the platform.
- Text scaling, contrast, and narrow-screen layouts SHOULD be tested.
- Success messages MUST reflect completed work; errors MUST remain visible
  and not be converted into success-shaped status.

Accessibility, internationalization, and shared formatting are current
follow-ups, not a verified repository-wide guarantee.

## 11. Reliability and performance

- Database migrations MUST be explicit and data-preserving.
- A restore or multi-record import SHOULD be transactional or offer a
  recoverable failure path.
- The app SHOULD continue to show an actionable error if persistence or file
  access fails.
- There are no approved cross-platform performance thresholds in this draft.
  Performance targets must be agreed and measured on representative devices
  and datasets before becoming release gates.
- Data limits MUST be based on measured platform constraints, not arbitrary
  product limits.

## 12. Implementation status by project

This matrix is a source-review snapshot, not an exhaustive audit. “Present”
means code/configuration was found; it does not mean every edge case passes.

| Capability | Browser app | Android app | Desktop/backend |
| --- | --- | --- | --- |
| Accounts, transactions, categories, budgets | Present; local IndexedDB | Present; local Room | Present; backend API and SQLite |
| Dashboard and net worth | Present | Present | Present |
| CSV transactions | Import/export UI | Import/export UI | API import/export routes |
| JSON/full backup | Not a full DB backup | Settings export/restore of active core rows | Backend backup/portable-backup routes |
| Appearance/settings | Light/dark toggle | Theme, accent, text scale, and display preferences | Settings page |
| Recurring and other advanced finance features | Not in current navigation | Not in current navigation | Multiple API routes and UI pages |
| Tests | No test script or test files checked in | No `test`/`androidTest` sources checked in | Backend, Go, frontend, and Rust test files/scripts exist; clean setup/CI has gaps |
| Automatic synchronization with other apps | No | No | Backend has service/API integrations; no verified common sync contract with the other apps |

Project-specific details:

- [Browser project guide](../spendingsaver-web/README.md)
- [Android project guide](../SpendingSaverAndroid/README.md)
- [Desktop/backend guide](../polyglot-app/README.md)
- [MCP guide](../spendingsaver-mcp/README.md)

## 13. Acceptance criteria

A release or feature change that claims to satisfy a requirement MUST provide
evidence for the affected project:

1. **Data correctness:** create, edit, categorize, delete, and read back
   representative records; verify relationships and amount sign semantics.
2. **Budget/report correctness:** compare calculations with a small
   hand-computed fixture, including period boundaries and currency handling.
3. **Import/export:** round-trip supported fields, report invalid/skipped rows,
   test duplicate policy, and leave the database recoverable after failure.
4. **Migration/restore:** test the prior schema or backup version, preservation
   of user data, and rollback/atomic failure behavior.
5. **Privacy boundary:** verify that local-only workflows work offline and
   confirm any remote request is expected, documented, and configured.
6. **Accessibility:** verify labels, text scaling, contrast, keyboard or
   assistive-technology navigation for changed UI.
7. **Build and tests:** run the smallest relevant project checks and report
   warnings, missing test coverage, and unavailable platform prerequisites.

The current lack of Android and standalone browser tests means those criteria
are not yet regression-covered for those projects.

## 14. Decisions still required

These questions affect behavior across projects and must be resolved before
implementing a shared product contract:

1. Are Android, browser, and desktop intended to remain separate applications
   or eventually share a backend/account?
2. If data portability is required, what versioned format should all apps
   support, and should import replace, merge, or offer both?
3. Is an account balance an independent reconciled balance or derived from
   transactions? How should transfers and credit liabilities affect net worth?
4. Which currencies and conversion source/date are supported in reports?
5. What are the category-deletion, duplicate-import, and deleted-record
   retention rules?
6. Which optional desktop/backend features require a network, external
   provider, or user account, and what is the opt-in model?
7. What privacy, retention, account-deletion, and release-signing commitments
   are approved for a hosted deployment?
8. Which accessibility, localization, reliability, and performance targets
   should be enforced at release?

Until decided, implementations MUST document their own behavior and MUST NOT
imply a common cross-app answer.

## 15. Change control

Update this specification when an approved product decision changes a
requirement, data boundary, or acceptance criterion. Update the implementation
status only after checking the relevant source and tests. Keep detailed
implementation sequencing in the project roadmap and actionable verified
engineering gaps in the root [`TODO.md`](../TODO.md).
