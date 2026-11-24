# Security policy

SpendingSaver handles personal financial data, so security reports are welcome.

## Reporting a vulnerability

Please **do not open a public issue** for a security problem. Use GitHub's private reporting:
**Security → Report a vulnerability** on this repository. Include what you found, how to reproduce it, and the
version or commit. You should get a first reply within a week.

## Scope and design notes

- The Android app and the native desktop app are local-first: data stays on the device unless you export it or turn
  on sync. Sync is opt-in and described in [`polyglot-app/docs/sync.md`](polyglot-app/docs/sync.md).
- The sync hub only accepts requests that carry a per-device token obtained with the pairing key. Pairing attempts are
  rate limited. Use it on networks you trust; plain HTTP is for local networks only.
- The MCP adapter can change financial records. Run it only against a backend you trust.
- Do not commit credentials, pairing keys, bank tokens or real financial data. Bank-linking credentials are stored
  only in the backend's encrypted vault.
- Tax figures are planning estimates, not tax advice.
