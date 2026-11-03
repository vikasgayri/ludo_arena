import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { RoomApi, errorMessage, fieldErrors as getFieldErrors } from '../lib/api';
import { useAuthStore } from '../store/authStore';
import { toast } from '../store/toastStore';
import { Field, PageHeader, Spinner } from '../components/ui';

export default function CreateRoom() {
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: `${user?.name ?? 'My'}'s room`, maxPlayers: 4, isPrivate: false });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (ev) => {
    ev.preventDefault();
    const e = {};
    const name = form.name.trim();
    if (name.length < 3 || name.length > 40) e.name = 'Room name must be 3-40 characters';
    setErrors(e);
    setFormError(null);
    if (Object.keys(e).length) return;
    setBusy(true);
    try {
      const room = await RoomApi.create({ name, maxPlayers: form.maxPlayers, isPrivate: form.isPrivate });
      toast.success('Room created');
      navigate(`/room/${room.id}`);
    } catch (err) {
      setErrors(getFieldErrors(err));
      setFormError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-lg">
      <PageHeader title="Create room" subtitle="Set up a match and invite friends with the room code." />
      <form onSubmit={submit} className="card space-y-5" noValidate>
        {formError && <p role="alert" className="rounded-xl bg-red-500/10 px-3 py-2 text-sm text-red-300">{formError}</p>}
        <Field label="Room name" htmlFor="room-name" error={errors.name}>
          <input id="room-name" className="input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={40} />
        </Field>
        <fieldset>
          <legend className="label">Players</legend>
          <div className="grid grid-cols-2 gap-3">
            {[2, 4].map((n) => (
              <label key={n} className={`cursor-pointer rounded-xl border p-3 text-center transition ${form.maxPlayers === n ? 'border-brand-400 bg-brand-500/10' : 'border-white/10 hover:bg-white/5'}`}>
                <input type="radio" name="maxPlayers" className="sr-only" checked={form.maxPlayers === n} onChange={() => setForm({ ...form, maxPlayers: n })} />
                <span className="block text-2xl font-bold">{n}</span>
                <span className="text-xs text-slate-400">{n === 2 ? 'Head to head' : 'Full table'}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <label className="flex cursor-pointer items-center justify-between rounded-xl border border-white/10 p-3">
          <span>
            <span className="block text-sm font-medium">Private room</span>
            <span className="block text-xs text-slate-400">Hidden from the lobby — friends join with the code.</span>
          </span>
          <input type="checkbox" className="h-5 w-5 accent-brand-500" checked={form.isPrivate} onChange={(e) => setForm({ ...form, isPrivate: e.target.checked })} aria-label="Private room" />
        </label>
        <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner className="h-4 w-4" />}Create room</button>
      </form>
    </div>
  );
}
