import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Bot, Clock, Flag, MessageCircle, Trophy, Volume2, VolumeX, Wifi, WifiOff } from 'lucide-react';
import { useGameStore } from '../store/gameStore';
import { useAuthStore } from '../store/authStore';
import { useWalletStore } from '../store/walletStore';
import { RoomApi, errorMessage } from '../lib/api';
import { describeEvent } from '../lib/events';
import { prefersReducedMotion, useAnimatedPlayers } from '../lib/animation';
import { useDiceRoll, useGameSounds } from '../lib/gameFx';
import { isMuted, onMutedChange, setMuted } from '../lib/sound';
import { COLOR_HEX } from '../lib/format';
import Confetti from '../components/Confetti';
import ChatPanel from '../components/ChatPanel';
import { play } from '../lib/sound';
import { toast } from '../store/toastStore';
import LudoBoard from '../components/LudoBoard';
import Dice from '../components/Dice';
import PlayerCard from '../components/PlayerCard';
import Modal, { ConfirmModal } from '../components/Modal';
import LoadingSkeleton from '../components/LoadingSkeleton';
import { ErrorState } from '../components/ui';

const AUTO_MOVE_DELAY_MS = 400;

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
  const { watchMatch, unwatchMatch, match, events, lastUpdateEvents, chat, sendChat, connection, pending, error, rollDice, moveToken, clearError, serverOffsetMs, requestState } = useGameStore();
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

  // presentation only: smooth token walking, dice shuffle, sounds (the server state stays authoritative)
  const animationsOn = user?.preferences?.animations !== false && !prefersReducedMotion();
  const { players: shownPlayers, animating } = useAnimatedPlayers(match?.players || [], { enabled: animationsOn });
  const diceRolling = useDiceRoll(lastUpdateEvents);
  useGameSounds(lastUpdateEvents, user?.id);
  const [muted, setMutedState] = useState(isMuted());
  useEffect(() => onMutedChange(setMutedState), []);

  // chat (online matches only): unread badge for the mobile chat button, a soft sound for incoming messages
  const chatEnabled = !!match && !match.vsComputer;
  const [chatOpen, setChatOpen] = useState(false);
  const othersCount = (chat || []).filter((c) => c.userId !== user?.id).length;
  const [seenOthers, setSeenOthers] = useState(0);
  const unread = chatOpen ? 0 : Math.max(0, othersCount - seenOthers);
  useEffect(() => { if (chatOpen) setSeenOthers(othersCount); }, [chatOpen, othersCount]);
  const prevChatLen = useRef(0);
  useEffect(() => {
    const list = chat || [];
    const last = list[list.length - 1];
    if (prevChatLen.current > 0 && list.length > prevChatLen.current && last && last.userId !== user?.id) play('chat');
    prevChatLen.current = list.length;
  }, [chat, user?.id]);

  const me = match?.players.find((p) => p.userId === user?.id);
  const isMyTurn = !!match && match.status === 'ACTIVE' && match.currentUserId === user?.id;
  const canRoll = isMyTurn && !match.awaitingMove && !pending;
  // highlight choices only after the dice has landed and tokens stopped walking
  const settled = !diceRolling && !animating;
  const movable = isMyTurn && match.awaitingMove && settled ? match.validMoves : [];
  // Only one real choice (one legal token, or several legal tokens on the same spot, e.g. all in base)?
  // Then move it automatically. The server still validates the move like any other.
  const onlyChoice = useMemo(() => {
    if (!isMyTurn || !match?.awaitingMove || !me) return null;
    const moves = match.validMoves || [];
    if (!moves.length) return null;
    return new Set(moves.map((i) => me.tokens[i])).size === 1 ? moves[0] : null;
  }, [isMyTurn, match, me]);
  const autoMovedSeq = useRef(null);
  useEffect(() => {
    if (onlyChoice == null || !settled || pending || autoMovedSeq.current === match.seq) return undefined;
    const t = setTimeout(() => {
      autoMovedSeq.current = match.seq;
      moveToken(onlyChoice);
    }, AUTO_MOVE_DELAY_MS);
    return () => clearTimeout(t);
  }, [onlyChoice, settled, pending, match?.seq, moveToken]);
  const current = match?.players.find((p) => p.userId === match.currentUserId);
  const botTurn = !!match && match.status === 'ACTIVE' && !!current?.bot;
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
  const turnLabel = match.status === 'ABANDONED' ? 'Match abandoned'
    : match.status !== 'ACTIVE' ? (winner ? `${winner.name} won` : 'Finished')
      : isMyTurn ? 'Your turn' : botTurn ? `${current.name} is thinking…` : `${current?.name ?? '…'}'s turn`;
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <div className="flex flex-col items-center">
        {connection !== 'connected' && (
          <div role="status" className="mb-3 flex w-full items-center gap-2 rounded-xl bg-yellow-500/10 px-3 py-2 text-sm text-yellow-200"><WifiOff className="h-4 w-4" aria-hidden />Connection lost — reconnecting…</div>
        )}
        <div className="mb-3 flex w-full flex-wrap justify-center gap-2 lg:hidden" data-testid="mobile-players">
          {match.players.map((p) => {
            const isActive = match.status === 'ACTIVE' && p.userId === match.currentUserId;
            return (
              <span key={p.userId} className={`flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold transition ${isActive ? 'bg-white/15 text-white ring-2 ring-white/60' : 'bg-white/5 text-slate-300'} ${p.forfeited ? 'line-through opacity-40' : ''}`}>
                <span className="h-2.5 w-2.5 rounded-full" style={{ background: COLOR_HEX[p.color] }} aria-hidden />
                {p.userId === user?.id ? 'You' : p.name}
                {p.bot && <Bot className="h-3 w-3 text-accent-400" aria-label="computer" />}
              </span>
            );
          })}
        </div>
        <LudoBoard
          activeColor={match.status === 'ACTIVE' ? current?.color : null}
          players={shownPlayers}
          movableTokens={movable}
          movableUserId={isMyTurn ? user?.id : null}
          onTokenSelect={moveToken}
          skin={me?.boardSkin || 'default'}
        />
        <div className="sticky bottom-3 z-20 mt-4 flex w-full max-w-[640px] items-center justify-between gap-4 rounded-2xl border border-white/10 bg-navy-800/90 p-4 shadow-xl backdrop-blur lg:static">
          <div className="min-w-0">
            <p className="text-xs uppercase tracking-wide text-slate-400">{match.status === 'ACTIVE' ? 'Turn' : 'Match over'}</p>
            <p className="flex items-center gap-1.5 text-base font-bold leading-tight sm:text-lg" data-testid="turn-label">{botTurn && <Bot className="h-5 w-5 shrink-0 text-accent-400" aria-hidden />}{turnLabel}</p>
            {match.vsComputer && <p className="text-xs text-slate-400">Practice vs computer · {String(match.botDifficulty || '').toLowerCase()}</p>}
            {match.status === 'ACTIVE' && !botTurn && secondsLeft != null && (
              <p className={`flex items-center gap-1 text-sm ${secondsLeft <= 5 ? 'text-red-300' : 'text-slate-400'}`}><Clock className="h-4 w-4" aria-hidden /><span data-testid="turn-timer">{secondsLeft}s</span></p>
            )}
            {isMyTurn && match.awaitingMove && (
              <p className="mt-1 text-sm text-brand-400" data-testid="move-hint">{onlyChoice != null ? 'Only one move — moving automatically…' : 'Pick a highlighted token to move.'}</p>
            )}
          </div>
          <div className="flex flex-col items-center gap-1">
            <Dice
              value={shownDice}
              rolling={diceRolling || (pending && !match.awaitingMove)}
              skin={diceOwner?.diceSkin || 'default'}
              onClick={canRoll ? rollDice : undefined}
              disabled={!canRoll}
              label={canRoll ? 'Roll dice' : undefined}
              size={76}
            />
            <span className="text-xs text-slate-400">{canRoll ? 'Tap to roll' : ''}</span>
          </div>
          <div className="flex flex-col items-center gap-2 self-stretch">
            <button type="button" className="btn-ghost p-2" onClick={() => setMuted(!muted)} aria-label={muted ? 'Unmute sounds' : 'Mute sounds'} data-testid="mute-toggle">
              {muted ? <VolumeX className="h-5 w-5" aria-hidden /> : <Volume2 className="h-5 w-5" aria-hidden />}
            </button>
            {chatEnabled && (
              <button type="button" onClick={() => setChatOpen(true)} data-testid="chat-fab"
                className="relative flex h-10 w-10 items-center justify-center rounded-full bg-brand-500 text-white shadow-glow lg:hidden"
                aria-label={unread ? `Open chat, ${unread} unread` : 'Open chat'}>
                <MessageCircle className="h-5 w-5" aria-hidden />
                {unread > 0 && <span className="absolute -right-1.5 -top-1.5 flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-red-500 px-1 text-[11px] font-bold" data-testid="chat-unread">{unread}</span>}
              </button>
            )}
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
              connected={p.connected} forfeited={p.forfeited} finishedTokens={p.finishedTokens} rank={p.rank}
              bot={p.bot} botDifficulty={p.botDifficulty} thinking={botTurn && p.userId === match.currentUserId} />
          ))}
        </div>
        {chatEnabled && <ChatPanel className="hidden lg:flex" messages={chat} myId={user?.id} onSend={sendChat} disabled={connection !== 'connected'} />}
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

      {finished && match.winnerId === user?.id && <Confetti />}

      <Modal open={chatOpen} onClose={() => setChatOpen(false)} title="Chat" size="sm">
        <ChatPanel bare messages={chat} myId={user?.id} onSend={sendChat} disabled={connection !== 'connected'} />
      </Modal>

      <ConfirmModal open={leaveOpen} title="Leave this match?" danger busy={leaving} confirmLabel="Forfeit"
        message={match.vsComputer ? 'Leaving ends this practice match without a result.' : "You will forfeit the match and lose your place. This can't be undone."} onConfirm={leave} onClose={() => setLeaveOpen(false)} />

      <Modal open={resultOpen} onClose={() => setResultOpen(false)} title={match.winnerId === user?.id ? 'You won! 🎉' : match.status === 'ABANDONED' ? 'Match abandoned' : 'Match finished'} size="sm"
        footer={<><button className="btn-secondary" onClick={() => navigate('/matches')}>Match history</button><button className="btn-primary" onClick={() => navigate('/dashboard')}>Back to dashboard</button></>}>
        <div className="space-y-3">
          <p className="flex items-center gap-2 text-sm text-slate-300"><Trophy className="h-4 w-4 text-yellow-300" aria-hidden />{winner ? `${winner.name} took the win.` : 'The match ended without a winner.'}</p>
          {match.vsComputer && <p className="text-xs text-slate-400" data-testid="practice-note">Practice matches against the computer don't award coins or leaderboard XP.</p>}
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
