import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bell } from 'lucide-react';
import { useNotificationStore } from '../store/notificationStore';
import { timeAgo } from '../lib/format';
import LoadingSkeleton from './LoadingSkeleton';
import { EmptyState, ErrorState } from './ui';

export default function NotificationDropdown() {
  const { items, unread, loading, error, loadUnread, loadRecent, markRead, markAllRead } = useNotificationStore();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    loadUnread();
    const t = setInterval(loadUnread, 60000);
    return () => clearInterval(t);
  }, [loadUnread]);

  useEffect(() => {
    if (!open) return undefined;
    loadRecent();
    const onDoc = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('mousedown', onDoc); document.removeEventListener('keydown', onKey); };
  }, [open, loadRecent]);

  return (
    <div className="relative" ref={ref}>
      <button className="btn-ghost relative p-2" onClick={() => setOpen((o) => !o)} aria-haspopup="true" aria-expanded={open} aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}>
        <Bell className="h-5 w-5" aria-hidden />
        {unread > 0 && <span className="absolute right-0.5 top-0.5 min-w-4 rounded-full bg-red-500 px-1 text-center text-[10px] font-bold leading-4 text-white" data-testid="unread-badge">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <div className="absolute right-0 z-40 mt-2 w-80 max-w-[90vw] animate-pop rounded-2xl border border-white/10 bg-navy-800 p-3 shadow-2xl" role="menu">
          <div className="mb-2 flex items-center justify-between">
            <p className="font-semibold">Notifications</p>
            {unread > 0 && <button className="text-xs text-brand-400 hover:underline" onClick={markAllRead}>Mark all read</button>}
          </div>
          <div className="max-h-80 overflow-y-auto">
            {loading && !items.length ? <LoadingSkeleton rows={3} />
              : error ? <ErrorState message={error} onRetry={loadRecent} />
                : !items.length ? <EmptyState title="All caught up" message="No notifications yet." />
                  : items.map((n) => (
                    <button key={n.id} role="menuitem" onClick={() => markRead(n.id)} className={`block w-full rounded-lg px-3 py-2 text-left hover:bg-white/5 ${n.read ? 'opacity-60' : ''}`}>
                      <p className="flex items-center gap-2 text-sm font-semibold">{!n.read && <span className="h-2 w-2 rounded-full bg-brand-400" />}{n.title}</p>
                      <p className="text-xs text-slate-400">{n.message}</p>
                      <p className="mt-0.5 text-[11px] text-slate-500">{timeAgo(n.createdAt)}</p>
                    </button>
                  ))}
          </div>
          <Link to="/notifications" onClick={() => setOpen(false)} className="mt-2 block rounded-lg py-1.5 text-center text-sm text-brand-400 hover:bg-white/5">View all</Link>
        </div>
      )}
    </div>
  );
}
