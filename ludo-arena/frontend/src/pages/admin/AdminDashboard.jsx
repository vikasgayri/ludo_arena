import { Activity, Coins, Flag, Gamepad2, ShoppingBag, UserCheck, UserPlus, Users, UserX } from 'lucide-react';
import { AdminApi } from '../../lib/api';
import { useAsync } from '../../lib/useAsync';
import { formatDate, formatNumber } from '../../lib/format';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { EmptyState, ErrorState, PageHeader, StatCard } from '../../components/ui';

export default function AdminDashboard() {
  const { data: d, loading, error, reload } = useAsync(() => AdminApi.dashboard(), []);
  return (
    <div>
      <PageHeader title="Admin dashboard" subtitle="Platform health at a glance" />
      {loading && !d ? <LoadingSkeleton variant="stats" />
        : error ? <ErrorState message={error} onRetry={reload} />
          : (
            <>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <StatCard label="Total users" value={formatNumber(d.totalUsers)} icon={Users} />
                <StatCard label="Active users" value={formatNumber(d.activeUsers)} icon={UserCheck} tone="green" />
                <StatCard label="Suspended" value={formatNumber(d.suspendedUsers)} icon={UserX} tone="red" />
                <StatCard label="New (7 days)" value={formatNumber(d.newUsers7d)} icon={UserPlus} tone="blue" />
                <StatCard label="Online now" value={formatNumber(d.onlineUsers)} icon={Activity} tone="green" />
                <StatCard label="Active matches" value={formatNumber(d.activeMatches)} hint={`${formatNumber(d.totalMatches)} total · ${formatNumber(d.completedMatches)} finished`} icon={Gamepad2} tone="yellow" />
                <StatCard label="Coins in circulation" value={formatNumber(d.coinCirculation)} icon={Coins} tone="yellow" />
                <StatCard label="Store purchases" value={formatNumber(d.storePurchases)} hint={`${formatNumber(d.coinsSpentInStore)} coins spent`} icon={ShoppingBag} />
                <StatCard label="Open reports" value={formatNumber(d.openReports)} icon={Flag} tone="red" />
              </div>
              <section className="card mt-6" aria-label="Recent activity">
                <h2 className="mb-3 font-bold">Recent activity</h2>
                {!d.recentActivity?.length ? <EmptyState title="No recent activity" /> : (
                  <ul className="divide-y divide-white/5">
                    {d.recentActivity.map((a, i) => <li key={i} className="flex justify-between gap-3 py-2 text-sm"><span>{a.message}</span><span className="shrink-0 text-xs text-slate-500">{formatDate(a.at)}</span></li>)}
                  </ul>
                )}
              </section>
            </>
          )}
    </div>
  );
}
