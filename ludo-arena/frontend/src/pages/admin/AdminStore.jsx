import { useState } from 'react';
import { Plus } from 'lucide-react';
import { AdminApi, errorMessage, fieldErrors as getFieldErrors } from '../../lib/api';
import { useAsync } from '../../lib/useAsync';
import { toast } from '../../store/toastStore';
import { formatNumber } from '../../lib/format';
import Modal from '../../components/Modal';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { Badge, EmptyState, ErrorState, Field, PageHeader } from '../../components/ui';

const CATEGORIES = ['BOARD_SKIN', 'DICE_SKIN', 'AVATAR', 'PROFILE_THEME'];
const BLANK = { sku: '', name: '', description: '', category: 'BOARD_SKIN', priceCoins: 100, rarity: 'common', assetKey: '', active: true };

export default function AdminStore() {
  const { data, loading, error, reload } = useAsync(() => AdminApi.storeItems(), []);
  const [editing, setEditing] = useState(null); // item or BLANK + isNew

  return (
    <div>
      <PageHeader title="Store management" subtitle="Cosmetic items sold for virtual coins" actions={<button className="btn-primary" onClick={() => setEditing({ ...BLANK, isNew: true })}><Plus className="h-4 w-4" aria-hidden />New item</button>} />
      {loading && !data ? <LoadingSkeleton variant="table" rows={6} />
        : error ? <ErrorState message={error} onRetry={reload} />
          : !data?.length ? <EmptyState title="No store items" message="Create the first item." />
            : (
              <div className="overflow-x-auto rounded-2xl border border-white/5">
                <table className="min-w-full divide-y divide-white/5">
                  <thead className="bg-navy-800"><tr><th className="th">Item</th><th className="th">Category</th><th className="th">Rarity</th><th className="th text-right">Price</th><th className="th">Asset key</th><th className="th">Status</th><th className="th" /></tr></thead>
                  <tbody className="divide-y divide-white/5">
                    {data.map((i) => (
                      <tr key={i.id}>
                        <td className="td"><p className="font-medium">{i.name}</p><p className="font-mono text-xs text-slate-500">{i.sku}</p></td>
                        <td className="td">{i.category}</td><td className="td">{i.rarity}</td>
                        <td className="td text-right tabular-nums">{formatNumber(i.priceCoins)}</td>
                        <td className="td font-mono text-xs">{i.assetKey}</td>
                        <td className="td"><Badge tone={i.active ? 'green' : 'gray'}>{i.active ? 'Active' : 'Hidden'}</Badge></td>
                        <td className="td text-right"><button className="btn-ghost px-2 py-1" onClick={() => setEditing({ ...i, isNew: false })}>Edit</button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
      {editing && <ItemModal item={editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); reload(); }} />}
    </div>
  );
}

function ItemModal({ item, onClose, onSaved }) {
  const [f, setF] = useState(item);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  const submit = async () => {
    const e = {};
    if (!/^[a-z0-9_-]{3,40}$/.test(f.sku)) e.sku = 'Lowercase letters, digits, - or _ (3-40)';
    if (!f.name.trim()) e.name = 'Name is required';
    if (!f.assetKey.trim()) e.assetKey = 'Asset key is required';
    const price = parseInt(f.priceCoins, 10);
    if (!Number.isFinite(price) || price < 0) e.priceCoins = 'Price must be 0 or more';
    setErrors(e);
    if (Object.keys(e).length) return;
    const body = { sku: f.sku, name: f.name.trim(), description: f.description, category: f.category, priceCoins: price, rarity: f.rarity, assetKey: f.assetKey.trim(), active: !!f.active };
    setBusy(true);
    try {
      if (item.isNew) await AdminApi.createStoreItem(body); else await AdminApi.updateStoreItem(item.id, body);
      toast.success(item.isNew ? 'Item created' : 'Item updated');
      onSaved();
    } catch (err) {
      setErrors(getFieldErrors(err));
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open title={item.isNew ? 'New store item' : `Edit ${item.name}`} onClose={onClose} footer={<><button className="btn-secondary" onClick={onClose}>Cancel</button><button className="btn-primary" disabled={busy} onClick={submit}>{busy ? 'Saving…' : 'Save'}</button></>}>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="SKU" htmlFor="i-sku" error={errors.sku}><input id="i-sku" className="input" value={f.sku} onChange={set('sku')} disabled={!item.isNew} /></Field>
        <Field label="Name" htmlFor="i-name" error={errors.name}><input id="i-name" className="input" value={f.name} onChange={set('name')} maxLength={60} /></Field>
        <div className="sm:col-span-2"><Field label="Description" htmlFor="i-desc" error={errors.description}><input id="i-desc" className="input" value={f.description || ''} onChange={set('description')} maxLength={300} /></Field></div>
        <Field label="Category" htmlFor="i-cat"><select id="i-cat" className="input" value={f.category} onChange={set('category')}>{CATEGORIES.map((c) => <option key={c}>{c}</option>)}</select></Field>
        <Field label="Rarity" htmlFor="i-rar"><select id="i-rar" className="input" value={f.rarity || 'common'} onChange={set('rarity')}><option>common</option><option>rare</option><option>epic</option><option>legendary</option></select></Field>
        <Field label="Price (coins)" htmlFor="i-price" error={errors.priceCoins}><input id="i-price" className="input" inputMode="numeric" value={f.priceCoins} onChange={set('priceCoins')} /></Field>
        <Field label="Asset key" htmlFor="i-asset" error={errors.assetKey}><input id="i-asset" className="input" value={f.assetKey} onChange={set('assetKey')} placeholder="board-neon" /></Field>
        <label className="flex items-center gap-2 text-sm sm:col-span-2"><input type="checkbox" className="accent-brand-500" checked={!!f.active} onChange={set('active')} />Visible in store</label>
      </div>
    </Modal>
  );
}
