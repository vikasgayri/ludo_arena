import { useState } from 'react';
import { AdminApi } from '../../lib/api';
import { useAsync } from '../../lib/useAsync';
import { TX_LABEL, formatDate, formatNumber } from '../../lib/format';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { Badge, EmptyState, ErrorState, PageHeader, Pagination } from '../../components/ui';

const TONE = { COMPLETED: 'green', PENDING: 'yellow', FAILED: 'red' };

export default function AdminTransactions() {
  const [f, setF] = useState({ userId: '', type: '', status: '' });
  const [applied, setApplied] = useState(f);
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useAsync(() => AdminApi.transactions({ userId: applied.userId || undefined, type: applied.type || undefined, status: applied.status || undefined, page, size: 20 }), [applied, page]);
  return (
    <div>
      <PageHeader title="Transaction monitoring" subtitle="Read-only view of the immutable wallet ledger" />
      <form className="mb-4 flex flex-wrap items-end gap-3" onSubmit={(e) => { e.preventDefault(); setApplied({ ...f, userId: f.userId.trim() }); setPage(0); }}>
        <div className="min-w-[14rem] flex-1"><label className="label" htmlFor="t-u">User ID</label><input id="t-u" className="input" value={f.userId} onChange={(e) => setF({ ...f, userId: e.target.value })} /></div>
        <div><label className="label" htmlFor="t-t">Type</label><select id="t-t" className="input" value={f.type} onChange={(e) => setF({ ...f, type: e.target.value })}><option value="">Any</option>{Object.keys(TX_LABEL).map((t) => <option key={t} value={t}>{TX_LABEL[t]}</option>)}</select></div>
        <div><label className="label" htmlFor="t-s">Status</label><select id="t-s" className="input" value={f.status} onChange={(e) => setF({ ...f, status: e.target.value })}><option value="">Any</option><option>COMPLETED</option><option>PENDING</option><option>FAILED</option></select></div>
        <button className="btn-primary">Apply</button>
      </form>
      {loading && !data ? <LoadingSkeleton variant="table" rows={6} />
        : error ? <ErrorState message={error} onRetry={reload} />
          : !data?.items.length ? <EmptyState title="No transactions match" />
            : (
              <>
                <div className="overflow-x-auto rounded-2xl border border-white/5">
                  <table className="min-w-full divide-y divide-white/5">
                    <thead className="bg-navy-800"><tr><th className="th">Date</th><th className="th">User</th><th className="th">Type</th><th className="th text-right">Amount</th><th className="th text-right">Prev → New</th><th className="th">Reference</th><th className="th">Status</th></tr></thead>
                    <tbody className="divide-y divide-white/5">
                      {data.items.map((t) => (
                        <tr key={t.id}>
                          <td className="td whitespace-nowrap">{formatDate(t.createdAt)}</td>
                          <td className="td font-mono text-xs">{t.userId}</td>
                          <td className="td">{TX_LABEL[t.type] || t.type}</td>
                          <td className={`td text-right font-bold tabular-nums ${t.amount >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>{t.amount > 0 ? '+' : ''}{formatNumber(t.amount)}</td>
                          <td className="td whitespace-nowrap text-right tabular-nums text-slate-400">{t.previousBalance ?? '—'} → {t.newBalance ?? '—'}</td>
                          <td className="td max-w-[12rem] truncate font-mono text-xs text-slate-500" title={t.referenceId}>{t.referenceId}</td>
                          <td className="td"><Badge tone={TONE[t.status] || 'gray'}>{t.status}</Badge></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
              </>
            )}
    </div>
  );
}
