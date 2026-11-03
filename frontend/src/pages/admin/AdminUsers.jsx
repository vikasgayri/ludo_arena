import { useState } from 'react';
import { Search } from 'lucide-react';
import { AdminApi, errorMessage } from '../../lib/api';
import { useAsync } from '../../lib/useAsync';
import { useAuthStore } from '../../store/authStore';
import { toast } from '../../store/toastStore';
import { formatDate, formatNumber } from '../../lib/format';
import Modal from '../../components/Modal';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { Badge, EmptyState, ErrorState, Field, PageHeader, Pagination } from '../../components/ui';

export default function AdminUsers() {
  const me = useAuthStore((s) => s.user);
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [role, setRole] = useState('');
  const [page, setPage] = useState(0);
  const [action, setAction] = useState(null); // { kind, user }
  const { data, loading, error, reload } = useAsync(() => AdminApi.users({ q: query || undefined, status: status || undefined, role: role || undefined, page, size: 15 }), [query, status, role, page]);
  const superAdmin = me?.role === 'SUPER_ADMIN';

  return (
    <div>
      <PageHeader title="User management" subtitle="Search, suspend, and manage accounts" />
      <form className="mb-4 flex flex-wrap items-end gap-3" onSubmit={(e) => { e.preventDefault(); setQuery(q.trim()); setPage(0); }} role="search">
        <div className="min-w-[14rem] flex-1"><label className="label" htmlFor="u-q">Search</label><input id="u-q" className="input" placeholder="Name or email" value={q} onChange={(e) => setQ(e.target.value)} /></div>
        <div><label className="label" htmlFor="u-s">Status</label><select id="u-s" className="input" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}><option value="">Any</option><option>ACTIVE</option><option>SUSPENDED</option></select></div>
        <div><label className="label" htmlFor="u-r">Role</label><select id="u-r" className="input" value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}><option value="">Any</option><option>USER</option><option>ADMIN</option><option>SUPER_ADMIN</option></select></div>
        <button className="btn-primary"><Search className="h-4 w-4" aria-hidden />Search</button>
      </form>
      {loading && !data ? <LoadingSkeleton variant="table" rows={6} />
        : error ? <ErrorState message={error} onRetry={reload} />
          : !data?.items.length ? <EmptyState title="No users found" message="Try different filters." />
            : (
              <>
                <div className="overflow-x-auto rounded-2xl border border-white/5">
                  <table className="min-w-full divide-y divide-white/5">
                    <thead className="bg-navy-800"><tr><th className="th">User</th><th className="th">Role</th><th className="th">Status</th><th className="th text-right">Coins</th><th className="th text-right">W / M</th><th className="th">Last login</th><th className="th" /></tr></thead>
                    <tbody className="divide-y divide-white/5">
                      {data.items.map((u) => (
                        <tr key={u.id}>
                          <td className="td"><p className="font-medium">{u.name}</p><p className="text-xs text-slate-500">{u.email}</p></td>
                          <td className="td"><Badge tone={u.role === 'USER' ? 'gray' : 'purple'}>{u.role}</Badge></td>
                          <td className="td"><Badge tone={u.status === 'ACTIVE' ? 'green' : 'red'}>{u.status}</Badge></td>
                          <td className="td text-right tabular-nums">{formatNumber(u.coinBalance)}</td>
                          <td className="td text-right tabular-nums">{u.stats.wins} / {u.stats.totalMatches}</td>
                          <td className="td whitespace-nowrap text-slate-400">{formatDate(u.lastLoginAt)}</td>
                          <td className="td whitespace-nowrap text-right">
                            {u.id !== me?.id && (
                              <>
                                <button className="btn-ghost px-2 py-1" onClick={() => setAction({ kind: 'status', user: u })}>{u.status === 'ACTIVE' ? 'Suspend' : 'Reinstate'}</button>
                                {superAdmin && <button className="btn-ghost px-2 py-1" onClick={() => setAction({ kind: 'role', user: u })}>Role</button>}
                                {superAdmin && <button className="btn-ghost px-2 py-1" onClick={() => setAction({ kind: 'credit', user: u })}>Coins</button>}
                              </>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
              </>
            )}
      <UserActionModal action={action} onClose={() => setAction(null)} onDone={() => { setAction(null); reload(); }} />
    </div>
  );
}

function UserActionModal({ action, onClose, onDone }) {
  const [reason, setReason] = useState('');
  const [role, setRole] = useState('USER');
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  if (!action) return null;
  const { kind, user } = action;

  const submit = async () => {
    setError(null);
    setBusy(true);
    try {
      if (kind === 'status') {
        const next = user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
        if (next === 'SUSPENDED' && !reason.trim()) { setError('A reason is required to suspend'); setBusy(false); return; }
        await AdminApi.setUserStatus(user.id, next, reason.trim() || undefined);
      } else if (kind === 'role') {
        await AdminApi.setUserRole(user.id, role);
      } else {
        const n = parseInt(amount, 10);
        if (!Number.isFinite(n) || n === 0 || Math.abs(n) > 100000) { setError('Enter a non-zero whole number up to ±100,000'); setBusy(false); return; }
        if (!reason.trim()) { setError('A reason is required'); setBusy(false); return; }
        await AdminApi.creditUser(user.id, n, reason.trim());
      }
      toast.success('Done');
      setReason(''); setAmount('');
      onDone();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const title = kind === 'status' ? (user.status === 'ACTIVE' ? `Suspend ${user.name}` : `Reinstate ${user.name}`) : kind === 'role' ? `Change role — ${user.name}` : `Adjust coins — ${user.name}`;
  return (
    <Modal open title={title} onClose={onClose} footer={<><button className="btn-secondary" onClick={onClose}>Cancel</button><button className="btn-primary" disabled={busy} onClick={submit}>{busy ? 'Saving…' : 'Confirm'}</button></>}>
      <div className="space-y-3">
        {kind === 'role' && <Field label="Role" htmlFor="a-role"><select id="a-role" className="input" value={role} onChange={(e) => setRole(e.target.value)}><option>USER</option><option>ADMIN</option><option>SUPER_ADMIN</option></select></Field>}
        {kind === 'credit' && <Field label="Amount (negative to deduct)" htmlFor="a-amt"><input id="a-amt" className="input" inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value)} /></Field>}
        {kind !== 'role' && <Field label={kind === 'status' && user.status !== 'ACTIVE' ? 'Note (optional)' : 'Reason'} htmlFor="a-reason"><input id="a-reason" className="input" maxLength={200} value={reason} onChange={(e) => setReason(e.target.value)} /></Field>}
        {error && <p role="alert" className="text-sm text-red-300">{error}</p>}
      </div>
    </Modal>
  );
}
