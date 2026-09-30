export async function checkPythonBackend(baseUrl: string): Promise<'online' | 'offline'> {
  try {
    const res = await fetch(`${baseUrl}/health`, { signal: AbortSignal.timeout(3000) })
    if (res.ok) return 'online'
    return 'offline'
  } catch {
    return 'offline'
  }
}

export async function checkGoService(baseUrl: string): Promise<'online' | 'offline'> {
  try {
    const res = await fetch(`${baseUrl}/health`, { signal: AbortSignal.timeout(3000) })
    if (res.ok) return 'online'
    return 'offline'
  } catch {
    return 'offline'
  }
}
