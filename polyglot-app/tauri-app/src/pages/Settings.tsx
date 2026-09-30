import { useSettings, ACCENTS } from '../hooks/useSettings'

export default function Settings() {
  const { settings, setSettings } = useSettings()

  return (
    <div className="space-y-6">
      <div>
        <h3 className="text-xl font-bold text-gray-100">Settings</h3>
        <p className="text-sm text-gray-500">Personalize the app's look and behavior.</p>
      </div>

      {/* Appearance */}
      <section className="bg-white dark:bg-gray-800 rounded-2xl shadow p-6 space-y-5">
        <h4 className="text-lg font-semibold">Appearance</h4>

        <div>
          <label className="block text-sm font-medium mb-2">Theme</label>
          <div className="flex gap-2">
            {(['system', 'light', 'dark'] as const).map((t) => (
              <button
                key={t}
                onClick={() => setSettings({ ...settings, theme: t })}
                className={`px-4 py-2 rounded-lg capitalize ${
                  settings.theme === t
                    ? 'btn-accent text-white'
                    : 'bg-gray-700 text-gray-200'
                }`}
              >
                {t}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium mb-2">Accent color</label>
          <div className="flex flex-wrap gap-3">
            {ACCENTS.map((a) => (
              <button
                key={a}
                aria-label={a}
                onClick={() => setSettings({ ...settings, accent: a })}
                className={`w-8 h-8 rounded-full transition ${
                  settings.accent === a ? 'ring-2 ring-offset-2 ring-offset-gray-800 ring-blue-400' : ''
                }`}
                style={{ background: `var(--accent-${a})` }}
                onMouseEnter={(e) => (e.currentTarget.style.background = `var(--accent-${a})`)}
              />
            ))}
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium mb-2">
            Text size ({Math.round(settings.textScale * 100)}%)
          </label>
          <input
            type="range"
            min={0.85}
            max={1.3}
            step={0.05}
            value={settings.textScale}
            onChange={(e) => setSettings({ ...settings, textScale: parseFloat(e.target.value) })}
            className="w-full accent-blue-600"
          />
        </div>
      </section>

      {/* Privacy */}
      <section className="bg-white dark:bg-gray-800 rounded-2xl shadow p-6 space-y-4">
        <h4 className="text-lg font-semibold">Privacy & display</h4>
        <label className="flex items-center justify-between">
          <div>
            <span className="block">Show balances</span>
            <span className="text-xs text-gray-500">Hide amounts for privacy when off</span>
          </div>
          <input
            type="checkbox"
            checked={settings.showBalance}
            onChange={(e) => setSettings({ ...settings, showBalance: e.target.checked })}
            className="w-5 h-5"
          />
        </label>
        <label className="flex items-center justify-between">
          <div>
            <span className="block">Compact mode</span>
            <span className="text-xs text-gray-500">Denser rows in lists</span>
          </div>
          <input
            type="checkbox"
            checked={settings.compact}
            onChange={(e) => setSettings({ ...settings, compact: e.target.checked })}
            className="w-5 h-5"
          />
        </label>
      </section>

      {/* Regional */}
      <section className="bg-white dark:bg-gray-800 rounded-2xl shadow p-6 space-y-4">
        <h4 className="text-lg font-semibold">Currency & dates</h4>
        <div className="flex flex-wrap gap-2">
          {['USD', 'EUR', 'GBP', 'CAD', 'AUD', 'JPY', 'INR', 'BRL', 'CHF', 'SEK'].map((c) => (
            <button
              key={c}
              onClick={() => setSettings({ ...settings, currency: c })}
              className={`px-3 py-1.5 rounded-lg ${settings.currency === c ? 'btn-accent text-white' : 'bg-gray-700 text-gray-200'}`}
            >
              {c}
            </button>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          {['MM/dd/yyyy', 'dd/MM/yyyy', 'yyyy-MM-dd'].map((d) => (
            <button
              key={d}
              onClick={() => setSettings({ ...settings, dateFormat: d })}
              className={`px-3 py-1.5 rounded-lg ${settings.dateFormat === d ? 'btn-accent text-white' : 'bg-gray-700 text-gray-200'}`}
            >
              {d}
            </button>
          ))}
        </div>
      </section>

      {/* Backend + data */}
      <section className="bg-white dark:bg-gray-800 rounded-2xl shadow p-6 space-y-4">
        <h4 className="text-lg font-semibold">Backend & data</h4>
        <label className="flex items-center justify-between">
          <div>
            <span className="block">Auto-start backend services</span>
            <span className="text-xs text-gray-500">Launch API + connectors on startup</span>
          </div>
          <input
            type="checkbox"
            checked={settings.autoStartBackends}
            onChange={(e) => setSettings({ ...settings, autoStartBackends: e.target.checked })}
            className="w-5 h-5"
          />
        </label>
        <label className="block">
          <span className="block text-sm font-medium mb-1">API Base URL</span>
          <input
            value={settings.apiBase}
            onChange={(e) => setSettings({ ...settings, apiBase: e.target.value })}
            className="border rounded p-2 dark:bg-gray-700 dark:border-gray-600 w-full"
          />
        </label>
        <label className="flex items-center justify-between">
          <div>
            <span className="block">Telemetry</span>
            <span className="text-xs text-gray-500">Opt-in anonymous usage reporting</span>
          </div>
          <input
            type="checkbox"
            checked={settings.telemetry}
            onChange={(e) => setSettings({ ...settings, telemetry: e.target.checked })}
            className="w-5 h-5"
          />
        </label>
      </section>
    </div>
  )
}