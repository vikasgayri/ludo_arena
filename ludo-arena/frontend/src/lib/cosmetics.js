// Visual definitions keyed by StoreItem.assetKey. The backend stores only the key + ownership; looks live here.
export const BOARD_SKINS = {
  default: { bg: '#f8fafc', track: '#ffffff', line: '#cbd5e1', center: '#1e293b' },
  'board-neon': { bg: '#0f172a', track: '#1e293b', line: '#6366f1', center: '#a78bfa' },
  'board-royal': { bg: '#fef3c7', track: '#fffbeb', line: '#b45309', center: '#7c2d12' },
  'board-forest': { bg: '#dcfce7', track: '#f0fdf4', line: '#15803d', center: '#14532d' },
  'board-ocean': { bg: '#cffafe', track: '#ecfeff', line: '#0e7490', center: '#164e63' },
};

export const DICE_SKINS = {
  default: { face: '#ffffff', pip: '#0f172a', border: '#cbd5e1' },
  'dice-obsidian': { face: '#111827', pip: '#e5e7eb', border: '#374151' },
  'dice-gold': { face: '#fcd34d', pip: '#78350f', border: '#b45309' },
  'dice-neon': { face: '#1e1b4b', pip: '#22d3ee', border: '#6366f1' },
};

export const THEMES = {
  default: 'from-navy-800 to-navy-700',
  'theme-aurora': 'from-emerald-900/70 via-indigo-900/70 to-fuchsia-900/70',
  'theme-sunset': 'from-orange-900/70 via-rose-900/70 to-purple-900/70',
};

export const AVATAR_STYLES = {
  'avatar-crown': { emoji: '👑', bg: '#f59e0b' },
  'avatar-dragon': { emoji: '🐉', bg: '#16a34a' },
  'avatar-rocket': { emoji: '🚀', bg: '#6366f1' },
};

const FREE_EMOJI = ['🦊', '🐼', '🐯', '🐸', '🐙', '🦁', '🐨', '🐵', '🦄', '🐧', '🦉', '🐝'];
const FREE_BG = ['#ef4444', '#f97316', '#eab308', '#22c55e', '#14b8a6', '#3b82f6', '#6366f1', '#8b5cf6', '#d946ef', '#ec4899', '#64748b', '#0ea5e9'];

export const FREE_AVATARS = Array.from({ length: 12 }, (_, i) => `avatar-${String(i + 1).padStart(2, '0')}`);

export function avatarStyle(key) {
  if (key && AVATAR_STYLES[key]) return AVATAR_STYLES[key];
  const m = /^avatar-(\d{2})$/.exec(key || '');
  const i = m ? (parseInt(m[1], 10) - 1) % 12 : 0;
  return { emoji: FREE_EMOJI[i], bg: FREE_BG[i] };
}
