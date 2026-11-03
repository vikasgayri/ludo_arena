import { Link, useNavigate } from 'react-router-dom';
import { Coins, LogOut, Menu, Dices } from 'lucide-react';
import { useAuthStore } from '../store/authStore';
import { useWalletStore } from '../store/walletStore';
import { formatNumber } from '../lib/format';
import NotificationDropdown from './NotificationDropdown';
import { Avatar } from './ui';

export default function Navbar({ onMenu, admin = false }) {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const wallet = useWalletStore((s) => s.wallet);
  const navigate = useNavigate();

  const signOut = async () => { await logout(); navigate('/login'); };

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-white/5 bg-navy-900/90 px-4 backdrop-blur">
      <button className="btn-ghost p-2 lg:hidden" onClick={onMenu} aria-label="Open menu"><Menu className="h-5 w-5" /></button>
      <Link to={admin ? '/admin' : '/dashboard'} className="flex items-center gap-2 font-extrabold tracking-tight">
        <Dices className="h-6 w-6 text-brand-400" aria-hidden />
        <span>LUDO<span className="text-brand-400"> ARENA</span></span>
        {admin && <span className="badge bg-red-500/20 text-red-300">Admin</span>}
      </Link>
      <div className="flex-1" />
      {wallet && (
        <Link to="/wallet" className="hidden items-center gap-1.5 rounded-full bg-white/5 px-3 py-1 text-sm font-semibold sm:flex" aria-label="Wallet balance">
          <Coins className="h-4 w-4 text-yellow-300" aria-hidden /><span data-testid="nav-balance">{formatNumber(wallet.coinBalance)}</span>
        </Link>
      )}
      <NotificationDropdown />
      {user && (
        <Link to="/profile" className="flex items-center gap-2" aria-label="Profile">
          <Avatar avatar={user.avatar} name={user.name} size={32} />
          <span className="hidden max-w-[8rem] truncate text-sm font-medium md:block">{user.name}</span>
        </Link>
      )}
      <button className="btn-ghost p-2" onClick={signOut} aria-label="Sign out"><LogOut className="h-5 w-5" /></button>
    </header>
  );
}
