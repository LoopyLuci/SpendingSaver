import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import SimpleFinPanel from './SimpleFinPanel'

const state = { connected: false }
vi.mock('../hooks/useBackend', () => ({
  getSimpleFinStatus: async () => ({ connected: state.connected, last_sync: null, last_result: null, last_error: null, min_interval_s: 3600 }),
  connectSimpleFin: async (t: string) => { if (t === 'bad') throw new Error('SimpleFIN refused the token'); state.connected = true; return {} },
  syncSimpleFin: async () => ({ result: { accounts: 1, added: 4, skipped: 0 }, messages: [] }),
  disconnectSimpleFin: async () => ({ note: 'Also disable it at the bridge.' }),
}))

describe('SimpleFinPanel', () => {
  it('shows a clear error for a refused token, then connects and syncs', async () => {
    render(<SimpleFinPanel />)
    const input = await screen.findByTestId('sf-token')
    fireEvent.change(input, { target: { value: 'bad' } })
    fireEvent.click(screen.getByTestId('sf-connect'))
    expect(await screen.findByRole('alert')).toHaveTextContent('refused')
    fireEvent.change(screen.getByTestId('sf-token'), { target: { value: 'good' } })
    fireEvent.click(screen.getByTestId('sf-connect'))
    fireEvent.click(await screen.findByTestId('sf-sync'))
    await waitFor(() => expect(screen.getByText(/4 new transaction/)).toBeInTheDocument())
  })
})
