import { StrictMode, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { DataStore } from './storage/store'
import { StoreProvider, ToastProvider, useSnapshot } from './ui/context'
import App from './ui/App'
import { initPwa } from './pwa/pwa'
import './styles.css'

function Boot() {
  const snap = useSnapshot()
  return snap.ready ? <App /> : <div className="loading" role="status">Loading...</div>
}

function Root() {
  const [store, setStore] = useState<DataStore | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  useEffect(() => {
    DataStore.open().then(setStore).catch((e: unknown) => setFailure(e instanceof Error ? e.message : 'Unknown error'))
  }, [])
  if (failure) return <div className="empty"><h1>SpendingSaver could not start</h1><p>{failure}</p><button type="button" className="btn primary" onClick={() => window.location.reload()}>Try again</button></div>
  if (!store) return <div className="loading" role="status">Loading...</div>
  return (
    <StoreProvider store={store}>
      <ToastProvider><Boot /></ToastProvider>
    </StoreProvider>
  )
}

createRoot(document.getElementById('root')!).render(<StrictMode><Root /></StrictMode>)
initPwa()
