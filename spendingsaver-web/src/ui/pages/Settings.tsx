import { useEffect, useState } from 'react'
import { useBaseCurrency, useSnapshot, useStore } from '../context'
import { ACCENTS, type ThemeSetting } from '../theme'
import { CURRENCIES } from '../../domain/types'
import { PageHeader, Segmented, ComboField } from '../components'
import { checkForUpdate, getStorageInfo, getSwVersion, isStandalone, promptInstall, requestPersistence, usePwa, type StorageInfo } from '../../pwa/pwa'

const mb = (n: number | null) => (n == null ? 'unknown' : `${(n / 1024 / 1024).toFixed(n > 10 * 1024 * 1024 ? 0 : 1)} MB`)

export default function SettingsPage() {
  const store = useStore()
  const { settings, storageKind, live } = useSnapshot()
  const base = useBaseCurrency()
  const pwa = usePwa()
  const [info, setInfo] = useState<StorageInfo | null>(null)
  const [sw, setSw] = useState<string | null>(null)
  const [checked, setChecked] = useState<string | null>(null)
  const refresh = () => { void getStorageInfo().then(setInfo) }
  useEffect(() => { refresh(); void getSwVersion().then(setSw) }, [pwa.offlineReady])
  const [cur, setCur] = useState(settings.currency)

  return (
    <div>
      <PageHeader title="Settings" />
      <section className="card" aria-labelledby="h-look">
        <h2 id="h-look">Appearance</h2>
        <Segmented<ThemeSetting> label="Theme" value={settings.theme} onChange={(v) => void store.updateSettings({ theme: v })} options={[{ value: 'system', label: 'System' }, { value: 'light', label: 'Light' }, { value: 'dark', label: 'Dark' }]} />
        <div className="field">
          <span className="label" id="acc-label">Accent colour</span>
          <div className="swatches" role="radiogroup" aria-labelledby="acc-label">
            {ACCENTS.map((a) => (
              <button key={a.id} type="button" role="radio" aria-checked={settings.accent === a.id} aria-label={a.label} title={a.label} className={settings.accent === a.id ? 'swatch on' : 'swatch'} onClick={() => void store.updateSettings({ accent: a.id })}>
                <span className="swatch-dot" ref={(el) => { if (el) el.style.background = a.light }} />
              </button>
            ))}
          </div>
        </div>
        <ComboField id="set-cur" label="Currency for totals" value={cur} options={CURRENCIES} onChange={(e) => { setCur(e.target.value); const v = e.target.value.trim().toUpperCase(); if (/^[A-Z0-9]{3,8}$/.test(v)) void store.updateSettings({ currency: v }) }}
          hint={`Dashboard, budgets and the assistant total this currency only (now ${base}). Different currencies are never added together or converted.`} />
      </section>

      <section className="card" aria-labelledby="h-store">
        <h2 id="h-store">Your data and storage</h2>
        <p>All data is stored in this browser's IndexedDB on this device, under this site's address. There is no account and no server, and nothing is sent anywhere. <strong>Clearing site data, using a private window, or removing the browser deletes your data; export a backup from Import / Export.</strong></p>
        <dl className="kv">
          <dt>Storage</dt><dd>{storageKind === 'indexeddb' ? 'IndexedDB (saved)' : 'Memory only (NOT saved)'}</dd>
          <dt>Protected from eviction</dt><dd>{info == null ? '...' : info.persisted === true ? 'Yes (persistent storage granted)' : info.persisted === false ? 'Not granted: the browser may clear it if the device runs low on space' : 'Not supported by this browser'}</dd>
          <dt>Space used</dt><dd>{info ? `${mb(info.usage)} of ${mb(info.quota)} available` : '...'}</dd>
          <dt>Records</dt><dd>{live.accounts.length} accounts, {live.transactions.length} transactions, {live.categories.length} categories</dd>
        </dl>
        {info?.persisted === false && <button type="button" className="btn" onClick={() => { void requestPersistence().then(refresh) }}>Ask the browser to keep my data</button>}
      </section>

      <section className="card" aria-labelledby="h-app">
        <h2 id="h-app">App and updates</h2>
        <dl className="kv">
          <dt>Version</dt><dd>{__APP_VERSION__}{sw ? ` (offline build ${sw})` : ''}</dd>
          <dt>Offline</dt><dd>{pwa.unsupported ?? (pwa.offlineReady ? 'Ready: the app is saved on this device and works without a connection.' : 'Setting up: reload once if this stays unchanged.')}</dd>
          <dt>Installed</dt><dd>{pwa.installed || isStandalone() ? 'Running as an installed app' : 'Running in the browser'}</dd>
        </dl>
        <div className="row gap wrap">
          <button type="button" className="btn" onClick={() => { setChecked('Checking...'); checkForUpdate().then(() => setChecked(pwa.updateReady ? 'A new version is ready: use Reload at the top of the page.' : 'You have the latest version.')).catch(() => setChecked('Could not check (are you offline?).')) }}>Check for updates</button>
          {pwa.canInstall && <button type="button" className="btn primary" onClick={() => void promptInstall()}>Install app</button>}
        </div>
        {checked && <p role="status" className="muted small">{checked}</p>}
        {!pwa.installed && !isStandalone() && pwa.ios && <p><strong>Install on iPhone or iPad:</strong> open this page in Safari, tap the Share button, choose <strong>Add to Home Screen</strong>, then tap Add.</p>}
        {!pwa.installed && !isStandalone() && !pwa.ios && !pwa.canInstall && <p className="muted small">To install, use your browser's menu (Install app or Add to Home Screen). Some browsers only offer this on a secure (HTTPS) address.</p>}
      </section>

      <section className="card" aria-labelledby="h-about">
        <h2 id="h-about">About</h2>
        <p>SpendingSaver for the browser. No tracking, no analytics, no third-party requests. Sync with the desktop or Android app is not available yet; use JSON backups to move data between apps.</p>
      </section>
    </div>
  )
}
