import { useState } from 'react';
import { WalletApi } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import { TX_LABEL, formatDate, formatNumber } from '../lib/format';
import LoadingSkeleton from '../components/LoadingSkeleton';
import { Badge, EmptyState, ErrorState, PageHeader, Pagination } from '../components/ui';

const TYPES = Object.keys(TX_LABEL);
const TONE = { COMPLETED: 'green', PENDING: 'yellow', FAILED: 'red' };

export default function Transactions() {
  const [type, setType] = useState('');
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useAsync(() => WalletApi.transactions({ type: type || undefined, page, size: 15 }), [type, page]);

  return (
    <div>
      <PageHeader title="Transaction history" subtitle="Every coin movement is recorded in an append-only ledger." />
      <div className="mb-4 max-w-xs">
        <label className="label" htmlFor="tx-type">Type</label>
        <select id="tx-type" className="input" value={type} onChange={(e) => { setType(e.target.value); setPage(0); }}>
          <option value="">All types</option>
          {TYPES.map((t) => <option key={t} value={t}>{TX_LABEL[t]}</option>)}
        </select>
      </div>
      {loading && !data ? <LoadingSkeleton variant="table" rows={6} />
        : error ? <ErrorState message={error} onRetry={reload} />
          : !data?.items.length ? <EmptyState title="No transactions" message={type ? 'Nothing matches this filter.' : 'Your coin activity will appear here.'} />
            : (
              <>
                <div className="overflow-x-auto rounded-2xl border border-white/5">
                  <table className="min-w-full divide-y divide-white/5">
                    <thead className="bg-navy-800"><tr><th className="th">Date</th><th className="th">Type</th><th className="th">Description</th><th className="th text-right">Amount</th><th className="th text-right">Balance</th><th className="th">Status</th></tr></thead>
                    <tbody className="divide-y divide-white/5">
                      {data.items.map((t) => (
                        <tr key={t.id}>
                          <td className="td whitespace-nowrap">{formatDate(t.createdAt)}</td>
                          <td className="td">{TX_LABEL[t.type] || t.type}</td>
                          <td className="td text-slate-400">{t.description}</td>
                          <td className={`td text-right font-bold tabular-nums ${t.amount >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>{t.amount > 0 ? '+' : ''}{formatNumber(t.amount)}</td>
                          <td className="td text-right tabular-nums text-slate-400">{t.newBalance != null ? formatNumber(t.newBalance) : '—'}</td>
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
