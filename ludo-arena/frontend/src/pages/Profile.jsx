import { useState } from 'react';
import { Check, Pencil } from 'lucide-react';
import { StoreApi, UserApi, errorMessage, fieldErrors as getFieldErrors } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import { useAuthStore } from '../store/authStore';
import { toast } from '../store/toastStore';
import { FREE_AVATARS, THEMES } from '../lib/cosmetics';
import { formatDay, formatNumber } from '../lib/format';
import { Avatar, Field, PageHeader, Spinner, StatCard } from '../components/ui';

export default function Profile() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const refreshUser = useAuthStore((s) => s.refreshUser);
  const inventory = useAsync(() => StoreApi.items(), []);
  const [name, setName] = useState(user?.name ?? '');
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);

  if (!user) return null;
  const owned = (inventory.data || []).filter((i) => i.owned && i.category === 'AVATAR');
  const theme = THEMES[user.cosmetics?.profileTheme] || THEMES.default;

  const save = async (patch) => {
    setBusy(true);
    setErrors({});
    try {
      const updated = await UserApi.update(patch);
      setUser(updated);
      toast.success('Profile updated');
    } catch (e) {
      setErrors(getFieldErrors(e));
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const saveName = (ev) => {
    ev.preventDefault();
    const n = name.trim();
    if (n.length < 2 || n.length > 30) { setErrors({ name: 'Name must be 2-30 characters' }); return; }
    if (n !== user.name) save({ name: n });
  };

  const equipOwned = async (item) => {
    setBusy(true);
    try { await StoreApi.equip(item.id); await refreshUser(); toast.success(`${item.name} equipped`); } catch (e) { toast.error(errorMessage(e)); } finally { setBusy(false); }
  };

  const s = user.stats;
  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="Profile" />
      <div className={`card mb-6 flex flex-wrap items-center gap-4 bg-gradient-to-br ${theme}`}>
        <Avatar avatar={user.avatar} name={user.name} size={72} />
        <div>
          <h2 className="text-2xl font-bold">{user.name}</h2>
          <p className="text-sm text-slate-300">{user.email}</p>
          <p className="text-xs text-slate-400">Member since {formatDay(user.createdAt)} · Level {s.level}</p>
        </div>
      </div>

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Matches" value={s.totalMatches} />
        <StatCard label="Wins" value={s.wins} tone="yellow" />
        <StatCard label="Win rate" value={`${s.winRate}%`} tone="green" />
        <StatCard label="XP" value={formatNumber(s.xp)} hint={`${s.captures} captures`} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <form className="card space-y-4" onSubmit={saveName} noValidate>
          <h2 className="font-bold">Display name</h2>
          <Field label="Name" htmlFor="profile-name" error={errors.name}><input id="profile-name" className="input" value={name} maxLength={30} onChange={(e) => setName(e.target.value)} /></Field>
          <button className="btn-primary" disabled={busy || name.trim() === user.name}>{busy ? <Spinner className="h-4 w-4" /> : <Pencil className="h-4 w-4" aria-hidden />}Save name</button>
        </form>

        <section className="card" aria-label="Avatar">
          <h2 className="mb-3 font-bold">Avatar</h2>
          <div className="grid grid-cols-6 gap-2">
            {FREE_AVATARS.map((a) => (
              <button key={a} className={`relative rounded-full p-0.5 ${user.avatar === a ? 'ring-2 ring-brand-400' : 'hover:ring-2 hover:ring-white/20'}`} disabled={busy} onClick={() => save({ avatar: a })} aria-label={`Use ${a}`} aria-pressed={user.avatar === a}>
                <Avatar avatar={a} size={44} />{user.avatar === a && <Check className="absolute -right-1 -top-1 h-4 w-4 rounded-full bg-brand-500 p-0.5" aria-hidden />}
              </button>
            ))}
          </div>
          {owned.length > 0 && (
            <>
              <h3 className="mb-2 mt-4 text-sm font-semibold text-slate-300">Your unlocked avatars</h3>
              <div className="flex flex-wrap gap-2">
                {owned.map((i) => (
                  <button key={i.id} disabled={busy} onClick={() => equipOwned(i)} className={`rounded-full p-0.5 ${user.avatar === i.assetKey ? 'ring-2 ring-brand-400' : 'hover:ring-2 hover:ring-white/20'}`} aria-label={`Equip ${i.name}`}>
                    <Avatar avatar={i.assetKey} name={i.name} size={44} />
                  </button>
                ))}
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  );
}
