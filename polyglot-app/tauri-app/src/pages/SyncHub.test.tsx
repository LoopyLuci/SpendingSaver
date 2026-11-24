import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import SyncHub from './SyncHub'

const revoke = vi.fn(async (_id: string) => ({ revoked: 'd1' }))

vi.mock('../hooks/useBackend', async () => {
  const actual = await vi.importActual<any>('../hooks/useBackend')
  return {
    ...actual,
    getHubInfo: async () => ({ name: 'pc', lan_ip: '192.168.1.20', port: 8125, key: 'secret-key' }),
    rotateHubKey: async () => ({ key: 'new' }),
    listSyncDevices: async () => [
      { id: 'd1', name: 'Nokia 7.2', last_seen: null, revoked: false },
      { id: 'd2', name: 'Old phone', revoked: true },
    ],
    revokeSyncDevice: (id: string) => revoke(id),
    getLinkStatus: async () => ({ available: true, enabled: true, running: true, relays: ['r.example:5901'], name: 'pc', node_id: 'abc123', error: null, peers: [] }),
    createLinkInvite: async () => ({ invite: 'INVITE-TEXT', node_id: 'abc123' }),
    enableLink: async () => ({}),
    disableLink: async () => ({}),
  }
})

describe('SyncHub page', () => {
  beforeEach(() => { revoke.mockClear(); vi.spyOn(window, 'confirm').mockReturnValue(true) })

  it('shows the address, hides the key until asked, and lists devices', async () => {
    render(<SyncHub />)
    expect((await screen.findByTestId('hub-address')).textContent).toBe('192.168.1.20:8125')
    expect(screen.getByTestId('hub-key').textContent).not.toContain('secret-key')
    fireEvent.click(screen.getByText('Show'))
    expect(screen.getByTestId('hub-key').textContent).toBe('secret-key')
    expect(await screen.findByText(/Nokia 7\.2/)).toBeTruthy()
    expect(screen.getByText(/revoked/)).toBeTruthy()
  })

  it('revokes a paired phone after confirmation', async () => {
    render(<SyncHub />)
    fireEvent.click(await screen.findByText('Revoke'))
    await waitFor(() => expect(revoke).toHaveBeenCalledWith('d1'))
  })

  it('shows the link and creates a phone invite', async () => {
    render(<SyncHub />)
    expect((await screen.findByTestId('link-node')).textContent).toBe('abc123')
    fireEvent.click(screen.getByText('New phone invite'))
    expect(((await screen.findByTestId('link-invite')) as HTMLTextAreaElement).value).toBe('INVITE-TEXT')
  })
})
