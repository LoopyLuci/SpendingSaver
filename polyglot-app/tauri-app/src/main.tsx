import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'
import { setupGlobalErrorHandlers, errorTracker } from './lib/errorTracker'

// Wire production error capture (Sentry when VITE_SENTRY_DSN is set, local fallback otherwise).
setupGlobalErrorHandlers()
window.addEventListener('beforeunload', () => errorTracker.destroy())

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
