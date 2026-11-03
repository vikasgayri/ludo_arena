import { Award, Lock } from 'lucide-react';
import { UserApi } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import LoadingSkeleton from '../components/LoadingSkeleton';
import { EmptyState, ErrorState, PageHeader } from '../components/ui';

export default function Achievements() {
  const { data, loading, error, reload } = useAsync(() => UserApi.achievements(), []);
  const unlocked = data?.filter((a) => a.unlocked).length ?? 0;
  return (
    <div>
      <PageHeader title="Achievements" subtitle={data ? `${unlocked} of ${data.length} unlocked` : 'Milestones earned by playing'} />
      {loading && !data ? <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{Array.from({ length: 6 }).map((_, i) => <LoadingSkeleton key={i} variant="card" />)}</div>
        : error ? <ErrorState message={error} onRetry={reload} />
          : !data?.length ? <EmptyState title="No achievements available" />
            : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {data.map((a) => {
                  const pct = Math.min(100, Math.round((a.progress / Math.max(1, a.target)) * 100));
                  return (
                    <article key={a.key} className={`card ${a.unlocked ? 'border-yellow-300/30' : 'opacity-80'}`} data-testid="achievement">
                      <div className="flex items-start gap-3">
                        <div className={`rounded-xl p-2.5 ${a.unlocked ? 'bg-yellow-300/15 text-yellow-300' : 'bg-white/5 text-slate-500'}`}>{a.unlocked ? <Award className="h-6 w-6" aria-hidden /> : <Lock className="h-6 w-6" aria-hidden />}</div>
                        <div><h3 className="font-bold">{a.title}</h3><p className="text-xs text-slate-400">{a.description}</p></div>
                      </div>
                      <div className="mt-3" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label={`${a.title} progress`}>
                        <div className="h-2 overflow-hidden rounded-full bg-navy-700"><div className="h-full rounded-full bg-gradient-to-r from-brand-500 to-accent" style={{ width: `${pct}%` }} /></div>
                        <p className="mt-1 text-right text-xs text-slate-500">{Math.min(a.progress, a.target)} / {a.target}</p>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
    </div>
  );
}
