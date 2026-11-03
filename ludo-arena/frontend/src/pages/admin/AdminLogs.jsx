import { useState } from 'react';
import { AdminApi } from '../../lib/api';
import { useAsync } from '../../lib/useAsync';
import { formatDate } from '../../lib/format';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { EmptyState, ErrorState, PageHeader, Pagination } from '../../components/ui';

export default function AdminLogs() {
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useAsync(() => AdminApi.logs({ page, size: 25 }), [page]);
  return (
    <div>
      <PageHeader title="Admin activity logs" subtitle="Every privileged action is recorded and cannot be edited" />
      {loading && !data ? <LoadingSkeleton variant="table" rows={8} />
        : error ? <ErrorState message={error} onRetry={reload} />
          : !data?.items.length ? <EmptyState title="No admin activity yet" />
            : (
              <>
                <div className="overflow-x-auto rounded-2xl border border-white/5">
                  <table className="min-w-full divide-y divide-white/5">
                    <thead className="bg-navy-800"><tr><th className="th">When</th><th className="th">Admin</th><th className="th">Action</th><th className="th">Target</th><th className="th">Details</th></tr></thead>
                    <tbody className="divide-y divide-white/5">
                      {data.items.map((l) => (
                        <tr key={l.id}>
                          <td className="td whitespace-nowrap">{formatDate(l.createdAt)}</td>
                          <td className="td">{l.adminEmail}</td>
                          <td className="td font-mono text-xs">{l.action}</td>
                          <td className="td text-xs text-slate-400">{l.targetType}{l.targetId ? ` · ${l.targetId}` : ''}</td>
                          <td className="td max-w-xs truncate font-mono text-xs text-slate-500" title={JSON.stringify(l.details)}>{l.details ? JSON.stringify(l.details) : ''}</td>
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
