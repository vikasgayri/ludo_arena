import { Trophy } from 'lucide-react';
import { formatNumber } from '../lib/format';
import LoadingSkeleton from './LoadingSkeleton';
import { Avatar, EmptyState, ErrorState } from './ui';

const MEDAL = { 1: 'text-yellow-300', 2: 'text-slate-300', 3: 'text-orange-400' };

export default function LeaderboardCard({ entries, loading, error, onRetry, highlightUserId, title = 'Leaderboard', limit }) {
  const rows = limit ? entries?.slice(0, limit) : entries;
  return (
    <section className="card" aria-label={title}>
      <h2 className="mb-3 flex items-center gap-2 font-bold"><Trophy className="h-5 w-5 text-yellow-300" aria-hidden />{title}</h2>
      {loading && !entries ? <LoadingSkeleton rows={5} />
        : error ? <ErrorState message={error} onRetry={onRetry} />
          : !rows?.length ? <EmptyState title="No ranked players yet" message="Play a match to appear here." />
            : (
              <ol className="divide-y divide-white/5">
                {rows.map((e) => (
                  <li key={e.userId} className={`flex items-center gap-3 py-2.5 ${e.userId === highlightUserId ? 'rounded-lg bg-brand-500/10 px-2' : ''}`}>
                    <span className={`w-7 text-center text-sm font-bold tabular-nums ${MEDAL[e.rank] || 'text-slate-400'}`}>{e.rank}</span>
                    <Avatar avatar={e.avatar} name={e.name} size={32} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">{e.name}</p>
                      <p className="text-xs text-slate-400">Lv {e.level} · {e.wins}W / {e.totalMatches}M · {e.winRate}%</p>
                    </div>
                    <span className="text-sm font-bold tabular-nums text-brand-400">{formatNumber(e.xp)} XP</span>
                  </li>
                ))}
              </ol>
            )}
    </section>
  );
}
