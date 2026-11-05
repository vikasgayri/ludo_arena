import { Link, useNavigate } from 'react-router-dom';
import { Bot, Flame, Gamepad2, Percent, Swords, Target, Trophy } from 'lucide-react';
import { LeaderboardApi, MatchApi, RoomApi, WalletApi } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import { useAuthStore } from '../store/authStore';
import { useWalletStore } from '../store/walletStore';
import { toast } from '../store/toastStore';
import { errorMessage } from '../lib/api';
import { useState } from 'react';
import WalletCard from '../components/WalletCard';
import LeaderboardCard from '../components/LeaderboardCard';
import MatchHistoryTable from '../components/MatchHistoryTable';
import { ErrorState, PageHeader, StatCard } from '../components/ui';

export default function Dashboard() {
  const user = useAuthStore((s) => s.user);
  const { wallet, loading: wLoading, error: wError, load, setWallet } = useWalletStore();
  const navigate = useNavigate();
  const [claiming, setClaiming] = useState(false);

  const active = useAsync(() => RoomApi.active(), []);
  const recent = useAsync(() => MatchApi.list({ page: 0, size: 5 }), []);
  const board = useAsync(() => LeaderboardApi.top({ page: 0, size: 5 }), []);

  const claim = async () => {
    setClaiming(true);
    try {
      const w = await WalletApi.dailyBonus();
      setWallet(w);
      toast.success('Daily bonus added to your wallet!');
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setClaiming(false);
    }
  };

  const resume = () => {
    const a = active.data;
    if (!a) return;
    navigate(a.matchId ? `/game/${a.matchId}` : `/room/${a.room.id}`);
  };

  const s = user?.stats;
  return (
    <div>
      <PageHeader
        title={`Hey, ${user?.name ?? 'player'} 👋`}
        subtitle="Ready for your next match?"
        actions={(
          <>
            <Link to="/play" className="btn-primary"><Bot className="h-4 w-4" aria-hidden />Play now</Link>
            <Link to="/rooms/create" className="btn-secondary"><Swords className="h-4 w-4" aria-hidden />Create room</Link>
            <Link to="/lobby" className="btn-secondary"><Gamepad2 className="h-4 w-4" aria-hidden />Browse lobby</Link>
          </>
        )}
      />

      {active.data && (
        <div className="card mb-6 flex flex-wrap items-center justify-between gap-3 border-brand-400/40" role="status">
          <div className="flex items-center gap-3">
            <Flame className="h-6 w-6 text-orange-400" aria-hidden />
            <div>
              <p className="font-semibold">{active.data.matchId ? 'You have a match in progress' : `You're in room “${active.data.room.name}”`}</p>
              <p className="text-xs text-slate-400">{active.data.matchId ? 'Jump back in before your turn times out.' : 'Waiting for players to be ready.'}</p>
            </div>
          </div>
          <button className="btn-primary" onClick={resume}>{active.data.matchId ? 'Resume match' : 'Go to room'}</button>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Matches" value={s?.totalMatches ?? 0} icon={Gamepad2} tone="blue" />
        <StatCard label="Wins" value={s?.wins ?? 0} icon={Trophy} tone="yellow" />
        <StatCard label="Win rate" value={`${s?.winRate ?? 0}%`} icon={Percent} tone="green" />
        <StatCard label="Level" value={s?.level ?? 1} hint={`${s?.xp ?? 0} XP · ${s?.captures ?? 0} captures`} icon={Target} />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <section>
            <div className="mb-2 flex items-center justify-between"><h2 className="font-bold">Recent matches</h2><Link to="/matches" className="text-sm text-brand-400 hover:underline">View all</Link></div>
            <MatchHistoryTable matches={recent.data?.items} loading={recent.loading} error={recent.error} onRetry={recent.reload} />
          </section>
        </div>
        <div className="space-y-6">
          <WalletCard wallet={wallet} loading={wLoading} error={wError} onRetry={load} onClaim={claim} claiming={claiming} compact />
          <LeaderboardCard title="Top players" entries={board.data?.items} loading={board.loading} error={board.error} onRetry={board.reload} highlightUserId={user?.id} />
        </div>
      </div>
      {active.error && !active.data && <div className="mt-6"><ErrorState message={active.error} onRetry={active.reload} /></div>}
    </div>
  );
}
