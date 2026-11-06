import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { USER } from './helpers';
import { RoomApi } from '../lib/api';
import { DICE_ROLL_MS, TURN_BANNER_MS } from '../lib/gameFx';
import { useAuthStore } from '../store/authStore';
import { __setSocketForTests, useGameStore } from '../store/gameStore';
import GameRoom from '../pages/GameRoom';

vi.mock('../lib/api', async (orig) => ({
  ...(await orig()),
  RoomApi: { leave: vi.fn() },
  WalletApi: { get: vi.fn().mockResolvedValue({ coinBalance: 1, dailyBonusAvailable: false, dailyBonusCoins: 0 }) },
  // never resolves: a finished-match test must not overwrite the logged-in user of the next test
  UserApi: { me: vi.fn(() => new Promise(() => {})) },
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
  useGameStore.setState({ connect: () => {}, watchMatch: (id) => useGameStore.setState({ matchId: id, match: m }), unwatchMatch: () => {}, requestState: () => {}, connection: 'connected', events: [], lastUpdateEvents: [], chat: [], pending: false, error: null });
  useAuthStore.setState({ user: USER, status: 'authenticated' });
  return render(
    <MemoryRouter initialEntries={['/game/m1']}>
      <Routes><Route path="/game/:matchId" element={<GameRoom />} /><Route path="*" element={<div>elsewhere</div>} /></Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => { vi.clearAllMocks(); });

function cleanupAll() { cleanup(); }
/** Render as whoever is logged in now (no user reset). */
function setupAs(m) {
  useGameStore.setState({ connect: () => {}, watchMatch: (id) => useGameStore.setState({ matchId: id, match: m }), unwatchMatch: () => {}, requestState: () => {}, connection: 'connected', events: [], lastUpdateEvents: [], chat: [], pending: false, error: null });
  return render(
    <MemoryRouter initialEntries={['/game/m1']}>
      <Routes><Route path="/game/:matchId" element={<GameRoom />} /><Route path="*" element={<div>elsewhere</div>} /></Routes>
    </MemoryRouter>,
  );
}

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

  it('shows a computer opponent as thinking during its turn and hides the turn timer', () => {
    const botPlayers = [players[0], { ...players[1], userId: 'BOT:r1:1', name: 'Master Bot', bot: true, botDifficulty: 'HARD' }];
    setup(match({ players: botPlayers, currentUserId: 'BOT:r1:1', vsComputer: true, botDifficulty: 'HARD' }));
    expect(screen.getByTestId('turn-label')).toHaveTextContent('Master Bot is thinking…');
    expect(screen.getByTestId('bot-badge')).toHaveTextContent('Computer · Hard');
    expect(screen.getByTestId('bot-thinking')).toBeInTheDocument();
    expect(screen.queryByTestId('turn-timer')).toBeNull();
    expect(screen.queryByRole('button', { name: /roll dice/i })).not.toBeInTheDocument();
  });

  it('explains an abandoned practice match in the result dialog', () => {
    const botPlayers = [{ ...players[0], forfeited: true }, { ...players[1], userId: 'BOT:r1:1', name: 'Master Bot', bot: true, botDifficulty: 'HARD' }];
    setup(match({ players: botPlayers, status: 'ABANDONED', winnerId: null, currentUserId: null, vsComputer: true, botDifficulty: 'HARD' }));
    expect(screen.getByTestId('turn-label')).toHaveTextContent('Match abandoned');
    expect(screen.getByRole('dialog')).toHaveTextContent('Match abandoned');
    expect(screen.getByTestId('practice-note')).toBeInTheDocument();
  });

  it('moves automatically when there is only one legal token', async () => {
    const published = [];
    setup(match({ diceValue: 3, awaitingMove: true, validMoves: [2] }), published);
    expect(screen.getByTestId('move-hint')).toHaveTextContent(/moving automatically/i);
    await waitFor(() => expect(published).toHaveLength(1), { timeout: 2000 });
    expect(published[0].dest).toBe('/app/match.moveToken');
    expect(published[0].body.tokenIndex).toBe(2);
  });

  it('moves automatically when all legal tokens stand on the same spot (e.g. leaving base on a six)', async () => {
    const published = [];
    const inBase = [{ ...players[0], tokens: [-1, -1, -1, -1] }, players[1]];
    setup(match({ players: inBase, diceValue: 6, awaitingMove: true, validMoves: [0, 1, 2, 3] }), published);
    await waitFor(() => expect(published).toHaveLength(1), { timeout: 2000 });
    expect(published[0].body.tokenIndex).toBe(0);
  });

  it('waits for the player when there is a real choice', async () => {
    const published = [];
    setup(match({ diceValue: 4, awaitingMove: true, validMoves: [1, 2] }), published);
    expect(screen.getByTestId('move-hint')).toHaveTextContent(/pick a highlighted token/i);
    await new Promise((r) => setTimeout(r, 700));
    expect(published).toHaveLength(0);
  });

  it('draws a soft halo (not a spinning dashed ring) around movable tokens', () => {
    setup(match({ diceValue: 4, awaitingMove: true, validMoves: [1, 2] }));
    const token = screen.getByTestId('token-RED-1');
    expect(token.querySelector('.token-halo')).not.toBeNull();
    expect(token.querySelector('.token-ring')).toBeNull();
    expect(token.querySelector('[stroke-dasharray]')).toBeNull();
  });

  it("ringed highlight on all four tokens of the player whose turn it is, and only theirs", () => {
    setup(match({ currentUserId: 'u2' }));
    [0, 1, 2, 3].forEach((i) => {
      expect(screen.getByTestId(`token-YELLOW-${i}`)).toHaveAttribute('data-active', 'true');
      expect(screen.getByTestId(`token-YELLOW-${i}`).querySelector('.token-turn-ring')).not.toBeNull();
      expect(screen.getByTestId(`token-RED-${i}`)).toHaveAttribute('data-active', 'false');
    });
  });

  it('a tapped token starts moving at once, before the server answers', async () => {
    const published = [];
    setup(match({ diceValue: 4, awaitingMove: true, validMoves: [1, 2] }), published);
    const token = screen.getByTestId('token-RED-2');
    const before = token.style.transform;
    await userEvent.click(token);
    expect(published).toHaveLength(1);
    expect(token.style.transform).not.toBe(before); // first step drawn without waiting for the round trip
    expect(screen.getByTestId('token-RED-1')).toHaveAttribute('data-movable', 'false'); // no double move
  });

  it('a move the server rejects puts the token back', async () => {
    setup(match({ diceValue: 4, awaitingMove: true, validMoves: [1, 2] }));
    const token = screen.getByTestId('token-RED-2');
    const before = token.style.transform;
    await userEvent.click(token);
    expect(token.style.transform).not.toBe(before);
    act(() => { useGameStore.setState({ pending: false, error: 'Token cannot be moved' }); });
    await waitFor(() => expect(token.style.transform).toBe(before));
  });

  it('online matches have a chat that sends through the socket', async () => {
    const published = [];
    setup(match({ currentUserId: 'u2' }), published);
    act(() => { useGameStore.setState({ chat: [{ id: 'c1', matchId: 'm1', userId: 'u2', name: 'Ravi', color: 'YELLOW', text: 'Good luck!', at: '2026-11-05T10:00:00Z' }] }); });
    const panel = screen.getAllByTestId('chat-panel')[0];
    expect(panel).toHaveTextContent('Good luck!');
    const input = panel.querySelector('input');
    await userEvent.type(input, 'Thanks{enter}');
    const chatFrames = published.filter((p) => p.dest === '/app/match.chat');
    expect(chatFrames).toHaveLength(1);
    expect(chatFrames[0].body).toEqual({ matchId: 'm1', text: 'Thanks' });
    expect(input).toHaveValue('');
  });

  it('there is no chat in a practice match against the computer', () => {
    const botPlayers = [players[0], { ...players[1], userId: 'BOT:r1:1', name: 'Master Bot', bot: true, botDifficulty: 'HARD' }];
    setup(match({ players: botPlayers, currentUserId: 'BOT:r1:1', vsComputer: true }));
    expect(screen.queryByTestId('chat-panel')).toBeNull();
    expect(screen.queryByTestId('chat-fab')).toBeNull();
  });

  it('the mobile chat button counts unread messages from others', () => {
    setup(match({ currentUserId: 'u2' }));
    act(() => { useGameStore.setState({ chat: [
      { id: 'c1', userId: 'u2', name: 'Ravi', color: 'YELLOW', text: 'hi', at: '2026-11-05T10:00:00Z' },
      { id: 'c2', userId: 'u1', name: 'Asha', color: 'RED', text: 'hello', at: '2026-11-05T10:00:01Z' },
      { id: 'c3', userId: 'u2', name: 'Ravi', color: 'YELLOW', text: 'ready?', at: '2026-11-05T10:00:02Z' },
    ] }); });
    expect(screen.getByTestId('chat-unread')).toHaveTextContent('2');
  });

  describe('turn, dice and menu UX', () => {
    const four = [
      players[0],
      { ...players[1], color: 'GREEN', seat: 1 },
      { userId: 'u3', name: 'Sita', avatar: 'avatar-03', color: 'YELLOW', seat: 2, tokens: [-1, -1, -1, -1], connected: true, forfeited: false, finishedTokens: 0, rank: 0 },
      { userId: 'u4', name: 'Kabir', avatar: 'avatar-04', color: 'BLUE', seat: 3, tokens: [-1, -1, -1, -1], connected: true, forfeited: false, finishedTokens: 0, rank: 0 },
    ];
    const turnTo = (m, extra) => act(() => { useGameStore.getState().applyState({ ...m, ...extra }, []); });

    it('the dice sits at the home of whoever is to move, for every colour (4 players)', () => {
      const base = match({ mode: 4, players: four });
      setup(base);
      const dice = screen.getByTestId('stage-dice');
      ['u1', 'u2', 'u3', 'u4'].forEach((uid, i) => {
        turnTo(base, { seq: 10 + i, currentUserId: uid });
        const color = four[i].color;
        expect(dice).toHaveAttribute('data-dock', color);
        expect(screen.getByTestId(`pod-${color}`)).toHaveAttribute('data-active', 'true');
        four.filter((p) => p.color !== color).forEach((p) => expect(screen.getByTestId(`pod-${p.color}`)).toHaveAttribute('data-active', 'false'));
      });
    });

    it('2 players: RED and YELLOW panels sit at opposite corners', () => {
      setup(match());
      expect(screen.getByTestId('pod-RED')).toBeInTheDocument();
      expect(screen.getByTestId('pod-YELLOW')).toBeInTheDocument();
      expect(screen.queryByTestId('pod-GREEN')).not.toBeInTheDocument();
      expect(screen.getByTestId('stage-dice')).toHaveAttribute('data-dock', 'RED');
    });

    it('shows ROLL DICE only to the player who may roll, and hides it once rolled', async () => {
      setup(match());
      expect(screen.getByTestId('roll-prompt')).toBeInTheDocument();
      expect(screen.getByTestId('pod-status-RED')).toHaveTextContent('YOUR TURN');
      await userEvent.click(screen.getByRole('button', { name: /roll dice/i }));
      expect(screen.queryByTestId('roll-prompt')).not.toBeInTheDocument();
    });

    it("never shows ROLL DICE on someone else's turn; their panel says TURN", () => {
      setup(match({ currentUserId: 'u2' }));
      expect(screen.queryByTestId('roll-prompt')).not.toBeInTheDocument();
      expect(screen.getByTestId('pod-status-YELLOW')).toHaveTextContent(/^TURN$/);
    });

    it('no ROLL DICE while a move is awaited', () => {
      setup(match({ diceValue: 4, awaitingMove: true, validMoves: [1, 2] }));
      expect(screen.queryByTestId('roll-prompt')).not.toBeInTheDocument();
    });

    it('turn banner: YOUR TURN! for me, "<name>\'s Turn" for others, once per turn', () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      try {
        const base = match();
        setup(base);
        expect(screen.getByTestId('turn-banner')).toHaveTextContent('YOUR TURN!');
        act(() => { vi.advanceTimersByTime(TURN_BANNER_MS + 50); });
        expect(screen.queryByTestId('turn-banner')).not.toBeInTheDocument();
        // extra turn for the same player: no new banner
        turnTo(base, { seq: 2, currentUserId: 'u1' });
        expect(screen.queryByTestId('turn-banner')).not.toBeInTheDocument();
        turnTo(base, { seq: 3, currentUserId: 'u2' });
        expect(screen.getByTestId('turn-banner')).toHaveTextContent("Ravi's Turn");
      } finally {
        vi.useRealTimers();
      }
    });

    it('every viewer sees the same active player from the server state', () => {
      const state = match({ currentUserId: 'u2' });
      setup(state);
      expect(screen.getByTestId('pod-YELLOW')).toHaveAttribute('data-active', 'true');
      expect(screen.getByTestId('stage-dice')).toHaveAttribute('data-dock', 'YELLOW');
      cleanupAll();
      // the opponent's screen, same server state
      useAuthStore.setState({ user: { ...USER, id: 'u2', name: 'Ravi' } });
      setupAs(state);
      expect(screen.getByTestId('pod-YELLOW')).toHaveAttribute('data-active', 'true');
      expect(screen.getByTestId('pod-status-YELLOW')).toHaveTextContent('YOUR TURN');
      expect(screen.getByTestId('roll-prompt')).toBeInTheDocument();
    });

    it('explains a roll without a legal move', () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      try {
        const base = match({ currentUserId: 'u2' });
        setup(base);
        act(() => {
          useGameStore.getState().applyState({ ...base, seq: 4, currentUserId: 'u1', lastDiceValue: 3, lastDiceUserId: 'u2' }, [
            { eventId: 'n1', seq: 2, type: 'DICE_ROLLED', userId: 'u2', data: { value: 3 }, at: 1 },
            { eventId: 'n2', seq: 3, type: 'NO_MOVES', userId: 'u2', data: { value: 3 }, at: 1 },
            { eventId: 'n3', seq: 4, type: 'TURN_CHANGED', userId: null, data: { nextUserId: 'u1', extraTurn: false }, at: 1 },
          ]);
        });
        act(() => { vi.advanceTimersByTime(DICE_ROLL_MS + 20); });
        expect(screen.getByTestId('game-notice')).toHaveTextContent('Ravi has no legal move with a 3');
      } finally {
        vi.useRealTimers();
      }
    });

    it('Menu > Leave game asks first; confirming sends the leave/forfeit request and leaves the page', async () => {
      RoomApi.leave.mockResolvedValue(null);
      setup(match());
      await userEvent.click(screen.getByTestId('menu-button'));
      expect(screen.getByTestId('game-menu')).toBeInTheDocument();
      await userEvent.click(screen.getByTestId('menu-leave'));
      await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
      expect(RoomApi.leave).not.toHaveBeenCalled();
      await userEvent.click(screen.getByTestId('menu-button'));
      await userEvent.click(screen.getByTestId('menu-leave'));
      await userEvent.click(screen.getByRole('button', { name: 'Leave game' }));
      await waitFor(() => expect(RoomApi.leave).toHaveBeenCalledWith('r1'));
      await screen.findByText('elsewhere');
    });

    it('Menu > Resume closes the menu; sound settings are reachable', async () => {
      setup(match());
      await userEvent.click(screen.getByTestId('menu-button'));
      await userEvent.click(screen.getByTestId('menu-sound'));
      expect(screen.getByTestId('sound-settings')).toBeInTheDocument();
      expect(screen.getByLabelText('Background music volume')).toBeInTheDocument();
      await userEvent.click(screen.getByRole('button', { name: 'Back' }));
      await userEvent.click(screen.getByTestId('menu-resume'));
      expect(screen.queryByTestId('game-menu')).not.toBeInTheDocument();
    });

    it('legal tokens get the glow and pointer, illegal ones stay normal', () => {
      setup(match({ diceValue: 6, awaitingMove: true, validMoves: [0, 3] }));
      expect(screen.getByTestId('token-arrow-RED-0')).toBeInTheDocument();
      expect(screen.getByTestId('token-arrow-RED-3')).toBeInTheDocument();
      expect(screen.queryByTestId('token-arrow-RED-1')).not.toBeInTheDocument();
      expect(screen.queryByTestId('token-arrow-RED-2')).not.toBeInTheDocument();
      expect(screen.queryByTestId('token-arrow-YELLOW-0')).not.toBeInTheDocument();
    });
  });
});
