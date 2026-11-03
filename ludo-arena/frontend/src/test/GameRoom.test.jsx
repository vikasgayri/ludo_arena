import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { USER } from './helpers';
import { useAuthStore } from '../store/authStore';
import { __setSocketForTests, useGameStore } from '../store/gameStore';
import GameRoom from '../pages/GameRoom';

vi.mock('../lib/api', async (orig) => ({
  ...(await orig()),
  RoomApi: { leave: vi.fn() },
  WalletApi: { get: vi.fn().mockResolvedValue({ coinBalance: 1, dailyBonusAvailable: false, dailyBonusCoins: 0 }) },
  UserApi: { me: vi.fn().mockResolvedValue({}) },
}));

const players = [
  { userId: 'u1', name: 'Asha', avatar: 'avatar-01', color: 'RED', seat: 0, tokens: [-1, 3, 10, -1], connected: true, forfeited: false, finishedTokens: 0, rank: 0 },
  { userId: 'u2', name: 'Ravi', avatar: 'avatar-02', color: 'YELLOW', seat: 1, tokens: [-1, -1, -1, -1], connected: true, forfeited: false, finishedTokens: 0, rank: 0 },
];
const match = (extra = {}) => ({
  matchId: 'm1', roomId: 'r1', status: 'ACTIVE', mode: 2, players, currentUserId: 'u1', diceValue: null, lastDiceValue: null, lastDiceUserId: null,
  awaitingMove: false, validMoves: [], consecutiveSixes: 0, winnerId: null, turnDeadline: Date.now() + 25000, serverTime: Date.now(), seq: 1, ...extra,
});

function setup(m, published = []) {
  const sock = { publish: (dest, body) => { published.push({ dest, body }); return true; }, subscribe: vi.fn(), unsubscribe: vi.fn(), disconnect: vi.fn() };
  __setSocketForTests(sock);
  useGameStore.setState({ connect: () => {}, watchMatch: (id) => useGameStore.setState({ matchId: id, match: m }), unwatchMatch: () => {}, requestState: () => {}, connection: 'connected', events: [], pending: false, error: null });
  useAuthStore.setState({ user: USER, status: 'authenticated' });
  return render(
    <MemoryRouter initialEntries={['/game/m1']}>
      <Routes><Route path="/game/:matchId" element={<GameRoom />} /><Route path="*" element={<div>elsewhere</div>} /></Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => { vi.clearAllMocks(); });

describe('GameRoom', () => {
  it('lets the current player roll, sending a rollDice action to the server', async () => {
    const published = [];
    setup(match(), published);
    expect(screen.getByTestId('turn-label')).toHaveTextContent('Your turn');
    await userEvent.click(screen.getByRole('button', { name: /roll dice/i }));
    expect(published).toHaveLength(1);
    expect(published[0].dest).toBe('/app/match.rollDice');
    expect(published[0].body.matchId).toBe('m1');
  });

  it("disables the dice when it is the opponent's turn", () => {
    setup(match({ currentUserId: 'u2' }));
    expect(screen.getByTestId('turn-label')).toHaveTextContent("Ravi's turn");
    expect(screen.queryByRole('button', { name: /roll dice/i })).not.toBeInTheDocument();
    expect(screen.getByTestId('dice')).toHaveAttribute('data-value', '');
  });

  it('highlights exactly the server validMoves and sends the chosen token index', async () => {
    const published = [];
    setup(match({ diceValue: 4, awaitingMove: true, validMoves: [1, 2] }), published);
    expect(screen.getByTestId('token-RED-1')).toHaveAttribute('data-movable', 'true');
    expect(screen.getByTestId('token-RED-2')).toHaveAttribute('data-movable', 'true');
    expect(screen.getByTestId('token-RED-0')).toHaveAttribute('data-movable', 'false');
    expect(screen.getByTestId('token-YELLOW-1')).toHaveAttribute('data-movable', 'false');
    expect(screen.getByTestId('dice')).toHaveAttribute('data-value', '4');
    await userEvent.click(screen.getByTestId('token-RED-2'));
    expect(published[0].dest).toBe('/app/match.moveToken');
    expect(published[0].body.tokenIndex).toBe(2);
  });

  it('syncs when the server pushes a new state (opponent moved, turn passes to me)', () => {
    setup(match({ currentUserId: 'u2' }));
    expect(screen.getByTestId('turn-label')).toHaveTextContent("Ravi's turn");
    act(() => {
      useGameStore.getState().applyState(match({ seq: 2, currentUserId: 'u1' }), [
        { eventId: 'e1', seq: 2, type: 'TURN_CHANGED', userId: 'u2', data: { nextUserId: 'u1', extraTurn: false }, at: 1 },
      ]);
    });
    expect(screen.getByTestId('turn-label')).toHaveTextContent('Your turn');
    expect(screen.getByTestId('game-log')).toHaveTextContent('Your turn');
  });

  it('shows the result dialog with the winner when the match finishes', () => {
    setup(match({ status: 'FINISHED', winnerId: 'u2', currentUserId: null, players: players.map((p) => (p.userId === 'u2' ? { ...p, rank: 1, finishedTokens: 4 } : { ...p, rank: 2 })) }));
    expect(screen.getByRole('dialog')).toHaveTextContent(/Ravi took the win/);
  });

  it('warns the player when the connection drops', () => {
    setup(match());
    act(() => useGameStore.setState({ connection: 'reconnecting' }));
    expect(screen.getAllByText(/reconnecting/i).length).toBeGreaterThan(0);
  });
});
