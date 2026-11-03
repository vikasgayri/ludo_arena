import { describe, expect, it, vi } from 'vitest';
import { GameSocket, newActionId, toBrokerUrl } from '../lib/ws';
import { tokens } from '../lib/tokens';

describe('ws helpers', () => {
  it('converts http(s) URLs to ws(s)', () => {
    expect(toBrokerUrl('http://localhost:8080/ws')).toBe('ws://localhost:8080/ws');
    expect(toBrokerUrl('https://api.example.com/ws')).toBe('wss://api.example.com/ws');
  });

  it('generates unique action ids', () => {
    const ids = new Set(Array.from({ length: 200 }, () => newActionId()));
    expect(ids.size).toBe(200);
  });
});

describe('GameSocket', () => {
  function build() {
    const subs = [];
    let cfg;
    const client = {
      connected: false,
      connectHeaders: {},
      activate: vi.fn(() => {}),
      deactivate: vi.fn(),
      publish: vi.fn(),
      subscribe: vi.fn((dest, cb) => { const s = { dest, cb, unsubscribe: vi.fn() }; subs.push(s); return s; }),
    };
    const statuses = [];
    const sock = new GameSocket({ onStatus: (s) => statuses.push(s), clientFactory: (c) => { cfg = c; return client; } });
    return { sock, client, subs, statuses, cfg: () => cfg };
  }

  it('sends the JWT in the CONNECT headers (never in the URL) and subscribes once connected', async () => {
    tokens.set(`h.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 600 }))}.s`, 'r');
    const { sock, client, subs, statuses, cfg } = build();
    const handler = vi.fn();
    sock.connect();
    sock.subscribe('k', '/topic/match/1', handler); // registered before the socket is up
    expect(subs).toHaveLength(0);
    await cfg().beforeConnect();
    expect(client.connectHeaders.Authorization).toMatch(/^Bearer h\./);
    expect(cfg().brokerURL).not.toContain('token');
    client.connected = true;
    cfg().onConnect();
    expect(statuses).toContain('connected');
    expect(subs.map((s) => s.dest)).toEqual(['/topic/match/1']);
    subs[0].cb({ body: JSON.stringify({ hello: 1 }) });
    expect(handler).toHaveBeenCalledWith({ hello: 1 }, expect.anything());
  });

  it('re-subscribes after a reconnect', () => {
    const { sock, client, subs, cfg } = build();
    sock.connect();
    client.connected = true;
    sock.subscribe('k', '/topic/room/9', vi.fn());
    expect(subs).toHaveLength(1);
    cfg().onWebSocketClose();
    client.connected = true;
    cfg().onConnect();
    expect(subs).toHaveLength(2);
  });

  it('publish returns false while disconnected and JSON-encodes bodies when connected', () => {
    const { sock, client } = build();
    sock.connect();
    expect(sock.publish('/app/x', { a: 1 })).toBe(false);
    client.connected = true;
    expect(sock.publish('/app/x', { a: 1 })).toBe(true);
    expect(client.publish).toHaveBeenCalledWith({ destination: '/app/x', body: '{"a":1}' });
  });
});
