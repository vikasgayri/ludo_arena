import { useState } from 'react';
import { MatchApi } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import MatchHistoryTable from '../components/MatchHistoryTable';
import MatchDetailModal from '../components/MatchDetailModal';
import { PageHeader, Pagination } from '../components/ui';

export default function MatchHistory() {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('');
  const [selected, setSelected] = useState(null);
  const { data, loading, error, reload } = useAsync(() => MatchApi.list({ status: status || undefined, page, size: 15 }), [status, page]);
  return (
    <div>
      <PageHeader title="Match history" subtitle="Your past and in-progress matches" />
      <div className="mb-4 max-w-xs">
        <label className="label" htmlFor="m-status">Status</label>
        <select id="m-status" className="input" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All</option><option value="ACTIVE">Active</option><option value="FINISHED">Finished</option><option value="ABANDONED">Abandoned</option>
        </select>
      </div>
      <MatchHistoryTable matches={data?.items} loading={loading} error={error} onRetry={reload} onSelect={setSelected} />
      <Pagination page={data?.page ?? 0} totalPages={data?.totalPages ?? 0} onChange={setPage} />
      <MatchDetailModal match={selected} onClose={() => setSelected(null)} />
    </div>
  );
}
