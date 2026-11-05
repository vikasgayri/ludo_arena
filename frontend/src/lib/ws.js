import { Client } from '@stomp/stompjs';
import { tokens } from './tokens';
import { refreshSession } from './api';

const RAW = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';

/** The backend exposes a plain WebSocket endpoint (no SockJS); http(s) -> ws(s). */
export function toBrokerUrl(url) {
  return url.replace(/^http/i, 'ws');
}

export const WS_URL = toBrokerUrl(RAW);

let counter = 0;
/** Client-generated idempotency key for dice rolls / moves. */
export function newActionId() {
  counter += 1;
  const rnd = (typeof crypto !== 'undefined' && crypto.randomUUID)
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  return `${rnd}-${counter}`;
}

/**
 * Thin wrapper over @stomp/stompjs. The JWT travels in the CONNECT frame header (never in the URL).
 * Subscriptions registered before the socket is up are (re)applied on every (re)connect.
 */
export class GameSocket {
  constructor({ onStatus, clientFactory } = {}) {
    this.onStatus = onStatus || (() => {});
    this.subs = new Map(); // key -> { destination, handler, active }
    this.status = 'disconnected';
    this.clientFactory = clientFactory || ((cfg) => new Client(cfg));
    this.client = null;
  }

  setStatus(s) {
    this.status = s;
    this.onStatus(s);
  }

  connect() {
    if (this.client) return;
    this.setStatus('connecting');
    this.client = this.clientFactory({
      brokerURL: WS_URL,
      reconnectDelay: 2000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: async () => {
        // Make sure the access token in the CONNECT frame is fresh.
        const token = tokens.getAccess();
        if (!token || isExpiringSoon(token)) {
          try { await refreshSession(); } catch { /* handled by connect failure */ }
        }
        this.client.connectHeaders = { Authorization: `Bearer ${tokens.getAccess() || ''}` };
      },
      onConnect: () => {
        this.setStatus('connected');
        this.subs.forEach((s) => this.attach(s));
      },
      onWebSocketClose: () => {
        if (this.client && this.status !== 'disconnected') this.setStatus('reconnecting');
        this.subs.forEach((s) => { s.active = null; });
      },
      onStompError: () => this.setStatus('reconnecting'),
    });
    this.client.activate();
  }

  attach(s) {
    if (!this.client?.connected) return;
    if (s.active) return;
    s.active = this.client.subscribe(s.destination, (msg) => {
      let body = null;
      try { body = JSON.parse(msg.body); } catch { body = msg.body; }
      s.handler(body, msg);
    });
  }

  /** Returns an unsubscribe function. Re-subscribing with the same key replaces the old subscription. */
  subscribe(key, destination, handler) {
    this.unsubscribe(key);
    const s = { destination, handler, active: null };
    this.subs.set(key, s);
    this.attach(s);
    return () => this.unsubscribe(key);
  }

  unsubscribe(key) {
    const s = this.subs.get(key);
    if (s?.active) { try { s.active.unsubscribe(); } catch { /* ignore */ } }
    this.subs.delete(key);
  }

  publish(destination, body) {
    if (!this.client?.connected) return false;
    this.client.publish({ destination, body: JSON.stringify(body ?? {}) });
    return true;
  }

  disconnect() {
    this.subs.forEach((_, k) => this.unsubscribe(k));
    const c = this.client;
    this.client = null;
    this.setStatus('disconnected');
    if (c) { try { c.deactivate(); } catch { /* ignore */ } }
  }
}

function isExpiringSoon(jwt) {
  try {
    const payload = JSON.parse(atob(jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload.exp * 1000 - Date.now() < 30000;
  } catch {
    return false;
  }
}

export const Dest = {
  join: '/app/room.join',
  leave: '/app/room.leave',
  ready: '/app/room.ready',
  roll: '/app/match.rollDice',
  move: '/app/match.moveToken',
  state: '/app/match.requestState',
  chatSend: '/app/match.chat',
  voiceSend: '/app/match.voice',
  room: (id) => `/topic/room/${id}`,
  match: (id) => `/topic/match/${id}`,
  chat: (id) => `/topic/match/${id}/chat`,
  errors: '/user/queue/errors',
  userState: '/user/queue/state',
  voice: '/user/queue/voice',
};
