import { create } from 'zustand';
import { NotificationApi, errorMessage } from '../lib/api';

export const useNotificationStore = create((set, get) => ({
  items: [],
  unread: 0,
  loading: false,
  error: null,

  async loadUnread() {
    try {
      const { unread } = await NotificationApi.unread();
      set({ unread });
    } catch { /* non-critical */ }
  },

  async loadRecent() {
    set({ loading: true, error: null });
    try {
      const page = await NotificationApi.list({ page: 0, size: 10 });
      set({ items: page.items, loading: false });
    } catch (e) {
      set({ loading: false, error: errorMessage(e) });
    }
  },

  async markRead(id) {
    const prev = get().items;
    set({
      items: prev.map((n) => (n.id === id ? { ...n, read: true } : n)),
      unread: Math.max(0, get().unread - (prev.find((n) => n.id === id && !n.read) ? 1 : 0)),
    });
    try { await NotificationApi.read(id); } catch { set({ items: prev }); get().loadUnread(); }
  },

  async markAllRead() {
    const prev = get().items;
    set({ items: prev.map((n) => ({ ...n, read: true })), unread: 0 });
    try { await NotificationApi.readAll(); } catch { set({ items: prev }); get().loadUnread(); }
  },

  reset() { set({ items: [], unread: 0, loading: false, error: null }); },
}));
