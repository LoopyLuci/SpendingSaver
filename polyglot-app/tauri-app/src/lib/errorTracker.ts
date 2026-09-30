/**
 * Production error tracking for SpendingSaver.
 * Sentry-compatible error capture with local fallback.
 */
interface ErrorContext {
  component?: string
  action?: string
  userId?: string
  extra?: Record<string, any>
}

interface SentryEvent {
  event_id: string
  timestamp: string
  level: string
  message: string
  stacktrace?: string
  tags: Record<string, string>
  extra: Record<string, any>
  user?: { id: string }
}

class ErrorTracker {
  private enabled: boolean
  private dsn: string | null
  private environment: string
  private release: string
  private queue: SentryEvent[] = []
  private flushTimer: ReturnType<typeof setInterval> | null = null

  constructor() {
    this.dsn = (import.meta as any).env?.VITE_SENTRY_DSN || null
    this.enabled = !!this.dsn || (import.meta as any).env?.PROD
    this.environment = (import.meta as any).env?.MODE || 'development'
    this.release = (import.meta as any).env?.VITE_APP_VERSION || '0.1.0'

    if (this.enabled && this.dsn) {
      this.flushTimer = setInterval(() => this.flush(), 30000)
    }
  }

  captureError(error: Error, context: ErrorContext = {}) {
    if (!this.enabled) return

    const event: SentryEvent = {
      event_id: this.generateId(),
      timestamp: new Date().toISOString(),
      level: 'error',
      message: error.message,
      stacktrace: error.stack,
      tags: {
        environment: this.environment,
        release: this.release,
        component: context.component || 'unknown',
      },
      extra: {
        ...context.extra,
        url: window.location.href,
        userAgent: navigator.userAgent,
      },
      user: context.userId ? { id: context.userId } : undefined,
    }

    console.error(`[ErrorTracker] ${error.message}`, context)

    if (this.dsn) {
      this.queue.push(event)
      if (this.queue.length >= 10) this.flush()
    } else {
      this.storeLocally(event)
    }
  }

  captureMessage(message: string, level: string = 'info') {
    if (!this.enabled) return

    const event: SentryEvent = {
      event_id: this.generateId(),
      timestamp: new Date().toISOString(),
      level,
      message,
      tags: {
        environment: this.environment,
        release: this.release,
      },
      extra: {
        url: window.location.href,
      },
    }

    if (this.dsn) {
      this.queue.push(event)
      if (this.queue.length >= 10) this.flush()
    }
  }

  setUserId(userId: string) {
    this.captureMessage(`User identified: ${userId}`, 'info')
  }

  private async flush() {
    if (this.queue.length === 0 || !this.dsn) return

    const events = [...this.queue]
    this.queue = []

    try {
      const url = new URL(this.dsn)
      const projectId = url.pathname.replace('/', '')
      const apiBase = `${url.protocol}//${url.host}/api/${projectId}/envelope/`

      for (const event of events) {
        const header = JSON.stringify({event_id: event.event_id, sent_at: event.timestamp, dsn: this.dsn})
        const envelope = header + '\n' + JSON.stringify({type: 'event', ...event})

        await fetch(apiBase, {
          method: 'POST',
          headers: { 'Content-Type': 'text/plain' },
          body: envelope,
        })
      }
    } catch {
      events.forEach(e => this.storeLocally(e))
    }
  }

  private storeLocally(event: SentryEvent) {
    try {
      const errors = JSON.parse(localStorage.getItem('ss_errors') || '[]')
      errors.push({
        event_id: event.event_id,
        message: event.message,
        level: event.level,
        timestamp: event.timestamp,
        component: event.tags.component,
      })
      if (errors.length > 100) errors.splice(0, errors.length - 100)
      localStorage.setItem('ss_errors', JSON.stringify(errors))
    } catch {}
  }

  private generateId(): string {
    return 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'.replace(/x/g, () =>
      Math.floor(Math.random() * 16).toString(16)
    )
  }

  getStoredErrors() {
    try {
      return JSON.parse(localStorage.getItem('ss_errors') || '[]')
    } catch {
      return []
    }
  }

  clearStoredErrors() {
    localStorage.removeItem('ss_errors')
  }

  destroy() {
    if (this.flushTimer) clearInterval(this.flushTimer)
    this.flush()
  }
}

export const errorTracker = new ErrorTracker()

export function setupGlobalErrorHandlers() {
  window.addEventListener('error', (event) => {
    errorTracker.captureError(event.error || new Error(event.message), {
      component: 'window.onerror',
      extra: { filename: event.filename, lineno: event.lineno, colno: event.colno },
    })
  })

  window.addEventListener('unhandledrejection', (event) => {
    const error = event.reason instanceof Error ? event.reason : new Error(String(event.reason))
    errorTracker.captureError(error, { component: 'unhandledrejection' })
  })
}
