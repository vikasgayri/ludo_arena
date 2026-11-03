import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowDownLeft, ArrowUpRight, Receipt } from 'lucide-react';
import { WalletApi, errorMessage } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import { useWalletStore } from '../store/walletStore';
import { toast } from '../store/toastStore';
import { TX_LABEL, formatDate, formatNumber } from '../lib/format';
import WalletCard from '../components/WalletCard';
import LoadingSkeleton from '../components/LoadingSkeleton';
import { EmptyState, ErrorState, PageHeader } from '../components/ui';

export default function Wallet() {
  const { wallet, loading, error, load, setWallet } = useWalletStore();
  const [claiming, setClaiming] = useState(false);
  const txs = useAsync(() => WalletApi.transactions({ page: 0, size: 5 }), []);

  const claim = async () => {
    setClaiming(true);
    try {
      setWallet(await WalletApi.dailyBonus());
      toast.success('Daily bonus claimed!');
      txs.reload();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setClaiming(false);
    }
  };

  return (
    <div>
      <PageHeader title="Wallet" subtitle="Your virtual coin balance" actions={<Link to="/store" className="btn-primary">Visit store</Link>} />
      <div className="grid gap-6 lg:grid-cols-3">
        <WalletCard wallet={wallet} loading={loading} error={error} onRetry={load} onClaim={claim} claiming={claiming} />
        <section className="card lg:col-span-2" aria-label="Recent transactions">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="flex items-center gap-2 font-bold"><Receipt className="h-5 w-5 text-brand-400" aria-hidden />Recent activity</h2>
            <Link to="/transactions" className="text-sm text-brand-400 hover:underline">All transactions</Link>
          </div>
          {txs.loading && !txs.data ? <LoadingSkeleton rows={4} />
            : txs.error ? <ErrorState message={txs.error} onRetry={txs.reload} />
              : !txs.data?.items.length ? <EmptyState title="No transactions yet" message="Claim your daily bonus or win a match to earn coins." />
                : (
                  <ul className="divide-y divide-white/5">
                    {txs.data.items.map((t) => (
                      <li key={t.id} className="flex items-center gap-3 py-2.5">
                        {t.amount >= 0 ? <ArrowDownLeft className="h-5 w-5 text-emerald-400" aria-label="Credit" /> : <ArrowUpRight className="h-5 w-5 text-red-400" aria-label="Debit" />}
                        <div className="min-w-0 flex-1"><p className="truncate text-sm font-medium">{t.description || TX_LABEL[t.type] || t.type}</p><p className="text-xs text-slate-500">{formatDate(t.createdAt)}</p></div>
                        <span className={`font-bold tabular-nums ${t.amount >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>{t.amount > 0 ? '+' : ''}{formatNumber(t.amount)}</span>
                      </li>
                    ))}
                  </ul>
                )}
        </section>
      </div>
    </div>
  );
}
