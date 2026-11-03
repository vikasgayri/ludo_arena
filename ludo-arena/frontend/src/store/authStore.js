import { create } from 'zustand';
import { AuthApi, UserApi } from '../lib/api';
import { tokens } from '../lib/tokens';

export const useAuthStore = create((set, get) => ({
  user: null,
  // idle -> loading -> authenticated | anonymous
  status: 'idle',

  /** Restore a session from stored tokens on app start. */
  async init() {
    if (get().status !== 'idle') return;
    if (!tokens.getAccess() && !tokens.getRefresh()) {
      set({ status: 'anonymous' });
      return;
    }
    set({ status: 'loading' });
    try {
      const user = await AuthApi.me();
      set({ user, status: 'authenticated' });
    } catch {
      tokens.clear();
      set({ user: null, status: 'anonymous' });
    }
  },

  async login(email, password) {
    const res = await AuthApi.login({ email, password });
    tokens.set(res.accessToken, res.refreshToken);
    set({ user: res.user, status: 'authenticated' });
    return res.user;
  },

  async register(name, email, password) {
    const res = await AuthApi.register({ name, email, password });
    tokens.set(res.accessToken, res.refreshToken);
    set({ user: res.user, status: 'authenticated' });
    return res.user;
  },

  async logout() {
    const refresh = tokens.getRefresh();
    try { if (refresh) await AuthApi.logout(refresh); } catch { /* best effort */ }
    tokens.clear();
    set({ user: null, status: 'anonymous' });
  },

  async refreshUser() {
    const user = await UserApi.me();
    set({ user });
    return user;
  },

  setUser(user) { set({ user }); },

  /** Called by the API layer when the refresh token is rejected. */
  sessionLost() {
    set({ user: null, status: 'anonymous' });
  },

  isAdmin() {
    const r = get().user?.role;
    return r === 'ADMIN' || r === 'SUPER_ADMIN';
  },
}));

tokens.onAuthLost(() => useAuthStore.getState().sessionLost());
