import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Clock, Flag, Trophy, Wifi, WifiOff } from 'lucide-react';
import { useGameStore } from '../store/gameStore';
import { useAuthStore } from '../store/authStore';
import { useWalletStore } from '../store/walletStore';
import { RoomApi, errorMessage } from '../lib/api';
import { describeEvent } from '../lib/events';
import { toast } from '../store/toastStore';
import LudoBoard from '../components/LudoBoard';
import Dice from '../components/Dice';
import PlayerCard from '../components/PlayerCard';
import Modal, { ConfirmModal } from '../components/Modal';
import LoadingSkeleton from '../components/LoadingSkeleton';
import { ErrorState } from '../components/ui';

function useCountdown(deadline, offsetMs, active) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!active) return undefined;
    const t = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(t);
  }, [active]);
  if (!active || !deadline) return null;
  return Math.max(0, Math.ceil((deadline - (now + offsetMs)) / 1000));
}

export default function GameRoom() {
  const { matchId } = useParams();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const { watchMatch, unwatchMatch, match, events, connection, pending, error, rollDice, moveToken, clearError, serverOffsetMs, requestState } = useGameStore();
  const [leaveOpen, setLeaveOpen] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [resultOpen, setResultOpen] = useState(false);
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    watchMatch(matchId);
    const t = setTimeout(() => setTimedOut(true), 12000);
    return () => { clearTimeout(t); unwatchMatch(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [matchId]);

  // After a socket reconnect, ask the server for a fresh snapshot (this is the reconnection path).
  useEffect(() => { if (connection === 'connected' && match) requestState(); /* eslint-disable-next-line */ }, [connection]);

  useEffect(() => {
    if (error) { toast.error(error); clearError(); }
  }, [error, clearError]);

  const finished = match && match.status !== 'ACTIVE';
  useEffect(() => {
    if (finished) {
      setResultOpen(true);
      useWalletStore.getState().load(); // rewards were credited by the server
      useAuthStore.getState().refreshUser().catch(() => {});
    }
  }, [finished]);

  const me = match?.players.find((p) => p.userId === user?.id);
  const isMyTurn = !!match && match.status === 'ACTIVE' && match.currentUserId === user?.id;
  const canRoll = isMyTurn && !match.awaitingMove && !pending;
  const movable = isMyTurn && match.awaitingMove ? match.validMoves : [];
  const current = match?.players.find((p) => p.userId === match.currentUserId);
  const secondsLeft = useCountdown(match?.turnDeadline, serverOffsetMs, !!match && match.status === 'ACTIVE');
  const shownDice = match ? (match.diceValue ?? match.lastDiceValue) : null;
  const diceOwner = match?.players.find((p) => p.userId === (match.diceValue != null ? match.currentUserId : match.lastDiceUserId)) || current;
  const nameOf = useMemo(() => {
    const m = new Map((match?.players || []).map((p) => [p.userId, p.userId === user?.id ? 'You' : p.name]));
    return (id) => m.get(id) || 'Someone';
  }, [match, user?.id]);

  const leave = async () => {
    setLeaving(true);
    try {
      if (match?.roomId) await RoomApi.leave(match.roomId);
      navigate('/dashboard', { replace: true });
    } catch (e) {
      toast.error(errorMessage(e));
      setLeaving(false);
    }
  };

  if (!match) {
    if (timedOut) return <ErrorState message="Could not load this match. It may have ended or you may not be a participant." onRetry={() => { setTimedOut(false); watchMatch(matchId); }} />;
    return <LoadingSkeleton variant="page" />;
  }

  const winner = match.players.find((p) => p.userId === match.winnerId);
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <div className="flex flex-col items-center">
        {connection !== 'connected' && (
          <div role="status" className="mb-3 flex w-full items-center gap-2 rounded-xl bg-yellow-500/10 px-3 py-2 text-sm text-yellow-200"><WifiOff className="h-4 w-4" aria-hidden />Connection lost — reconnecting…</div>
        )}
        <LudoBoard
          players={match.players}
          movableTokens={movable}
          movableUserId={isMyTurn ? user?.id : null}
          onTokenSelect={moveToken}
          skin={me?.boardSkin || 'default'}
        />
        <div className="mt-4 flex w-full max-w-[640px] items-center justify-between gap-4 rounded-2xl bg-navy-800/80 p-4">
          <div className="min-w-0">
            <p className="text-xs uppercase tracking-wide text-slate-400">{match.status === 'ACTIVE' ? 'Turn' : 'Match over'}</p>
            <p className="truncate text-lg font-bold" data-testid="turn-label">{match.status !== 'ACTIVE' ? (winner ? `${winner.name} won` : 'Finished') : isMyTurn ? 'Your turn' : `${current?.name ?? '…'}'s turn`}</p>
            {match.status === 'ACTIVE' && secondsLeft != null && (
              <p className={`flex items-center gap-1 text-sm ${secondsLeft <= 5 ? 'text-red-300' : 'text-slate-400'}`}><Clock className="h-4 w-4" aria-hidden /><span data-testid="turn-timer">{secondsLeft}s</span></p>
            )}
            {isMyTurn && match.awaitingMove && <p className="mt-1 text-sm text-brand-400">Pick a highlighted token to move.</p>}
          </div>
          <div className="flex flex-col items-center gap-1">
            <Dice
              value={shownDice}
              rolling={pending && !match.awaitingMove}
              skin={diceOwner?.diceSkin || 'default'}
              onClick={canRoll ? rollDice : undefined}
              disabled={!canRoll}
              label={canRoll ? 'Roll dice' : undefined}
              size={76}
            />
            <span className="text-xs text-slate-400">{canRoll ? 'Tap to roll' : ''}</span>
          </div>
        </div>
      </div>

      <aside className="space-y-4" aria-label="Match info">
        <div className="flex items-center justify-between">
          <h2 className="font-bold">Players</h2>
          <span className={`badge ${connection === 'connected' ? 'bg-emerald-500/15 text-emerald-300' : 'bg-yellow-500/15 text-yellow-200'}`}>
            {connection === 'connected' ? <Wifi className="mr-1 h-3 w-3" aria-hidden /> : <WifiOff className="mr-1 h-3 w-3" aria-hidden />}{connection === 'connected' ? 'Live' : 'Offline'}
          </span>
        </div>
        <div className="space-y-2">
          {match.players.map((p) => (
            <PlayerCard key={p.userId} name={p.name} avatar={p.avatar} color={p.color} you={p.userId === user?.id} active={match.status === 'ACTIVE' && p.userId === match.currentUserId}
              connected={p.connected} forfeited={p.forfeited} finishedTokens={p.finishedTokens} rank={p.rank} />
          ))}
        </div>
        <div className="card p-4">
          <h3 className="mb-2 text-sm font-bold">Game log</h3>
          <ol className="max-h-64 space-y-1 overflow-y-auto text-sm text-slate-300" aria-live="polite" data-testid="game-log">
            {events.length === 0 && <li className="text-slate-500">Waiting for the first move…</li>}
            {[...events].reverse().slice(0, 40).map((e) => <li key={e.eventId}>{describeEvent(e, nameOf)}</li>)}
          </ol>
        </div>
        {match.status === 'ACTIVE' && me && !me.forfeited && (
          <button className="btn-secondary w-full text-red-300" onClick={() => setLeaveOpen(true)}><Flag className="h-4 w-4" aria-hidden />Forfeit & leave</button>
        )}
      </aside>

      <ConfirmModal open={leaveOpen} title="Leave this match?" danger busy={leaving} confirmLabel="Forfeit"
        message="You will forfeit the match and lose your place. This can't be undone." onConfirm={leave} onClose={() => setLeaveOpen(false)} />

      <Modal open={resultOpen} onClose={() => setResultOpen(false)} title={match.winnerId === user?.id ? 'You won! 🎉' : 'Match finished'} size="sm"
        footer={<><button className="btn-secondary" onClick={() => navigate('/matches')}>Match history</button><button className="btn-primary" onClick={() => navigate('/dashboard')}>Back to dashboard</button></>}>
        <div className="space-y-3">
          <p className="flex items-center gap-2 text-sm text-slate-300"><Trophy className="h-4 w-4 text-yellow-300" aria-hidden />{winner ? `${winner.name} took the win.` : 'The match ended without a winner.'}</p>
          <ol className="space-y-1 text-sm">
            {[...match.players].sort((a, b) => (a.rank || 99) - (b.rank || 99)).map((p) => (
              <li key={p.userId} className="flex justify-between"><span>{p.rank ? `#${p.rank}` : '—'} {p.name}{p.userId === user?.id ? ' (you)' : ''}</span><span className="text-slate-400">{p.finishedTokens}/4 home</span></li>
            ))}
          </ol>
        </div>
      </Modal>
    </div>
  );
}
