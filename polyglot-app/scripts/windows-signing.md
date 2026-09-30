# Windows code signing notes

1. Enroll a code-signing cert: self-signed for dev (`New-SelfSignedCertificate`), or EV/OV from a CA for distribution.
2. Install the cert into the current user's Personal store.
3. Set the thumbprint in `src-tauri/tauri.conf.json` -> `bundle.windows.certificateThumbprint`.
4. Build signed release: `npm run tauri build`.
5. Verify: `scripts/verify_release.py` checks artifacts; manually run `signtool verify /pa <exe>`.

Dev test example:
- `New-SelfSignedCertificate -Type CodeSigningCert -Subject "CN=SpendingSaver Dev" -CertStoreLocation Cert:\CurrentUser\My`
- Copy the cert thumbprint into tauri.conf.json.
