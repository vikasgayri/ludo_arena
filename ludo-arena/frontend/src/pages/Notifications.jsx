import { useState } from 'react';
import { CheckCheck } from 'lucide-react';
import { NotificationApi, errorMessage } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import { useNotificationStore } from '../store/notificationStore';
import { toast } from '../store/toastStore';
import { formatDate } from '../lib/format';
import LoadingSkeleton from '../components/LoadingSkeleton';
import { EmptyState, ErrorState, PageHeader, Pagination } from '../components/ui';

export default function Notifications() {
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const { data, loading, error, reload } = useAsync(() => NotificationApi.list({ unreadOnly, page, size: 15 }), [page, unreadOnly]);
  const loadUnread = useNotificationStore((s) => s.loadUnread);

  const read = async (n) => {
    if (n.read) return;
    try { await NotificationApi.read(n.id); await reload(); loadUnread(); } catch (e) { toast.error(errorMessage(e)); }
  };
  const readAll = async () => {
    try { await NotificationApi.readAll(); await reload(); loadUnread(); toast.success('All notifications marked as read'); } catch (e) { toast.error(errorMessage(e)); }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Notifications" actions={<button className="btn-secondary" onClick={readAll}><CheckCheck className="h-4 w-4" aria-hidden />Mark all read</button>} />
      <label className="mb-4 flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-500" checked={unreadOnly} onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0); }} />Unread only</label>
      {loading && !data ? <LoadingSkeleton rows={5} />
        : error ? <ErrorState message={error} onRetry={reload} />
          : !data?.items.length ? <EmptyState title={unreadOnly ? 'No unread notifications' : 'No notifications yet'} message="Match results, rewards and account updates will appear here." />
            : (
              <>
                <ul className="space-y-2">
                  {data.items.map((n) => (
                    <li key={n.id}>
                      <button onClick={() => read(n)} className={`card block w-full p-4 text-left ${n.read ? 'opacity-60' : 'border-brand-400/30'}`}>
                        <p className="flex items-center gap-2 font-semibold">{!n.read && <span className="h-2 w-2 rounded-full bg-brand-400" aria-label="Unread" />}{n.title}</p>
                        <p className="text-sm text-slate-300">{n.message}</p>
                        <p className="mt-1 text-xs text-slate-500">{formatDate(n.createdAt)}</p>
                      </button>
                    </li>
                  ))}
                </ul>
                <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
              </>
            )}
    </div>
  );
}
