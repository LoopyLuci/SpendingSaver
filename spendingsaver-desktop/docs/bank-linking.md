# Bank and account linking: research and proposed design

**Status: step 2 for the US is implemented on the desktop (SimpleFIN, see "Implemented" below). The rest is
proposal.** It records what was researched (web
research, November 2026) and the design recommended for SpendingSaver. Vendor prices and terms below came
from comparison sites that disagree with each other; confirm each on the vendor's own page before
building on it.

## Constraints from the project

- Android and desktop must each stay fully usable alone. Linking is an optional add-on, never a dependency.
- Local-first: financial data stays on the user's devices. No SpendingSaver-operated server in the data path.
- Existing sync (`docs/sync.md`) moves *records*. It must never move bank credentials or aggregator tokens.

## What is available (and what is not)

| Route | Reach | Cost / access | Fit |
|---|---|---|---|
| **File import** (OFX/QFX/CSV downloaded from the bank) | Nearly every bank | Free, no credentials | Baseline. Works everywhere, zero credential risk. |
| **SimpleFIN Bridge** | US (and some CA) | About $15/year paid by the *user* (one source says $1.50/month; another says 50¢ per bank per month; verify). Read-only. | Best fit for US users: user-owned token, no developer contract. |
| **Enable Banking** (restricted production) | EU/UK PSD2 | Free for linking your *own* accounts, per several guides (terms unconfirmed) | Best fit for EU/UK individuals. |
| **GoCardless Bank Account Data** | EU/UK | New sign-ups are disabled (confirmed on GoCardless's own page, since about July 2025) | Not available to new users. |
| **Plaid** | US/CA/EU, widest coverage | Free development tier; production needs a signed agreement and a vetted app; secrets must stay on a server | Needs a hosted backend we would have to run and secure. Defer. |
| **Teller** | US | Public per-connection pricing, free tier (100 vs 1000 connections: sources conflict) | Possible bring-your-own-keys option for self-hosters. Confirm personal use is allowed. |
| **MX, Akoya** | US | Sales-led contracts | Not realistic for an individual-first app. |
| **CFPB Section 1033 (US)** | n/a | Rule finalised Oct 2024 but enjoined by a federal court; the CFPB is rewriting it (a new proposal went to OIRA in Aug 2026) | Do not plan around it. Revisit when final. |
| **Direct PSD2 access** | EU/UK | Requires being a regulated AISP | Not for us. Aggregators hold the licence. |

**Recommendation:** ship in this order, each step optional and independent:

1. **Better import first** (OFX/QFX plus the existing CSV, bank-specific CSV presets, duplicate detection).
   No credentials, usable by everyone today.
2. **User-owned aggregator connections**: SimpleFIN Bridge (US) and Enable Banking (EU/UK). The user signs up
   with the aggregator themself and pastes or scans a token. SpendingSaver acts as the user's own client, so
   we hold no contracts, no secrets of ours, and no one else's data.
3. **Bring-your-own-keys Plaid/Teller** for desktop self-hosters only, if demand exists.
4. **A hosted service** only if the project later wants one-tap linking for the public. That changes the
   privacy model (our servers in the data path), needs a security program, and should be a separate decision.

## Implemented: SimpleFIN on the desktop (US)

Code: `backend/app/simplefin.py` (HTTP API only: the former desktop UI was removed with the Tauri app and the native desktop app does not include bank linking yet),
tests `backend/tests/test_simplefin.py`, migration `c3e8a1d92f54_external_refs`.

- **Use:** subscribe at SimpleFIN Bridge and link your bank there; create a setup token; paste it in
  *Connect Accounts → Link a US bank*; press *Update now*.
- **Credential:** the one-time setup token is exchanged for an access URL, stored only in the encrypted vault
  (`vault_credentials`). It is never returned by the API, logged, synced, or exported. Only `/api/simplefin/*`
  on loopback can use it, and the sync gateway does not expose those routes.
- **Network:** HTTPS to `*.simplefin.org` only, no redirects, 30 s timeout, 20 MB response cap, read-only
  (`/claim` and `/accounts?version=2` only). One update per hour at most (the bridge expects about 24/day).
- **Import:** posted transactions only (pending are skipped, since ids can change on posting). Each item is
  de-duplicated by its bank id through `external_refs`, so repeated updates never duplicate; an item you have
  edited is never overwritten. The bank's balance is applied to the linked account. Each update re-reads the
  last 5 days (window limit 90 days; the first update fetches 90 days).
- **Phone:** imported rows enter the normal sync log, so a paired Android app receives them through the existing
  optional sync. The credential does not travel.
- **Disconnect:** removes the credential and the schedule state here; imported data stays. SimpleFIN has no
  revoke API, so also disable access in your Bridge account.
- **Not yet:** Android-only users (no desktop) cannot link a bank yet; no automatic scheduled updates; no
  matching against transactions you imported earlier by CSV (they could appear twice); no history beyond 90
  days; the vault key still sits in `data/vault.key` beside the database (moving it to the OS credential store
  is a planned hardening); not yet tried against a real SimpleFIN account (tests use a faked bridge).

## Security design (applies to every connector)

1. **Never handle bank passwords.** Only aggregator-issued, scoped, revocable tokens or the aggregator's
   own hosted login pages (OAuth in a Custom Tab/system browser, never an embedded WebView).
2. **Read-only scopes** only. No payment initiation.
3. **Token storage.** Android: encrypted with a key in the Android Keystore, in a file excluded from
   backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`). Desktop: the OS credential
   store (Windows Credential Manager / macOS Keychain / Secret Service), not a plain file. Never in logs, crash
   reports, the JSON backup, the sync log, or MCP tool output.
4. **Tokens do not sync between devices.** Each device connects on its own, or only the desktop connects and
   the phone receives *transactions* through the existing sync. A paired phone must never be able to extract
   the desktop's aggregator token.
5. **Where fetching runs.** Desktop by default (it can run on a schedule and holds the data); Android may fetch
   directly when used alone. Either way the request goes from the user's device to the aggregator, nothing else.
6. **Network hygiene.** Fixed allow-list of aggregator hosts, TLS only, short timeouts, response size caps,
   strict parsing of external JSON (treat all bank data as untrusted input), no following redirects off-host.
7. **Imported data model.** Store the aggregator's transaction id and account id for idempotent re-fetch and
   de-duplication; mark rows as imported; keep the user's edits (category, notes) authoritative on re-sync;
   handle pending → posted and removed transactions; reconcile balances rather than trusting them blindly.
8. **Categorisation stays local.** Rules run on-device. The optional AI toolkit stays off by default and must
   not send transaction data anywhere without explicit consent.
9. **User control.** A clear Disconnect that revokes the token at the aggregator where the API allows and
   deletes the local copy, with the option to keep or remove already imported transactions. A visible "last
   synced" and a per-connection error state.
10. **Consent and compliance.** In-app explanation of what is read and where it goes; a privacy policy; the Play
    Console Data safety form and financial-features declaration must be updated before shipping any of this.
    Because the user holds the aggregator relationship, SpendingSaver is not itself an AISP, but get legal
    review before any hosted option.

## Suggested implementation shape

- A `connectors` module on the desktop backend with one interface (`list_accounts`, `fetch_transactions(since)`,
  `disconnect`), one implementation per provider, and a mapper into the existing `sync_log`-tracked tables so
  linked data reaches Android through the existing optional sync with no protocol change.
- Android mirrors the interface for standalone use, behind the same opt-in switch as other optional features.
- Tests: recorded-fixture tests per connector, de-duplication and pending/posted edge cases, a test that tokens
  never appear in backups, sync payloads or logs.

## Open questions to settle before building

- Which region first (US via SimpleFIN, or EU/UK via Enable Banking)?
- Confirm current SimpleFIN Bridge pricing and per-institution coverage, Enable Banking's personal-use terms,
  and Teller's free-tier terms directly with each vendor.
- Whether a hosted option is ever wanted (it changes privacy, cost and compliance).

## Sources

- [Section 1033 status and timeline](https://www.openbankingtracker.com/guides/section-1033-status)
- [Cozen O'Connor on the enjoined 1033 rule](https://www.cozen.com/news-resources/publications/2026/section-1033-compliance-date-open-banking-rule-enjoined-and-under-reconsideration)
- [SimpleFIN developer guide](https://beta-bridge.simplefin.org/info/developers) and [protocol](https://www.simplefin.org/protocol-v1.html)
- [Actual Budget's SimpleFIN setup](https://actualbudget.org/docs/experimental/simplefin-sync)
- [GoCardless: new sign-ups disabled](https://bankaccountdata.gocardless.com/new-signups-disabled)
- [Free and indie open banking APIs](https://www.openbankingtracker.com/guides/free-open-banking-apis)
- [Best open banking APIs for developers 2026](https://www.openbankingtracker.com/blog/best-open-banking-api-providers-developers-2026)
- [Plaid Link for Android](https://plaid.com/docs/link/android/) and [Plaid security best practices](https://plaid.com/core-exchange/docs/security/)
