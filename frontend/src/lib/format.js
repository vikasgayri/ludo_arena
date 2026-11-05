export function formatDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function formatDay(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatNumber(n) {
  if (n == null || Number.isNaN(Number(n))) return '0';
  return Number(n).toLocaleString();
}

export function timeAgo(iso, now = Date.now()) {
  if (!iso) return '';
  const diff = Math.max(0, now - new Date(iso).getTime());
  const s = Math.floor(diff / 1000);
  if (s < 60) return 'just now';
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

export const COLOR_HEX = { RED: '#e53935', GREEN: '#2e9e4f', YELLOW: '#f7b500', BLUE: '#1e78d7' };
/** darker / lighter shades used for the 3D tokens and board gradients */
export const COLOR_DARK = { RED: '#a51d1a', GREEN: '#1b6b34', YELLOW: '#b98500', BLUE: '#0f4f9c' };
export const COLOR_LIGHT = { RED: '#ff8a80', GREEN: '#8be3a8', YELLOW: '#ffe082', BLUE: '#90c2ff' };
export const COLOR_SOFT = { RED: '#fecaca', GREEN: '#bbf7d0', YELLOW: '#fef08a', BLUE: '#bfdbfe' };
export const TX_LABEL = {
  SIGNUP_BONUS: 'Signup bonus', DAILY_BONUS: 'Daily bonus', MATCH_REWARD: 'Match reward', STORE_PURCHASE: 'Store purchase',
  PROMO_CREDIT: 'Promo credit', ADMIN_ADJUSTMENT: 'Admin adjustment', PAYMENT_CREDIT: 'Coin pack',
};
