import { describe, expect, it } from 'vitest';
import { BASE, HOME, canMove, forfeit, isValidSaved, move, newGame, roll } from '../lib/localEngine';

const first = () => 0; // random() -> first seat starts
const start = (names = ['A', 'B']) => newGame(names, { random: first }).state;
const withTokens = (s, idx, tokens) => ({ ...s, players: s.players.map((p, i) => (i === idx ? { ...p, tokens } : p)) });

describe('localEngine (offline pass & play)', () => {
  it('seats 2, 3 and 4 players with the same colours as the server', () => {
    expect(start(['A', 'B']).players.map((p) => p.color)).toEqual(['RED', 'YELLOW']);
    expect(start(['A', 'B', 'C']).players.map((p) => p.color)).toEqual(['RED', 'GREEN', 'YELLOW']);
    expect(start(['A', 'B', 'C', 'D']).players.map((p) => p.color)).toEqual(['RED', 'GREEN', 'YELLOW', 'BLUE']);
    expect(() => newGame(['solo'])).toThrow();
    expect(() => newGame(['a', 'b', 'c', 'd', 'e'])).toThrow();
  });

  it('empty names get a default', () => {
    expect(start(['  ', 'B']).players[0].name).toBe('Player 1');
  });

  it('needs a 6 to leave base and an exact roll to finish', () => {
    expect(canMove(BASE, 5)).toBe(false);
    expect(canMove(BASE, 6)).toBe(true);
    expect(canMove(53, 3)).toBe(true);
    expect(canMove(53, 4)).toBe(false);
    expect(canMove(HOME, 1)).toBe(false);
  });

  it('no legal move passes the turn', () => {
    const { state, events } = roll(start(), 3);
    expect(events.map((e) => e.type)).toEqual(['DICE_ROLLED', 'NO_MOVES', 'TURN_CHANGED']);
    expect(state.currentUserId).toBe('p1');
    expect(state.lastDiceValue).toBe(3);
  });

  it('a 6 brings a token out and grants another roll', () => {
    let s = roll(start(), 6).state;
    expect(s.awaitingMove).toBe(true);
    expect(s.validMoves).toEqual([0, 1, 2, 3]);
    const r = move(s, 0);
    s = r.state;
    expect(s.players[0].tokens[0]).toBe(0);
    expect(s.currentUserId).toBe('p0');
    expect(r.events.at(-1)).toMatchObject({ type: 'TURN_CHANGED', data: { extraTurn: true } });
  });

  it('turns rotate over all three players', () => {
    let s = start(['A', 'B', 'C']);
    const order = [];
    for (let i = 0; i < 4; i += 1) {
      order.push(s.currentUserId);
      s = roll(s, 2).state; // nobody can leave base
    }
    expect(order).toEqual(['p0', 'p1', 'p2', 'p0']);
  });

  it('landing on an opponent captures it and grants an extra roll; safe cells protect', () => {
    // RED at step 3 (global 3); YELLOW (start 26) at step 31 -> global 57 % 52 = 5
    let s = withTokens(start(), 0, [3, BASE, BASE, BASE]);
    s = withTokens(s, 1, [31, BASE, BASE, BASE]);
    s = roll(s, 2).state;
    const r = move(s, 0);
    expect(r.state.players[1].tokens[0]).toBe(BASE);
    expect(r.events.map((e) => e.type)).toContain('TOKEN_CAPTURED');
    expect(r.state.currentUserId).toBe('p0');

    // global 8 is a star: no capture there (YELLOW step 34 -> global 60 % 52 = 8)
    let t = withTokens(start(), 0, [6, BASE, BASE, BASE]);
    t = withTokens(t, 1, [34, BASE, BASE, BASE]);
    t = move(roll(t, 2).state, 0).state;
    expect(t.players[1].tokens[0]).toBe(34);
    expect(t.currentUserId).toBe('p1');
  });

  it('three sixes in a row forfeit the turn', () => {
    let s = withTokens(start(), 0, [10, BASE, BASE, BASE]);
    s = move(roll(s, 6).state, 0).state;
    s = move(roll(s, 6).state, 0).state;
    const r = roll(s, 6);
    expect(r.events.map((e) => e.type)).toEqual(['DICE_ROLLED', 'TRIPLE_SIX', 'TURN_CHANGED']);
    expect(r.state.currentUserId).toBe('p1');
    expect(r.state.players[0].tokens[0]).toBe(22);
  });

  it('bringing the last token home wins and ranks everyone', () => {
    let s = withTokens(start(['A', 'B', 'C']), 0, [HOME, HOME, HOME, 54]);
    s = withTokens(s, 2, [20, BASE, BASE, BASE]);
    const r = move(roll(s, 2).state, 3);
    expect(r.state.status).toBe('FINISHED');
    expect(r.state.winnerId).toBe('p0');
    expect(r.state.players.map((p) => p.rank)).toEqual([1, 3, 2]);
    expect(r.events.at(-1).type).toBe('MATCH_FINISHED');
    expect(() => roll(r.state, 3)).toThrow();
  });

  it('rejects illegal actions and never mutates the input state', () => {
    const s = start();
    const frozen = JSON.stringify(s);
    expect(() => move(s, 0)).toThrow();
    expect(() => roll(s, 7)).toThrow();
    const rolled = roll(s, 6).state;
    expect(() => roll(rolled, 2)).toThrow();
    expect(() => move(rolled, 9)).toThrow();
    move(rolled, 0);
    expect(JSON.stringify(s)).toBe(frozen);
  });

  it('a player leaving hands the turn on; the last one left wins', () => {
    let s = start(['A', 'B', 'C']);
    s = forfeit(s, 'p0').state;
    expect(s.currentUserId).toBe('p1');
    const r = forfeit(s, 'p2');
    expect(r.state.status).toBe('FINISHED');
    expect(r.state.winnerId).toBe('p1');
  });

  it('validates saved games read back from storage', () => {
    expect(isValidSaved(start())).toBe(true);
    expect(isValidSaved(null)).toBe(false);
    expect(isValidSaved({ ...start(), local: false })).toBe(false);
    expect(isValidSaved(withTokens(start(), 0, [99, 0, 0, 0]))).toBe(false);
  });
});
