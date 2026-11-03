import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Check, Copy, LogOut, Wifi, WifiOff } from 'lucide-react';
import { RoomApi, errorMessage } from '../lib/api';
import { useAuthStore } from '../store/authStore';
import { useGameStore } from '../store/gameStore';
import { toast } from '../store/toastStore';
import PlayerCard from '../components/PlayerCard';
import LoadingSkeleton from '../components/LoadingSkeleton';
import { ErrorState, PageHeader } from '../components/ui';

/** Waiting room: shows players, ready toggles and the join code. Navigates to /game/:matchId when the match starts. */
export default function RoomLobby() {
  const { roomId } = useParams();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const { watchRoom, unwatchRoom, room: liveRoom, roomEvent, connection } = useGameStore();
  const [room, setRoom] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const navigated = useRef(false);

  const go = (matchId) => {
    if (navigated.current) return;
    navigated.current = true;
    navigate(`/game/${matchId}`, { replace: true });
  };

  const load = async () => {
    try {
      const r = await RoomApi.get(roomId);
      setRoom((prev) => ({ ...r, code: r.code ?? prev?.code }));
      setError(null);
      if (r.status === 'IN_PROGRESS' && r.matchId) go(r.matchId);
      if (r.status === 'CLOSED' || r.status === 'FINISHED') { toast.info('This room has closed.'); navigate('/lobby', { replace: true }); }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    watchRoom(roomId);
    // Polling is a safety net for missed broadcasts while the socket reconnects.
    const t = setInterval(() => { if (document.visibilityState === 'visible') load(); }, 4000);
    return () => { clearInterval(t); unwatchRoom(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId]);

  useEffect(() => {
    if (liveRoom && liveRoom.id === roomId) setRoom((prev) => ({ ...liveRoom, code: liveRoom.code ?? prev?.code }));
  }, [liveRoom, roomId]);

  useEffect(() => {
    if (!roomEvent) return;
    if (roomEvent.type === 'MATCH_STARTED' && roomEvent.matchId) go(roomEvent.matchId);
    if (roomEvent.type === 'ROOM_CLOSED') { toast.info('The room was closed.'); navigate('/lobby', { replace: true }); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomEvent]);

  const me = room?.players.find((p) => p.userId === user?.id);

  const toggleReady = async () => {
    if (!me) return;
    setBusy(true);
    try {
      const updated = await RoomApi.ready(roomId, !me.ready);
      setRoom((prev) => ({ ...updated, code: updated.code ?? prev?.code }));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const leave = async () => {
    setBusy(true);
    try {
      await RoomApi.leave(roomId);
      navigate('/lobby', { replace: true });
    } catch (e) {
      toast.error(errorMessage(e));
      setBusy(false);
    }
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(room.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      toast.info(`Room code: ${room.code}`);
    }
  };

  if (loading) return <LoadingSkeleton variant="page" />;
  if (error && !room) return <ErrorState message={error} onRetry={load} />;
  if (!room) return null;

  const slots = Array.from({ length: room.maxPlayers }, (_, i) => room.players[i] || null);
  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        title={room.name}
        subtitle={`${room.maxPlayers}-player match · ${room.players.length}/${room.maxPlayers} joined`}
        actions={<span className={`badge ${connection === 'connected' ? 'bg-emerald-500/15 text-emerald-300' : 'bg-yellow-500/15 text-yellow-200'}`}>{connection === 'connected' ? <Wifi className="mr-1 h-3 w-3" aria-hidden /> : <WifiOff className="mr-1 h-3 w-3" aria-hidden />}{connection === 'connected' ? 'Live' : 'Reconnecting…'}</span>}
      />
      {room.code && (
        <div className="card mb-4 flex items-center justify-between gap-3">
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-400">Room code</p>
            <p className="text-3xl font-extrabold tracking-[0.3em]" data-testid="room-code">{room.code}</p>
          </div>
          <button className="btn-secondary" onClick={copy}>{copied ? <Check className="h-4 w-4 text-emerald-400" aria-hidden /> : <Copy className="h-4 w-4" aria-hidden />}{copied ? 'Copied' : 'Copy'}</button>
        </div>
      )}
      <div className="space-y-2">
        {slots.map((p, i) => p
          ? <PlayerCard key={p.userId} name={p.name} avatar={p.avatar} you={p.userId === user?.id} host={p.host} ready={p.ready} />
          : <div key={`empty-${i}`} className="rounded-xl border border-dashed border-white/10 p-4 text-center text-sm text-slate-500">Waiting for player…</div>)}
      </div>
      <p className="mt-4 text-center text-sm text-slate-400">
        {room.players.length < room.maxPlayers ? 'The match starts when the table is full and everyone is ready.' : 'Everyone is here — mark yourself ready to start.'}
      </p>
      <div className="mt-4 flex gap-3">
        <button className="btn-primary flex-1" onClick={toggleReady} disabled={busy || !me}>{me?.ready ? 'Not ready' : "I'm ready"}</button>
        <button className="btn-secondary" onClick={leave} disabled={busy}><LogOut className="h-4 w-4" aria-hidden />Leave</button>
      </div>
    </div>
  );
}
