import { useEffect, useState } from 'react';
import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { Dices } from 'lucide-react';
import { useAuthStore } from '../store/authStore';
import { useWalletStore } from '../store/walletStore';
import { useNotificationStore } from '../store/notificationStore';
import { useGameStore } from '../store/gameStore';
import Navbar from './Navbar';
import Sidebar from './Sidebar';
import LoadingSkeleton from './LoadingSkeleton';

function FullPageLoader() {
  return <div className="mx-auto max-w-3xl p-8"><LoadingSkeleton variant="page" /></div>;
}

/** Redirects anonymous visitors to /login, remembering where they were going. */
export function RequireAuth({ children }) {
  const status = useAuthStore((s) => s.status);
  const location = useLocation();
  if (status === 'idle' || status === 'loading') return <FullPageLoader />;
  if (status !== 'authenticated') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return children;
}

/** UX gate only: the server enforces admin access on every request. */
export function RequireAdmin({ children }) {
  const user = useAuthStore((s) => s.user);
  if (user?.role !== 'ADMIN' && user?.role !== 'SUPER_ADMIN') return <Navigate to="/dashboard" replace />;
  return children;
}

export function GuestOnly({ children }) {
  const status = useAuthStore((s) => s.status);
  if (status === 'idle' || status === 'loading') return <FullPageLoader />;
  if (status === 'authenticated') return <Navigate to="/dashboard" replace />;
  return children;
}

export function AppLayout({ admin = false }) {
  const [open, setOpen] = useState(false);
  const user = useAuthStore((s) => s.user);
  const loadWallet = useWalletStore((s) => s.load);
  const disconnect = useGameStore((s) => s.disconnect);
  const location = useLocation();

  useEffect(() => { loadWallet(); }, [loadWallet, user?.id]);
  useEffect(() => () => {
    // Leaving the authenticated shell (logout) drops sockets and per-user caches.
    disconnect();
    useWalletStore.getState().reset();
    useNotificationStore.getState().reset();
  }, [disconnect]);
  useEffect(() => { setOpen(false); }, [location.pathname]);

  const isAdminUser = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
  return (
    <div className="flex min-h-full flex-col">
      <Navbar onMenu={() => setOpen(true)} admin={admin} />
      <div className="flex flex-1">
        <Sidebar open={open} onClose={() => setOpen(false)} admin={admin} isAdminUser={isAdminUser} />
        <main className="min-w-0 flex-1 p-4 sm:p-6 lg:p-8"><div className="mx-auto max-w-6xl"><Outlet /></div></main>
      </div>
    </div>
  );
}

export function PublicLayout() {
  return (
    <div className="flex min-h-full flex-col">
      <header className="flex h-14 items-center justify-between px-4 sm:px-8">
        <Link to="/" className="flex items-center gap-2 font-extrabold tracking-tight">
          <Dices className="h-6 w-6 text-brand-400" aria-hidden />LUDO<span className="text-brand-400">ARENA</span>
        </Link>
        <div className="flex gap-2">
          <Link to="/login" className="btn-ghost">Sign in</Link>
          <Link to="/register" className="btn-primary">Play free</Link>
        </div>
      </header>
      <main className="flex-1"><Outlet /></main>
      <footer className="px-4 py-6 text-center text-xs text-slate-500">
        Ludo Arena uses virtual coins for entertainment only. Coins have no cash value and cannot be withdrawn or wagered.
      </footer>
    </div>
  );
}
