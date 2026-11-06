/**
 * Offline "pass & play" Ludo engine: the same rules as the server engine (com.ludoarena.game), ported to plain JS so
 * friends can play on one phone without an account or a connection. Online matches never use this file - the server
 * stays the only authority there.
 *
 * Steps per token: -1 = base, 0..50 = main track, 51..55 = home column, 56 = home.
 * The state has the same shape as the server's MatchStateDto, so the board, dice, log and sounds are reused as-is.
 * Every function is pure: it returns a new state and never mutates its input.
 */
export const BASE = -1;
export const HOME = 56;
const LAST_TRACK_STEP = 50;
const TRACK_LENGTH = 52;
const START = { RED: 0, GREEN: 13, YELLOW: 26, BLUE: 39 };
const SAFE = new Set([0, 8, 13, 21, 26, 34, 39, 47]);
/** 2 players sit opposite each other; 3 players take three consecutive colours (same as the server). */
export const COLORS_FOR = { 2: ['RED', 'YELLOW'], 3: ['RED', 'GREEN', 'YELLOW'], 4: ['RED', 'GREEN', 'YELLOW', 'BLUE'] };
export const DEFAULT_RULES = { extraTurnOnSix: true, extraTurnOnCapture: true, extraTurnOnHome: true, maxConsecutiveSixes: 3 };

export const canMove = (steps, dice) => {
  if (dice < 1 || dice > 6 || steps === HOME) return false;
  if (steps === BASE) return dice === 6;
  return steps + dice <= HOME;
};
export const targetSteps = (steps, dice) => (steps === BASE ? 0 : steps + dice);
const globalIndex = (color, steps) => (steps >= 0 && steps <= LAST_TRACK_STEP ? (START[color] + steps) % TRACK_LENGTH : -1);
const finished = (p) => p.tokens.filter((s) => s === HOME).length;
const progress = (p) => p.tokens.reduce((sum, s) => sum + (s < 0 ? 0 : s), 0);

const clone = (s) => ({
  ...s,
  rules: { ...s.rules },
  validMoves: [...s.validMoves],
  players: s.players.map((p) => ({ ...p, tokens: [...p.tokens] })),
});

function event(s, type, userId, data = {}) {
  s.seq += 1;
  return { eventId: `local-${s.seq}`, seq: s.seq, type, userId, data, at: Date.now() };
}

function syncDerived(s) {
  s.players.forEach((p) => { p.finishedTokens = finished(p); });
  s.currentUserId = s.players[s.turnIndex].userId;
}

/** names: 2-4 player names. random: () => [0,1) (injectable for tests). */
export function newGame(names, { random = Math.random, rules = DEFAULT_RULES } = {}) {
  const n = names.length;
  if (n < 2 || n > 4) throw new Error('Ludo needs 2 to 4 players');
  const colors = COLORS_FOR[n];
  const s = {
    matchId: `local-${Date.now()}`,
    local: true,
    status: 'ACTIVE',
    mode: n,
    players: names.map((name, i) => ({
      userId: `p${i}`, name: String(name || '').trim() || `Player ${i + 1}`, avatar: null, color: colors[i], seat: i,
      tokens: [BASE, BASE, BASE, BASE], connected: true, forfeited: false, finishedTokens: 0, rank: 0, bot: false,
    })),
    turnIndex: Math.floor(random() * n) % n,
    currentUserId: null,
    diceValue: null,
    lastDiceValue: null,
    lastDiceUserId: null,
    awaitingMove: false,
    validMoves: [],
    consecutiveSixes: 0,
    winnerId: null,
    seq: 0,
    rules: { ...rules },
    vsComputer: false,
  };
  syncDerived(s);
  return { state: s, events: [event(s, 'MATCH_STARTED', null, { firstUserId: s.currentUserId })] };
}

export const rollValue = (random = Math.random) => Math.floor(random() * 6) + 1;

function passTurn(s, events) {
  const n = s.players.length;
  for (let k = 1; k <= n; k += 1) {
    const idx = (s.turnIndex + k) % n;
    if (!s.players[idx].forfeited) { s.turnIndex = idx; break; }
  }
  s.consecutiveSixes = 0;
  s.diceValue = null;
  s.awaitingMove = false;
  s.validMoves = [];
  syncDerived(s);
  events.push(event(s, 'TURN_CHANGED', null, { nextUserId: s.currentUserId, extraTurn: false }));
}

function finish(s, winner, events) {
  s.status = 'FINISHED';
  s.awaitingMove = false;
  s.diceValue = null;
  s.validMoves = [];
  s.winnerId = winner ? winner.userId : null;
  const others = s.players.filter((p) => p !== winner)
    .sort((a, b) => Number(a.forfeited) - Number(b.forfeited) || progress(b) - progress(a));
  let rank = 1;
  if (winner) { winner.rank = rank; rank += 1; }
  others.forEach((p) => { p.rank = rank; rank += 1; });
  syncDerived(s);
  events.push(event(s, 'MATCH_FINISHED', s.winnerId, { winnerId: s.winnerId || '', ranking: s.players.map((p) => ({ userId: p.userId, rank: p.rank })) }));
}

