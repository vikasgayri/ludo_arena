import { useState } from 'react';
import { Check, Coins, Info, Lock, Sparkles } from 'lucide-react';
import { PaymentApi, StoreApi, errorMessage } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import { useAuthStore } from '../store/authStore';
import { useWalletStore } from '../store/walletStore';
import { toast } from '../store/toastStore';
import { BOARD_SKINS } from '../lib/cosmetics';
import { formatNumber } from '../lib/format';
import { ConfirmModal } from '../components/Modal';
import Dice from '../components/Dice';
import LoadingSkeleton from '../components/LoadingSkeleton';
import { Avatar, Badge, EmptyState, ErrorState, PageHeader } from '../components/ui';

const CATS = [['BOARD_SKIN', 'Board skins'], ['DICE_SKIN', 'Dice skins'], ['AVATAR', 'Avatars'], ['PROFILE_THEME', 'Profile themes']];
const RARITY = { common: 'gray', rare: 'blue', epic: 'purple', legendary: 'yellow' };

function Preview({ item }) {
  if (item.category === 'DICE_SKIN') return <Dice value={5} skin={item.assetKey} size={56} />;
  if (item.category === 'AVATAR') return <Avatar avatar={item.assetKey} name={item.name} size={56} />;
  if (item.category === 'BOARD_SKIN') {
    const b = BOARD_SKINS[item.assetKey] || BOARD_SKINS.default;
    return <div className="grid h-14 w-14 grid-cols-3 gap-px overflow-hidden rounded-lg border" style={{ background: b.line, borderColor: b.line }} aria-hidden>{Array.from({ length: 9 }).map((_, i) => <span key={i} style={{ background: i % 2 ? b.track : b.bg }} />)}</div>;
  }
  return <div className="flex h-14 w-14 items-center justify-center rounded-lg bg-gradient-to-br from-brand-500 to-accent"><Sparkles className="h-6 w-6" aria-hidden /></div>;
}

