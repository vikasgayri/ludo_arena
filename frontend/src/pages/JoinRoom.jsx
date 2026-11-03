import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { RoomApi, errorMessage } from '../lib/api';
import { toast } from '../store/toastStore';
import { Field, PageHeader, Spinner } from '../components/ui';

export default function JoinRoom() {
  const navigate = useNavigate();
  const [code, setCode] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (ev) => {
    ev.preventDefault();
    const c = code.trim().toUpperCase();
    setError(null);
    if (!/^[A-Z0-9]{6}$/.test(c)) { setError('Room codes are 6 letters or digits'); return; }
    setBusy(true);
    try {
      const room = await RoomApi.join(c);
      toast.success(`Joined ${room.name}`);
      navigate(`/room/${room.id}`);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-md">
      <PageHeader title="Join a room" subtitle="Enter the 6-character code your friend shared." />
      <form onSubmit={submit} className="card space-y-4" noValidate>
        <Field label="Room code" htmlFor="code" error={error}>
          <input id="code" className="input text-center text-2xl font-bold uppercase tracking-[0.4em]" value={code} maxLength={6} autoComplete="off" onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="ABC123" />
        </Field>
        <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner className="h-4 w-4" />}Join room</button>
      </form>
    </div>
  );
}
