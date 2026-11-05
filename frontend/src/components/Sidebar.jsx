import { NavLink } from 'react-router-dom';
import {
  Award, BarChart3, Bot, Bell, Coins, Flag, Gamepad2, History, LayoutDashboard, LogIn, Receipt, ScrollText, Settings, ShoppingBag,
  Shield, Swords, Trophy, User, Users, ListChecks, SlidersHorizontal, Store, X,
} from 'lucide-react';

export const USER_LINKS = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/play', label: 'Play', icon: Bot },
  { to: '/lobby', label: 'Lobby', icon: Gamepad2 },
  { to: '/rooms/create', label: 'Create Room', icon: Swords },
  { to: '/rooms/join', label: 'Join Room', icon: LogIn },
  { to: '/wallet', label: 'Wallet', icon: Coins },
  { to: '/store', label: 'Coin Store', icon: ShoppingBag },
  { to: '/transactions', label: 'Transactions', icon: Receipt },
  { to: '/matches', label: 'Match History', icon: History },
  { to: '/leaderboard', label: 'Leaderboard', icon: Trophy },
  { to: '/achievements', label: 'Achievements', icon: Award },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/profile', label: 'Profile', icon: User },
  { to: '/settings', label: 'Settings', icon: Settings },
];

export const ADMIN_LINKS = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/matches', label: 'Matches', icon: Gamepad2 },
  { to: '/admin/transactions', label: 'Transactions', icon: Receipt },
  { to: '/admin/stats', label: 'Game Statistics', icon: BarChart3 },
  { to: '/admin/store', label: 'Store', icon: Store },
  { to: '/admin/reports', label: 'Reports', icon: Flag },
  { to: '/admin/logs', label: 'Activity Logs', icon: ScrollText },
  { to: '/admin/settings', label: 'Platform Settings', icon: SlidersHorizontal },
];

export default function Sidebar({ open, onClose, admin = false, isAdminUser = false }) {
  const links = admin ? ADMIN_LINKS : USER_LINKS;
  return (
    <>
      {open && <div className="fixed inset-0 z-30 bg-black/60 lg:hidden" onClick={onClose} aria-hidden />}
      <aside className={`fixed inset-y-0 left-0 z-40 w-64 transform overflow-y-auto border-r border-white/5 bg-navy-900 p-3 pt-16 transition-transform lg:static lg:z-0 lg:translate-x-0 lg:pt-3 ${open ? 'translate-x-0' : '-translate-x-full'}`} aria-label="Sidebar">
        <button className="btn-ghost absolute right-3 top-3 p-2 lg:hidden" onClick={onClose} aria-label="Close menu"><X className="h-5 w-5" /></button>
        <nav className="flex flex-col gap-1">
          {links.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              onClick={onClose}
              className={({ isActive }) => `flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition ${isActive ? 'bg-brand-500/20 text-brand-400' : 'text-slate-300 hover:bg-white/5'}`}
            >
              <Icon className="h-4 w-4" aria-hidden />{label}
            </NavLink>
          ))}
          {!admin && isAdminUser && (
            <NavLink to="/admin" className="mt-3 flex items-center gap-3 rounded-xl border border-red-500/20 px-3 py-2 text-sm font-medium text-red-300 hover:bg-red-500/10" onClick={onClose}>
              <Shield className="h-4 w-4" aria-hidden />Admin panel
            </NavLink>
          )}
          {admin && (
            <NavLink to="/dashboard" className="mt-3 flex items-center gap-3 rounded-xl px-3 py-2 text-sm text-slate-400 hover:bg-white/5" onClick={onClose}>
              <ListChecks className="h-4 w-4" aria-hidden />Back to game
            </NavLink>
          )}
        </nav>
      </aside>
    </>
  );
}
