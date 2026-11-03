import { create } from 'zustand';

let id = 0;

export const useToastStore = create((set, get) => ({
  toasts: [],
  push(type, message, ttl = 4500) {
    id += 1;
    const toast = { id, type, message };
    set((s) => ({ toasts: [...s.toasts.slice(-4), toast] }));
    if (ttl > 0) setTimeout(() => get().dismiss(toast.id), ttl);
    return toast.id;
  },
  dismiss(toastId) {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== toastId) }));
  },
}));

export const toast = {
  success: (m) => useToastStore.getState().push('success', m),
  error: (m) => useToastStore.getState().push('error', m, 6500),
  info: (m) => useToastStore.getState().push('info', m),
};