export default function CoinStore() {
  const wallet = useWalletStore((s) => s.wallet);
  const setWallet = useWalletStore((s) => s.setWallet);
  const refreshUser = useAuthStore((s) => s.refreshUser);
  const items = useAsync(() => StoreApi.items(), []);
  const pay = useAsync(() => PaymentApi.status(), []);
  const [cat, setCat] = useState('BOARD_SKIN');
  const [buying, setBuying] = useState(null);
  const [busy, setBusy] = useState(false);

  const confirmBuy = async () => {
    setBusy(true);
    try {
      const res = await StoreApi.purchase(buying.id);
      setWallet(res.wallet);
      toast.success(`${buying.name} unlocked!`);
      setBuying(null);
      items.reload();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const equip = async (item) => {
    try {
      if (item.equipped) await StoreApi.unequip(item.category); else await StoreApi.equip(item.id);
      toast.success(item.equipped ? `${item.name} removed` : `${item.name} equipped`);
      items.reload();
      refreshUser().catch(() => {});
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const list = (items.data || []).filter((i) => i.category === cat);
  return (
    <div>
      <PageHeader title="Coin Store" subtitle="Spend virtual coins on cosmetics. Nothing here affects gameplay." actions={wallet && <span className="flex items-center gap-1.5 rounded-full bg-white/5 px-3 py-1.5 font-semibold"><Coins className="h-4 w-4 text-yellow-300" aria-hidden />{formatNumber(wallet.coinBalance)}</span>} />

      <div className="mb-5 flex flex-wrap gap-2" role="tablist" aria-label="Categories">
        {CATS.map(([k, label]) => <button key={k} role="tab" aria-selected={cat === k} className={cat === k ? 'btn-primary' : 'btn-secondary'} onClick={() => setCat(k)}>{label}</button>)}
      </div>

      {items.loading && !items.data ? <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{Array.from({ length: 6 }).map((_, i) => <LoadingSkeleton key={i} variant="card" />)}</div>
        : items.error ? <ErrorState message={items.error} onRetry={items.reload} />
          : !list.length ? <EmptyState title="Nothing in this category" message="Check back soon for new items." />
            : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {list.map((it) => (
                  <article key={it.id} className="card flex flex-col gap-3" data-testid="store-item">
                    <div className="flex items-start gap-3">
                      <Preview item={it} />
                      <div className="min-w-0 flex-1">
                        <h3 className="truncate font-bold">{it.name}</h3>
                        <Badge tone={RARITY[it.rarity?.toLowerCase()] || 'gray'}>{it.rarity || 'common'}</Badge>
                        <p className="mt-1 text-xs text-slate-400">{it.description}</p>
                      </div>
                    </div>
                    {it.owned ? (
                      <button className={it.equipped ? 'btn-secondary' : 'btn-primary'} onClick={() => equip(it)}>{it.equipped ? <><Check className="h-4 w-4" aria-hidden />Equipped — remove</> : 'Equip'}</button>
                    ) : (
                      <button className="btn-primary" disabled={wallet != null && wallet.coinBalance < it.priceCoins} onClick={() => setBuying(it)}>
                        {wallet != null && wallet.coinBalance < it.priceCoins ? <><Lock className="h-4 w-4" aria-hidden />Not enough coins</> : <><Coins className="h-4 w-4" aria-hidden />{formatNumber(it.priceCoins)}</>}
                      </button>
                    )}
                  </article>
                ))}
              </div>
            )}

      <section className="card mt-8" aria-label="Coin packs">
        <h2 className="mb-1 font-bold">Coin packs</h2>
        {pay.loading ? <LoadingSkeleton rows={1} />
          : pay.error ? <ErrorState message={pay.error} onRetry={pay.reload} />
            : !pay.data?.enabled ? (
              <p className="flex items-start gap-2 text-sm text-slate-400"><Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />{pay.data?.message || 'Coin purchases are not available right now. You can earn coins by playing and with the daily bonus.'}</p>
            ) : (
              <div className="mt-3 grid gap-3 sm:grid-cols-3">
                {pay.data.packages.map((p) => <CoinPack key={p.id} pack={p} onDone={() => { useWalletStore.getState().load(); }} />)}
              </div>
            )}
        <p className="mt-3 text-xs text-slate-500">Coins are virtual entertainment currency. They cannot be withdrawn, transferred for cash or used to wager.</p>
      </section>

      <ConfirmModal open={!!buying} title="Confirm purchase" busy={busy} confirmLabel="Buy" onConfirm={confirmBuy} onClose={() => setBuying(null)}
        message={buying ? `Buy “${buying.name}” for ${formatNumber(buying.priceCoins)} coins?` : ''} />
    </div>
  );
}

function loadRazorpay() {
  return new Promise((resolve, reject) => {
    if (window.Razorpay) { resolve(); return; }
    const s = document.createElement('script');
    s.src = 'https://checkout.razorpay.com/v1/checkout.js';
    s.onload = resolve;
    s.onerror = () => reject(new Error('Could not load the payment form'));
    document.body.appendChild(s);
  });
}

/** Payment checkout. Only rendered when the server reports payments enabled. The server verifies the signature. */
function CoinPack({ pack, onDone }) {
  const [busy, setBusy] = useState(false);
  const start = async () => {
    setBusy(true);
    try {
      const order = await PaymentApi.createOrder(pack.id);
      await loadRazorpay();
      const rzp = new window.Razorpay({
        key: order.keyId, amount: order.amountMinor, currency: order.currency, order_id: order.providerOrderId, name: 'Ludo Arena', description: pack.name,
        handler: async (r) => {
          try {
            await PaymentApi.verify({ orderId: order.orderId, paymentId: r.razorpay_payment_id, signature: r.razorpay_signature });
            toast.success('Payment verified — coins added!');
            onDone();
          } catch (e) { toast.error(errorMessage(e)); }
        },
        modal: { ondismiss: () => setBusy(false) },
      });
      rzp.open();
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="rounded-xl border border-white/10 p-4 text-center">
      <p className="font-bold">{pack.name}</p>
      <p className="text-2xl font-extrabold text-yellow-300">{formatNumber(pack.coins)}</p>
      <button className="btn-primary mt-2 w-full" disabled={busy} onClick={start}>{(pack.priceMinor / 100).toFixed(2)} {pack.currency}</button>
    </div>
  );
}
