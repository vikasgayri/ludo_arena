import { Coins, Gift, ShieldCheck } from 'lucide-react';
import { formatNumber } from '../lib/format';
import LoadingSkeleton from './LoadingSkeleton';
import { ErrorState } from './ui';

export default function WalletCard({ wallet, loading, error, onRetry, onClaim, claiming, compact = false }) {
  if (loading && !wallet) return <LoadingSkeleton variant="card" />;
  if (error && !wallet) return <ErrorState message={error} onRetry={onRetry} />;
  if (!wallet) return null;
  return (
    <section className="card bg-gradient-to-br from-brand-600/30 via-navy-800 to-navy-800" aria-label="Wallet">
      <div className="flex items-center justify-between">
        <p className="text-xs uppercase tracking-wide text-slate-400">Coin balance</p>
        <Coins className="h-5 w-5 text-yellow-300" aria-hidden />
      </div>
      <p className="mt-1 text-4xl font-extrabold tabular-nums" data-testid="coin-balance">{formatNumber(wallet.coinBalance)}</p>
      <p className="mt-2 flex items-start gap-1.5 text-xs text-slate-400">
        <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-400" aria-hidden />
        {compact ? 'Virtual coins — not redeemable for cash.' : wallet.notice}
      </p>
      {onClaim && (
        <button className="btn-primary mt-4 w-full" onClick={onClaim} disabled={!wallet.dailyBonusAvailable || claiming}>
          <Gift className="h-4 w-4" aria-hidden />
          {claiming ? 'Claiming…' : wallet.dailyBonusAvailable ? `Claim daily bonus (+${formatNumber(wallet.dailyBonusCoins)})` : 'Daily bonus claimed'}
        </button>
      )}
    </section>
  );
}