/** The current player rolls `value` (1-6). */
export function roll(state, value) {
  if (state.status !== 'ACTIVE') throw new Error('The game is over');
  if (state.awaitingMove || state.diceValue != null) throw new Error('Move a token first');
  if (!Number.isInteger(value) || value < 1 || value > 6) throw new Error('Invalid dice value');
  const s = clone(state);
  const p = s.players[s.turnIndex];
  const events = [];
  s.diceValue = value;
  s.lastDiceValue = value;
  s.lastDiceUserId = p.userId;
  s.consecutiveSixes = value === 6 ? s.consecutiveSixes + 1 : 0;
  events.push(event(s, 'DICE_ROLLED', p.userId, { value }));
  const max = s.rules.maxConsecutiveSixes;
  if (value === 6 && max > 0 && s.consecutiveSixes >= max) {
    events.push(event(s, 'TRIPLE_SIX', p.userId));
    passTurn(s, events);
    return { state: s, events };
  }
  const moves = [0, 1, 2, 3].filter((i) => canMove(p.tokens[i], value));
  if (!moves.length) {
    events.push(event(s, 'NO_MOVES', p.userId, { value }));
    passTurn(s, events);
    return { state: s, events };
  }
  s.validMoves = moves;
  s.awaitingMove = true;
  syncDerived(s);
  return { state: s, events };
}

/** The current player moves token `tokenIndex` with the rolled dice. */
export function move(state, tokenIndex) {
  if (state.status !== 'ACTIVE') throw new Error('The game is over');
  if (!state.awaitingMove || state.diceValue == null) throw new Error('Roll the dice first');
  if (!state.validMoves.includes(tokenIndex)) throw new Error('That token cannot move');
  const s = clone(state);
  const p = s.players[s.turnIndex];
  const dice = s.diceValue;
  const events = [];
  const from = p.tokens[tokenIndex];
  const to = targetSteps(from, dice);
  p.tokens[tokenIndex] = to;
  events.push(event(s, 'TOKEN_MOVED', p.userId, { tokenIndex, from, to, dice }));

  const captured = [];
  const g = globalIndex(p.color, to);
  if (g >= 0 && !SAFE.has(g)) {
    s.players.forEach((o) => {
      if (o === p || o.forfeited) return;
      o.tokens.forEach((st, i) => {
        if (globalIndex(o.color, st) === g) {
          o.tokens[i] = BASE;
          captured.push(o.userId);
          events.push(event(s, 'TOKEN_CAPTURED', p.userId, { victimUserId: o.userId, tokenIndex: i }));
        }
      });
    });
  }
  const reachedHome = to === HOME;
  if (reachedHome) events.push(event(s, 'TOKEN_HOME', p.userId, { tokenIndex }));
  syncDerived(s);

  if (finished(p) === 4) {
    finish(s, p, events);
    return { state: s, events };
  }
  const extra = (dice === 6 && s.rules.extraTurnOnSix) || (captured.length > 0 && s.rules.extraTurnOnCapture)
    || (reachedHome && s.rules.extraTurnOnHome);
  if (extra) {
    s.diceValue = null;
    s.awaitingMove = false;
    s.validMoves = [];
    events.push(event(s, 'TURN_CHANGED', p.userId, { nextUserId: p.userId, extraTurn: true }));
  } else {
    passTurn(s, events);
  }
  return { state: s, events };
}

/** A player leaves the table (their tokens go back to base). The last one left wins. */
export function forfeit(state, userId) {
  const s = clone(state);
  const p = s.players.find((x) => x.userId === userId);
  const events = [];
  if (!p || p.forfeited || s.status !== 'ACTIVE') return { state, events };
  const wasCurrent = s.players[s.turnIndex] === p;
  p.forfeited = true;
  p.tokens = [BASE, BASE, BASE, BASE];
  events.push(event(s, 'PLAYER_FORFEITED', userId));
  const left = s.players.filter((x) => !x.forfeited);
  if (left.length <= 1) finish(s, left[0] || null, events);
  else if (wasCurrent) passTurn(s, events);
  else syncDerived(s);
  return { state: s, events };
}

/** Basic shape check for a saved game read back from storage. */
export function isValidSaved(s) {
  return !!s && s.local === true && Array.isArray(s.players) && s.players.length >= 2 && s.players.length <= 4
    && s.players.every((p) => Array.isArray(p.tokens) && p.tokens.length === 4 && p.tokens.every((t) => Number.isInteger(t) && t >= BASE && t <= HOME))
    && Number.isInteger(s.turnIndex) && s.turnIndex >= 0 && s.turnIndex < s.players.length && s.rules != null;
}
