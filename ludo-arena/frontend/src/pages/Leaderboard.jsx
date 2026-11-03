import { useState } from 'react';
import { LeaderboardApi } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import { useAuthStore } from '../store/authStore';
import LeaderboardCard from '../components/LeaderboardCard';
import { PageHeader, Pagination } from '../components/ui';

export default function Leaderboard() {
  const user = useAuthStore((s) => s.user);
  const [page, setPage] = useState(0);
  const board = useAsync(() => LeaderboardApi.top({ page, size: 20 }), [page]);
  const mine = useAsync(() => LeaderboardApi.me(), []);
  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Leaderboard" subtitle="Ranked by experience points earned in matches" />
      {mine.data && (
        <div className="card mb-4 flex items-center justify-between border-brand-400/40">
          <span className="text-sm text-slate-300">Your rank</span>
          <span className="text-xl font-extrabold">#{mine.data.rank} <span className="text-sm font-normal text-slate-400">· {mine.data.xp} XP</span></span>
        </div>
      )}
      <LeaderboardCard entries={board.data?.items} loading={board.loading} error={board.error} onRetry={board.reload} highlightUserId={user?.id} title="Top players" />
      <Pagination page={board.data?.page ?? 0} totalPages={board.data?.totalPages ?? 0} onChange={setPage} />
    </div>
  );
}
