import { create } from 'zustand';
import { GameSocket, Dest, newActionId } from '../lib/ws';

const MAX_EVENTS = 200;

function saveToken(matchId, token) {
  try { sessionStorage.setItem(`la_rt_${matchId}`, token); } catch { /* ignore */ }
}
function loadToken(matchId) {
  try { return sessionStorage.getItem(`la_rt_${matchId}`) || undefined; } catch { return undefined; }
}

let socket = null;
let pendingTimer = null;

/** If the server never answers (lost frame), release the UI so the player can retry. Retrying is safe: actions are idempotent. */
function armPendingGuard(set, get) {
  clearTimeout(pendingTimer);
  pendingTimer = setTimeout(() => { if (get().pending) set({ pending: false }); }, 6000);
}

/**
 * Real-time game state. The server is authoritative: this store only applies what the server sends.
 * Ordering/idempotency: a MatchUpdate whose state.seq is older than what we hold is ignored, and events are
 * de-duplicated by eventId, so reconnects and duplicate deliveries are harmless.
 */
export const useGameStore = create((set, get) => ({
  connection: 'disconnected',
  matchId: null,
  match: null, // MatchStateDto
  events: [], // newest last
  lastUpdateEvents: [], // events from the most recent update (for animations)
  room: null, // RoomDto pushed over /topic/room/{id}
  roomEvent: null, // { type, matchId }
  error: null,
  pending: false, // an action is in flight (rolling / moving)
  serverOffsetMs: 0,

  connect() {
    if (socket) return;
    socket = new GameSocket({ onStatus: (s) => set({ connection: s }) });
    socket.subscribe('errors', Dest.errors, (body) => {
      set({ error: body?.message || 'Action rejected', pending: false });
    });
    socket.subscribe('userState', Dest.userState, (snap) => {
      if (!snap?.state) return;
      if (snap.reconnectToken && snap.state.matchId) saveToken(snap.state.matchId, snap.reconnectToken);
      get().applyState(snap.state, []);
    });
    socket.connect();
  },

  disconnect() {
    if (socket) { socket.disconnect(); socket = null; }
    set({ connection: 'disconnected', matchId: null, match: null, events: [], lastUpdateEvents: [], room: null, roomEvent: null, error: null, pending: false });
  },

  // ---- room (lobby) -------------------------------------------------------
  watchRoom(roomId) {
    get().connect();
    socket.subscribe('room', Dest.room(roomId), (update) => {
      set({ room: update.room, roomEvent: { type: update.type, matchId: update.matchId } });
    });
    socket.publish(Dest.join, { roomId });
  },

  unwatchRoom() {
    socket?.unsubscribe('room');
    set({ room: null, roomEvent: null });
  },

  setReady(roomId, ready) {
    return socket?.publish(Dest.ready, { roomId, ready }) ?? false;
  },

  // ---- match --------------------------------------------------------------
  watchMatch(matchId) {
    get().connect();
    set({ matchId, match: null, events: [], lastUpdateEvents: [], error: null, pending: false });
    socket.subscribe('match', Dest.match(matchId), (update) => {
      if (!update?.state) return;
      get().applyState(update.state, update.events || []);
    });
    // Ask for a full snapshot (also the reconnection path); the server rotates the reconnect token.
    get().requestState();
  },

  unwatchMatch() {
    socket?.unsubscribe('match');
    set({ matchId: null, match: null, events: [], lastUpdateEvents: [], pending: false });
  },

  requestState() {
    const { matchId } = get();
    if (!matchId || !socket) return;
    // A reconnect that happens after the socket is up again re-sends this via the 'connected' watcher below.
    const send = () => socket.publish(Dest.state, { matchId, reconnectToken: loadToken(matchId) });
    if (!send()) {
      const unsub = useGameStore.subscribe((s) => {
        if (s.connection === 'connected') { unsub(); send(); }
      });
    }
  },

  applyState(state, events) {
    const cur = get().match;
    if (cur && cur.matchId === state.matchId && state.seq < cur.seq) return; // stale
    const known = new Set(get().events.map((e) => e.eventId));
    const fresh = events.filter((e) => !known.has(e.eventId));
    const offset = state.serverTime ? state.serverTime - Date.now() : get().serverOffsetMs;
    set({
      match: state,
      events: fresh.length ? [...get().events, ...fresh].slice(-MAX_EVENTS) : get().events,
      lastUpdateEvents: fresh,
      pending: false,
      serverOffsetMs: offset,
    });
  },

  rollDice() {
    const { matchId, pending } = get();
    if (!matchId || pending) return false;
    set({ pending: true, error: null });
    armPendingGuard(set, get);
    const ok = socket?.publish(Dest.roll, { matchId, actionId: newActionId() }) ?? false;
    if (!ok) set({ pending: false, error: 'Not connected. Reconnecting…' });
    return ok;
  },

  moveToken(tokenIndex) {
    const { matchId, pending } = get();
    if (!matchId || pending) return false;
    set({ pending: true, error: null });
    armPendingGuard(set, get);
    const ok = socket?.publish(Dest.move, { matchId, tokenIndex, actionId: newActionId() }) ?? false;
    if (!ok) set({ pending: false, error: 'Not connected. Reconnecting…' });
    return ok;
  },

  clearError() { set({ error: null }); },
}));

// Test hook: lets tests inject a fake socket without a network.
export function __setSocketForTests(s) { socket = s; }
