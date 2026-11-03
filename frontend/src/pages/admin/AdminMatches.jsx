import { useState } from 'react';
import { AdminApi } from '../../lib/api';
import { useAsync } from '../../lib/useAsync';
import MatchHistoryTable from '../../components/MatchHistoryTable';
import MatchDetailModal from '../../components/MatchDetailModal';
import { PageHeader, Pagination } from '../../components/ui';

export default function AdminMatches() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const { data, loading, error, reload } = useAsync(() => AdminApi.matches({ status: status || undefined, page, size: 15 }), [status, page]);
  return (
    <div>
      <PageHeader title="Match management" subtitle="Every match on the platform, with its full event log" />
      <div className="mb-4 max-w-xs"><label className="label" htmlFor="am-s">Status</label>
        <select id="am-s" className="input" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}><option value="">All</option><option>ACTIVE</option><option>FINISHED</option><option>ABANDONED</option></select></div>
      <MatchHistoryTable admin matches={data?.items} loading={loading} error={error} onRetry={reload} onSelect={setSelected} />
      <Pagination page={data?.page ?? 0} totalPages={data?.totalPages ?? 0} onChange={setPage} />
      <MatchDetailModal admin match={selected} onClose={() => setSelected(null)} />
    </div>
  );
}
