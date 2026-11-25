import { useState } from 'react'
import { useSnapshot, useStore, useToast } from '../context'
import { MAX_BACKUP_BYTES, InvalidBackupError, parseBackup, type ParsedBackup } from '../../domain/backup'
import { MAX_CSV_BYTES, exportCsv, type CsvImportPlan } from '../../domain/csv'
import { applyCsvImport, loadSampleData, makeBackupFile, prepareCsvImport, restoreBackup } from '../../storage/actions'
import { todayYmd } from '../../lib/dates'
import { ConfirmDialog, ErrorList, Field, PageHeader, SelectField } from '../components'
import { downloadText } from '../download'

export default function DataPage() {
  const store = useStore()
  const toast = useToast()
  const { live, settings } = useSnapshot()
  const [restore, setRestore] = useState<ParsedBackup | null>(null)
  const [restoreError, setRestoreError] = useState<string | null>(null)
  const [csvAccount, setCsvAccount] = useState('')
  const [csvText, setCsvText] = useState<string | null>(null)
  const [csvName, setCsvName] = useState('')
  const [csvError, setCsvError] = useState<string | null>(null)
  const [wipe, setWipe] = useState(false)
  const accountId = csvAccount || live.accounts[0]?.id || ''
  const plan: CsvImportPlan | null = csvText !== null && accountId ? prepareCsvImport(store, csvText, accountId) : null

  async function exportBackup() {
    const f = makeBackupFile(store)
    downloadText(f.name, f.text, 'application/json')
    await store.updateSettings({ lastBackupAt: Date.now() })
    toast.show('Backup saved to your downloads. Keep it somewhere safe.')
  }

  async function pickBackup(file: File | undefined) {
    setRestoreError(null)
    if (!file) return
    if (file.size > MAX_BACKUP_BYTES) return setRestoreError('That file is too large to be a SpendingSaver backup.')
    try { setRestore(parseBackup(await file.text())) } catch (e) { setRestoreError(e instanceof InvalidBackupError ? e.message : 'Could not read that file.') }
  }

  async function pickCsv(file: File | undefined) {
    setCsvError(null); setCsvText(null)
    if (!file) return
    if (file.size > MAX_CSV_BYTES) return setCsvError('That file is too large (limit 5 MB).')
    try { setCsvText(await file.text()); setCsvName(file.name) } catch { setCsvError('Could not read that file.') }
  }

  return (
    <div>
      <PageHeader title="Import, export and backup" />
      <div className="banner warn" role="note"><span><strong>Your data lives only in this browser.</strong> Clearing site data, using a private window, or uninstalling the browser deletes it. Export a backup regularly and keep the file somewhere safe.</span></div>

      <section className="card" aria-labelledby="h-bk">
        <h2 id="h-bk">JSON backup</h2>
        <p>The same file format the Android and desktop apps use, so you can move accounts, categories, transactions and budgets between them. Recurring items, goals, debts and settings are not included. This is a file you save and load yourself; it is not sync.</p>
        <p className="muted small">{settings.lastBackupAt ? `Last backup from this browser: ${new Date(settings.lastBackupAt).toLocaleString()}.` : 'No backup made from this browser yet.'}</p>
        <div className="row gap wrap">
          <button type="button" className="btn primary" onClick={() => void exportBackup()}>Export backup</button>
        </div>
        <Field label="Restore from a backup file" htmlFor="bk-file" hint="Replaces your current accounts, categories, transactions and budgets with the file's contents. Export a backup first if you want to keep what is here.">
          <input id="bk-file" type="file" accept=".json,application/json" onChange={(e) => { void pickBackup(e.target.files?.[0]); e.target.value = '' }} />
        </Field>
        {restoreError && <p className="error" role="alert">{restoreError}</p>}
      </section>

      <section className="card" aria-labelledby="h-csv">
        <h2 id="h-csv">CSV</h2>
        <p>Import needs a header row with <code>Date</code>, <code>Description</code> and <code>Amount</code>; <code>Account</code> and <code>Category</code> columns are optional. Negative amounts are expenses. Rows with an empty category are categorized from the description.</p>
        <div className="row gap wrap"><button type="button" className="btn" disabled={live.transactions.length === 0} onClick={() => { downloadText(`spendingsaver-transactions-${todayYmd()}.csv`, exportCsv(live.transactions, live.accounts, live.categories), 'text/csv') }}>Export transactions (CSV)</button></div>
        <hr />
        {live.accounts.length === 0 ? <p className="muted">Add an account to import transactions into.</p> : (
          <>
            <SelectField id="csv-acct" label="Import into account" value={accountId} onChange={setCsvAccount} hint="Used for rows with no matching Account column value.">{live.accounts.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}</SelectField>
            <Field label="CSV file" htmlFor="csv-file"><input id="csv-file" type="file" accept=".csv,text/csv" onChange={(e) => { void pickCsv(e.target.files?.[0]); e.target.value = '' }} /></Field>
          </>
        )}
        {csvError && <p className="error" role="alert">{csvError}</p>}
        {plan && (
          <div className="stack" aria-live="polite">
            {plan.fatal ? <p className="error" role="alert">{plan.fatal}</p> : (
              <>
                <p><strong>{csvName}</strong>: {plan.transactions.length} row{plan.transactions.length === 1 ? '' : 's'} ready to import{plan.newCategories.length ? `, ${plan.newCategories.length} new categor${plan.newCategories.length === 1 ? 'y' : 'ies'}` : ''}{plan.skipped.length ? `, ${plan.skipped.length} skipped` : ''}.</p>
                {plan.skipped.length > 0 && <details><summary>Why rows were skipped</summary><ErrorList errors={plan.skipped.slice(0, 20).map((s) => `Line ${s.line}: ${s.reason}`)} />{plan.skipped.length > 20 && <p className="muted small">and {plan.skipped.length - 20} more.</p>}</details>}
                <div className="row gap"><button type="button" className="btn primary" disabled={plan.transactions.length === 0} onClick={async () => { await applyCsvImport(store, plan); toast.show(`Imported ${plan.transactions.length} transaction${plan.transactions.length === 1 ? '' : 's'}.`); setCsvText(null) }}>Import {plan.transactions.length} rows</button><button type="button" className="btn" onClick={() => setCsvText(null)}>Cancel</button></div>
              </>
            )}
          </div>
        )}
      </section>

      <section className="card" aria-labelledby="h-del">
        <h2 id="h-del">Sample data and reset</h2>
        <div className="row gap wrap">
          <button type="button" className="btn" onClick={() => void loadSampleData(store).then(() => toast.show('Sample data added.'))}>Add sample data</button>
          <button type="button" className="btn danger" onClick={() => setWipe(true)}>Delete all data</button>
        </div>
        <p className="muted small">Delete all data erases everything stored by SpendingSaver in this browser (accounts, transactions, budgets, recurring items, goals, debts). It cannot be undone, so export a backup first.</p>
      </section>

      {restore && (
        <ConfirmDialog title="Restore this backup?" confirmLabel="Replace my data" onCancel={() => setRestore(null)}
          body={<><p>This file has {restore.data.accounts.length} account{restore.data.accounts.length === 1 ? '' : 's'}, {restore.data.categories.length} categor{restore.data.categories.length === 1 ? 'y' : 'ies'}, {restore.data.transactions.length} transaction{restore.data.transactions.length === 1 ? '' : 's'} and {restore.data.budgets.length} budget{restore.data.budgets.length === 1 ? '' : 's'}{restore.exportedAt ? `, saved ${new Date(restore.exportedAt).toLocaleDateString()}` : ''}.</p>
            <p>Your current accounts, categories, transactions and budgets will be replaced. Things not in the file are removed from view. Export a backup first if you might want them.</p>
            {restore.danglingRefs > 0 && <p className="muted small">{restore.danglingRefs} transaction(s) refer to an account or category that is not in the file.</p>}</>}
          onConfirm={async () => { await restoreBackup(store, restore); setRestore(null); toast.show('Backup restored.') }} />
      )}
      {wipe && (
        <ConfirmDialog title="Delete all data?" confirmLabel="Delete everything" onCancel={() => setWipe(false)}
          body={<p>This permanently erases all SpendingSaver data in this browser. It cannot be undone. Have you exported a backup?</p>}
          onConfirm={async () => { await store.wipeAll(); setWipe(false); toast.show('All data deleted.') }} />
      )}
    </div>
  )
}
