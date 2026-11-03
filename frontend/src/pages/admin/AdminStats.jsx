import { AdminApi } from '../../lib/api';
import { useAsync } from '../../lib/useAsync';
import { formatNumber } from '../../lib/format';
import LeaderboardCard from '../../components/LeaderboardCard';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { ErrorState, PageHeader, StatCard } from '../../components/ui';

function Bars({ title, rows, color }) {
  const max = Math.max(1, ...rows.map((r) => r.count));
  return (
    <section className="card" aria-label={title}>
      <h2 className="mb-3 font-bold">{title}</h2>
      {rows.length === 0 ? <p className="text-sm text-slate-500">No data in this period.</p> : (
        <div className="flex h-40 items-end gap-1.5" role="img" aria-label={`${title} bar chart`}>
          {rows.map((r) => (
            <div key={r.date} className="flex flex-1 flex-col items-center justify-end gap-1" title={`${r.date}: ${r.count}`}>
              <span className="text-[10px] text-slate-400">{r.count}</span>
              <div className="w-full rounded-t" style={{ height: `${Math.max(3, (r.count / max) * 100)}%`, background: color }} />
              <span className="text-[10px] text-slate-500">{r.date.slice(5)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

export default function AdminStats() {
  const { data: d, loading, error, reload } = useAsync(() => AdminApi.stats(), []);
  return (
    <div>
      <PageHeader title="Game statistics" />
      {loading && !d ? <LoadingSkeleton variant="page" />
        : error ? <ErrorState message={error} onRetry={reload} />
          : (
            <div className="space-y-6">
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <StatCard label="2-player matches" value={formatNumber(d.twoPlayerMatches)} />
                <StatCard label="4-player matches" value={formatNumber(d.fourPlayerMatches)} tone="blue" />
                <StatCard label="Abandoned" value={formatNumber(d.abandonedMatches)} tone="red" />
                <StatCard label="Avg events / match" value={d.averageEventsPerMatch.toFixed(1)} tone="green" />
              </div>
              <div className="grid gap-6 lg:grid-cols-2">
                <Bars title="Matches per day" rows={d.matchesPerDay} color="#7c5cff" />
                <Bars title="Signups per day" rows={d.signupsPerDay} color="#3b82f6" />
              </div>
              <LeaderboardCard title="Top players" entries={d.topPlayers} limit={10} />
            </div>
          )}
    </div>
  );
}
