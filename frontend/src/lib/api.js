import axios from 'axios';
import { tokens } from './tokens';

export const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

// 45 s: a sleeping free-tier backend (e.g. Render) can need ~30-60 s for its first response
export const api = axios.create({ baseURL: API_BASE, timeout: 45000 });
/** Backend origin (API base without /api/v1), used for the health ping. */
export const API_ORIGIN = API_BASE.replace(/\/api\/v\d+\/?$/, '');
/** True when a production build was made without VITE_API_BASE_URL (it would silently call localhost). */
export const API_MISCONFIGURED = import.meta.env.PROD && !import.meta.env.VITE_API_BASE_URL;

// A bare client for the refresh call so the interceptors below never recurse.
const bare = axios.create({ baseURL: API_BASE, timeout: 45000 });

const PUBLIC_PATHS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/forgot-password', '/auth/reset-password'];

api.interceptors.request.use((config) => {
  const token = tokens.getAccess();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshing = null;

/** Single-flight token refresh: concurrent 401s share one refresh request. */
export function refreshSession() {
  if (!refreshing) {
    const refreshToken = tokens.getRefresh();
    if (!refreshToken) return Promise.reject(new Error('No refresh token'));
    refreshing = bare
      .post('/auth/refresh', { refreshToken })
      .then((res) => {
        const d = res.data.data;
        tokens.set(d.accessToken, d.refreshToken);
        return d;
      })
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    const isPublic = original && PUBLIC_PATHS.some((p) => original.url?.startsWith(p));
    if (status === 401 && original && !original._retried && !isPublic) {
      original._retried = true;
      try {
        const d = await refreshSession();
        original.headers.Authorization = `Bearer ${d.accessToken}`;
        return api(original);
      } catch {
        tokens.clear();
        tokens.notifyAuthLost();
      }
    }
    return Promise.reject(error);
  },
);

/** Unwraps the ApiResponse envelope. */
export const unwrap = (res) => res.data.data;

export function errorMessage(err, fallback = 'Something went wrong. Please try again.') {
  const body = err?.response?.data;
  if (body?.error?.message) return body.error.message;
  if (body?.message) return body.message;
  if (err?.code === 'ERR_NETWORK') return 'Cannot reach the server. Check your connection.';
  if (err?.code === 'ECONNABORTED') return 'The request timed out. Please try again.';
  return err?.message && !err.response ? err.message : fallback;
}

export function fieldErrors(err) {
  return err?.response?.data?.error?.fieldErrors || {};
}

export function errorCode(err) {
  return err?.response?.data?.error?.code || null;
}

// ----------------------------------------------------------------- endpoints

const get = (url, params) => api.get(url, { params }).then(unwrap);
const post = (url, body) => api.post(url, body).then(unwrap);
const put = (url, body) => api.put(url, body).then(unwrap);

export const AuthApi = {
  register: (b) => post('/auth/register', b),
  login: (b) => post('/auth/login', b),
  logout: (refreshToken) => post('/auth/logout', { refreshToken }),
  me: () => get('/auth/me'),
  forgot: (email) => post('/auth/forgot-password', { email }),
  reset: (token, newPassword) => post('/auth/reset-password', { token, newPassword }),
};

export const UserApi = {
  me: () => get('/users/me'),
  update: (b) => put('/users/me', b),
  changePassword: (b) => post('/users/me/password', b),
  achievements: () => get('/users/me/achievements'),
  profile: (id) => get(`/users/${id}/profile`),
};

export const WalletApi = {
  get: () => get('/wallet'),
  transactions: (params) => get('/wallet/transactions', params),
  purchases: (params) => get('/wallet/purchases', params),
  dailyBonus: () => post('/wallet/daily-bonus'),
};

export const StoreApi = {
  items: () => get('/store/items'),
  inventory: () => get('/store/inventory'),
  purchase: (itemId) => post('/store/purchase', { itemId }),
  equip: (itemId) => post('/store/equip', { itemId }),
  unequip: (category) => post('/store/unequip', { category }),
};

export const RoomApi = {
  list: (params) => get('/rooms', params),
  create: (b) => post('/rooms', b),
  /** Play with Computer: { players: 2|4, difficulty: 'EASY'|'MEDIUM'|'HARD' } -> { room, matchId } */
  createBotGame: (b) => post('/rooms/bot', b),
  get: (id) => get(`/rooms/${id}`),
  active: () => get('/rooms/active'),
  join: (idOrCode) => post(`/rooms/${encodeURIComponent(idOrCode)}/join`),
  leave: (id) => post(`/rooms/${id}/leave`),
  ready: (id, ready) => post(`/rooms/${id}/ready`, { ready }),
};

export const MatchApi = {
  list: (params) => get('/matches', params),
  get: (id) => get(`/matches/${id}`),
  events: (id, params) => get(`/matches/${id}/events`, params),
};

export const LeaderboardApi = {
  top: (params) => get('/leaderboard', params),
  me: () => get('/leaderboard/me'),
};

export const NotificationApi = {
  list: (params) => get('/notifications', params),
  unread: () => get('/notifications/unread-count'),
  read: (id) => put(`/notifications/${id}/read`),
  readAll: () => put('/notifications/read-all'),
};

export const ReportApi = {
  create: (b) => post('/reports', b),
  mine: (params) => get('/reports/mine', params),
};

export const PaymentApi = {
  status: () => get('/payments/status'),
  createOrder: (packageId) => post('/payments/orders', { packageId }),
  verify: (b) => post('/payments/verify', b),
};

export const AdminApi = {
  dashboard: () => get('/admin/dashboard'),
  stats: () => get('/admin/stats'),
  users: (params) => get('/admin/users', params),
  setUserStatus: (id, status, reason) => put(`/admin/users/${id}/status`, { status, reason }),
  setUserRole: (id, role) => put(`/admin/users/${id}/role`, { role }),
  creditUser: (id, amount, reason) => post(`/admin/users/${id}/credit`, { amount, reason }),
  matches: (params) => get('/admin/matches', params),
  matchEvents: (id, params) => get(`/admin/matches/${id}/events`, params),
  transactions: (params) => get('/admin/transactions', params),
  logs: (params) => get('/admin/logs', params),
  storeItems: () => get('/admin/store/items'),
  createStoreItem: (b) => post('/admin/store/items', b),
  updateStoreItem: (id, b) => put(`/admin/store/items/${id}`, b),
  reports: (params) => get('/admin/reports', params),
  updateReport: (id, b) => put(`/admin/reports/${id}`, b),
  settings: () => get('/admin/settings'),
  updateSettings: (b) => put('/admin/settings', b),
};
