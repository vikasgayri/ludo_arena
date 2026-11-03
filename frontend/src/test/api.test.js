import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';

const ok = (config, data, status = 200) => ({ data, status, statusText: 'OK', headers: {}, config });
const fail = (config, status, body) => {
  const err = new Error(`status ${status}`);
  err.config = config;
  err.response = { status, data: body, headers: {}, config };
  err.isAxiosError = true;
  return Promise.reject(err);
};

describe('api client', () => {
  let calls;
  beforeEach(() => {
    vi.resetModules();
    calls = [];
    localStorage.clear();
  });
  afterEach(() => { delete axios.defaults.adapter; });

  async function load(handler) {
    axios.defaults.adapter = (config) => { calls.push({ url: config.url, auth: config.headers.Authorization, body: config.data }); return handler(config); };
    const [apiMod, tokenMod] = await Promise.all([import('../lib/api'), import('../lib/tokens')]);
    return { ...apiMod, ...tokenMod };
  }

  it('attaches the bearer token and unwraps the envelope', async () => {
    const { WalletApi, tokens } = await load((c) => Promise.resolve(ok(c, { success: true, data: { coinBalance: 5 } })));
    tokens.set('access-1', 'refresh-1');
    await expect(WalletApi.get()).resolves.toEqual({ coinBalance: 5 });
    expect(calls[0].auth).toBe('Bearer access-1');
  });

  it('refreshes once on 401 and retries the original request with the new token', async () => {
    let first = true;
    const { WalletApi, tokens } = await load((c) => {
      if (c.url === '/auth/refresh') return Promise.resolve(ok(c, { success: true, data: { accessToken: 'new-acc', refreshToken: 'new-ref' } }));
      if (first) { first = false; return fail(c, 401, { success: false }); }
      return Promise.resolve(ok(c, { success: true, data: { coinBalance: 9 } }));
    });
    tokens.set('old-acc', 'old-ref');
    await expect(WalletApi.get()).resolves.toEqual({ coinBalance: 9 });
    expect(calls.map((c) => c.url)).toEqual(['/wallet', '/auth/refresh', '/wallet']);
    expect(calls[2].auth).toBe('Bearer new-acc');
    expect(tokens.getRefresh()).toBe('new-ref');
  });

  it('shares a single refresh between concurrent 401s', async () => {
    const seen = new Set();
    const { WalletApi, UserApi, tokens } = await load((c) => {
      if (c.url === '/auth/refresh') return new Promise((r) => setTimeout(() => r(ok(c, { success: true, data: { accessToken: 'n', refreshToken: 'r2' } })), 10));
      if (!seen.has(c.url)) { seen.add(c.url); return fail(c, 401, {}); }
      return Promise.resolve(ok(c, { success: true, data: { ok: true } }));
    });
    tokens.set('a', 'r');
    await Promise.all([WalletApi.get(), UserApi.me()]);
    expect(calls.filter((c) => c.url === '/auth/refresh')).toHaveLength(1);
  });

  it('clears tokens and notifies when the refresh itself fails', async () => {
    const { WalletApi, tokens } = await load((c) => (c.url === '/auth/refresh' ? fail(c, 401, {}) : fail(c, 401, {})));
    const lost = vi.fn();
    tokens.onAuthLost(lost);
    tokens.set('a', 'r');
    await expect(WalletApi.get()).rejects.toBeTruthy();
    expect(lost).toHaveBeenCalled();
    expect(tokens.getAccess()).toBeNull();
  });

  it('does not try to refresh failed logins', async () => {
    const { AuthApi } = await load((c) => fail(c, 401, { success: false, error: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' } }));
    await expect(AuthApi.login({ email: 'a@b.c', password: 'x' })).rejects.toBeTruthy();
    expect(calls.map((c) => c.url)).toEqual(['/auth/login']);
  });

  it('extracts readable error messages', async () => {
    const { errorMessage, fieldErrors, errorCode } = await load(() => Promise.resolve());
    const e = { response: { data: { error: { code: 'X', message: 'Nope', fieldErrors: { a: 'bad' } } } } };
    expect(errorMessage(e)).toBe('Nope');
    expect(fieldErrors(e)).toEqual({ a: 'bad' });
    expect(errorCode(e)).toBe('X');
    expect(errorMessage({ code: 'ERR_NETWORK' })).toMatch(/cannot reach the server/i);
    expect(errorMessage({}, 'fallback')).toBe('fallback');
  });
});
