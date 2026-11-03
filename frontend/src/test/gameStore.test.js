import { beforeEach, describe, expect, it, vi } from 'vitest';
import { __setSocketForTests, useGameStore } from '../store/gameStore';

function fakeSocket(connected = true) {
  return {
    published: [],
    publish(dest, body) { if (!connected) return false; this.published.push({ dest, body }); return true; },
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
    disconnect: vi.fn(),
  };
}

const state = (seq, extra = {}) => ({
  matchId: 'm1', roomId: 'r1', status: 'ACTIVE', mode: 2, players: [], currentUserId: 'u1', diceValue: null, lastDiceValue: null,
  awaitingMove: false, validMoves: [], consecutiveSixes: 0, winnerId: null, turnDeadline: 0, serverTime: Date.now(), seq, ...extra,
});
const ev = (id, seq, type = 'DICE_ROLLED') => ({ eventId: id, seq, type, userId: 'u1', data: { value: 3 }, at: 1 });

beforeEach(() => {
  useGameStore.setState({ matchId: 'm1', match: null, events: [], lastUpdateEvents: [], pending: false, error: null, connection: 'connected' });
});

describe('gameStore', () => {
  it('applies server state and appends new events', () => {
    useGameStore.getState().applyState(state(1), [ev('e1', 1)]);
    expect(useGameStore.getState().match.seq).toBe(1);
    expect(useGameStore.getState().events.map((e) => e.eventId)).toEqual(['e1']);
  });

  it('ignores stale updates (older seq) so late frames cannot rewind the board', () => {
    const s = useGameStore.getState();
    s.applyState(state(5, { diceValue: 6 }), [ev('e5', 5)]);
    s.applyState(state(3, { diceValue: 1 }), [ev('e3', 3)]);
    expect(useGameStore.getState().match.seq).toBe(5);
    expect(useGameStore.getState().match.diceValue).toBe(6);
    expect(useGameStore.getState().events.map((e) => e.eventId)).toEqual(['e5']);
  });

  it('de-duplicates events delivered twice (reconnect / replay)', () => {
    const s = useGameStore.getState();
    s.applyState(state(2), [ev('e1', 1), ev('e2', 2)]);
    s.applyState(state(2), [ev('e1', 1), ev('e2', 2)]);
    expect(useGameStore.getState().events).toHaveLength(2);
    expect(useGameStore.getState().lastUpdateEvents).toHaveLength(0);
  });

  it('accepts an equal-seq snapshot (state resync) and clears pending', () => {
    useGameStore.setState({ pending: true });
    useGameStore.getState().applyState(state(2), []);
    useGameStore.getState().applyState(state(2, { awaitingMove: true, validMoves: [1] }), []);
    expect(useGameStore.getState().match.validMoves).toEqual([1]);
    expect(useGameStore.getState().pending).toBe(false);
  });

  it('rollDice publishes to /app/match.rollDice with an idempotency actionId and blocks double-clicks', () => {
    const sock = fakeSocket();
    __setSocketForTests(sock);
    expect(useGameStore.getState().rollDice()).toBe(true);
    expect(useGameStore.getState().rollDice()).toBe(false); // pending
    expect(sock.published).toHaveLength(1);
    expect(sock.published[0].dest).toBe('/app/match.rollDice');
    expect(sock.published[0].body.matchId).toBe('m1');
    expect(sock.published[0].body.actionId).toMatch(/.{8,}/);
  });

  it('moveToken publishes the token index and a unique actionId per action', () => {
    const sock = fakeSocket();
    __setSocketForTests(sock);
    useGameStore.getState().moveToken(2);
    useGameStore.setState({ pending: false });
    useGameStore.getState().moveToken(2);
    const [a, b] = sock.published;
    expect(a.dest).toBe('/app/match.moveToken');
    expect(a.body.tokenIndex).toBe(2);
    expect(a.body.actionId).not.toBe(b.body.actionId);
  });

  it('reports an error and releases the UI when the socket is down', () => {
    __setSocketForTests(fakeSocket(false));
    expect(useGameStore.getState().rollDice()).toBe(false);
    expect(useGameStore.getState().pending).toBe(false);
    expect(useGameStore.getState().error).toMatch(/not connected/i);
  });

  it('requestState sends the stored reconnect token', () => {
    const sock = fakeSocket();
    __setSocketForTests(sock);
    sessionStorage.setItem('la_rt_m1', 'tok-xyz');
    useGameStore.getState().requestState();
    expect(sock.published[0]).toEqual({ dest: '/app/match.requestState', body: { matchId: 'm1', reconnectToken: 'tok-xyz' } });
  });
});
