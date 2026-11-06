import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { USER, renderAt } from './helpers';
import { useAuthStore } from '../store/authStore';
import { useGameStore } from '../store/gameStore';
import { RoomApi } from '../lib/api';
import RoomLobby from '../pages/RoomLobby';

vi.mock('../lib/api', async (orig) => ({
  ...(await orig()),
  RoomApi: { get: vi.fn(), start: vi.fn(), ready: vi.fn(), leave: vi.fn() },
}));

const room = (extra = {}) => ({
  id: 'r1', code: 'ABC234', name: 'Friends', hostId: 'u1', maxPlayers: 4, status: 'WAITING', matchId: null,
  players: [
    { userId: 'u1', name: 'Asha', avatar: 'avatar-01', ready: false, host: true },
    { userId: 'u2', name: 'Ravi', avatar: 'avatar-02', ready: true, host: false },
    { userId: 'u3', name: 'Sita', avatar: 'avatar-03', ready: false, host: false },
  ],
  ...extra,
});

function setup(r) {
  RoomApi.get.mockResolvedValue(r);
  useGameStore.setState({ watchRoom: () => {}, unwatchRoom: () => {}, room: null, roomEvent: null, connection: 'connected' });
  useAuthStore.setState({ user: USER, status: 'authenticated' });
  return renderAt(<RoomLobby />, { route: '/room/r1', path: '/room/:roomId' });
}

beforeEach(() => { vi.clearAllMocks(); });

describe('RoomLobby: start without a full table', () => {
  it('the host can start now with the 3 players who joined', async () => {
    RoomApi.start.mockResolvedValue(room({ status: 'IN_PROGRESS' }));
    setup(room());
    const btn = await screen.findByTestId('start-now');
    expect(btn).toHaveTextContent('Start now with 3 players');
    await userEvent.click(btn);
    await waitFor(() => expect(RoomApi.start).toHaveBeenCalledWith('r1'));
  });

  it('other players do not get the start button', async () => {
    setup(room({ hostId: 'u2' }));
    await screen.findByText('Friends');
    expect(screen.queryByTestId('start-now')).not.toBeInTheDocument();
  });

  it('a host alone in the room cannot start', async () => {
    setup(room({ players: [room().players[0]] }));
    await screen.findByText('Friends');
    expect(screen.queryByTestId('start-now')).not.toBeInTheDocument();
  });
});
