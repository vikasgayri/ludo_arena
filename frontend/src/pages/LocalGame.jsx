import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Menu, Play, RotateCcw, Smartphone, Trophy, Volume2, VolumeX, X } from 'lucide-react';
import { useAuthStore } from '../store/authStore';
import { COLORS_FOR, isValidSaved, move, newGame, roll, rollValue } from '../lib/localEngine';
import { prefersReducedMotion, useAnimatedPlayers } from '../lib/animation';
import { useDiceRoll, useGameNotice, useGameSounds, useTurnBanner } from '../lib/gameFx';
import { isMuted, onMutedChange, play, setMuted, startMusic, stopMusic } from '../lib/sound';
import { describeEvent } from '../lib/events';
import { COLOR_HEX } from '../lib/format';
import LudoBoard from '../components/LudoBoard';
import BoardStage from '../components/BoardStage';
import GameMenu from '../components/GameMenu';
import PlayerCard from '../components/PlayerCard';
import Confetti from '../components/Confetti';
import Modal from '../components/Modal';

const SAVE_KEY = 'ludo-arena:local-game';
const NAMES_KEY = 'ludo-arena:local-names';
const AUTO_MOVE_DELAY_MS = 120;
const COLOR_NAME = { RED: 'Red', GREEN: 'Green', YELLOW: 'Yellow', BLUE: 'Blue' };

// Browser storage is only a convenience (resume after a refresh); every access is guarded.
const store = {
  read(key) {
    try { const raw = window.localStorage.getItem(key); return raw ? JSON.parse(raw) : null; } catch { return null; }
  },
  write(key, value) {
    try { window.localStorage.setItem(key, JSON.stringify(value)); } catch { /* storage unavailable */ }
  },
  remove(key) {
    try { window.localStorage.removeItem(key); } catch { /* storage unavailable */ }
  },
};

function Setup({ onStart, saved, onResume }) {
  const signedIn = useAuthStore((s) => !!s.user);
  const [count, setCount] = useState(() => store.read(NAMES_KEY)?.length || 2);
  const [names, setNames] = useState(() => {
    const prev = store.read(NAMES_KEY);
    return Array.from({ length: 4 }, (_, i) => (Array.isArray(prev) && typeof prev[i] === 'string' ? prev[i] : ''));
  });
  const colors = COLORS_FOR[count];
  const start = (e) => {
    e.preventDefault();
    const list = names.slice(0, count).map((n, i) => n.trim().slice(0, 16) || `Player ${i + 1}`);
    store.write(NAMES_KEY, list);
    onStart(list);
  };
  return (
    <div className="mx-auto max-w-lg px-4 py-6">
      <Link to={signedIn ? '/play' : '/'} className="btn-ghost mb-2 -ml-2 inline-flex"><ArrowLeft className="h-4 w-4" aria-hidden />Back</Link>
      <div className="mb-5 flex items-start gap-3">
        <div className="rounded-xl bg-white/5 p-3 text-emerald-300"><Smartphone className="h-6 w-6" aria-hidden /></div>
        <div>
          <h1 className="text-2xl font-extrabold">Pass &amp; play</h1>
          <p className="text-sm text-slate-400">Friends take turns on this phone. Works offline — no account needed.</p>
        </div>
      </div>
      {saved && (
        <div className="card mb-4 flex items-center justify-between gap-3" data-testid="resume-card">
          <div className="min-w-0">
            <p className="font-semibold">Game in progress</p>
            <p className="truncate text-xs text-slate-400">{saved.players.map((p) => p.name).join(' · ')}</p>
          </div>
          <button type="button" className="btn-primary" onClick={onResume}><Play className="h-4 w-4" aria-hidden />Resume</button>
        </div>
      )}
      <form className="card space-y-5" onSubmit={start}>
        <fieldset>
          <legend className="label">Players</legend>
          <div className="grid grid-cols-3 gap-3">
            {[2, 3, 4].map((n) => (
              <label key={n} className={`cursor-pointer rounded-xl border p-3 text-center transition ${count === n ? 'border-brand-400 bg-brand-500/10' : 'border-white/10 hover:bg-white/5'}`}>
                <input type="radio" name="local-count" className="sr-only" checked={count === n} onChange={() => setCount(n)} />
                <span className="block text-2xl font-bold">{n}</span>
                <span className="text-xs text-slate-400">players</span>
              </label>
            ))}
          </div>
        </fieldset>
        <div className="space-y-3">
          {colors.map((c, i) => (
            <label key={c} className="flex items-center gap-3">
              <span className="h-8 w-8 shrink-0 rounded-full border-2 border-white/70 shadow" style={{ background: COLOR_HEX[c] }} aria-hidden />
              <span className="sr-only">{COLOR_NAME[c]} player name</span>
              <input className="input" value={names[i]} maxLength={16} placeholder={`Player ${i + 1} (${COLOR_NAME[c]})`} data-testid={`local-name-${i}`}
                onChange={(e) => setNames((cur) => cur.map((v, j) => (j === i ? e.target.value : v)))} />
            </label>
          ))}
        </div>
        <button className="btn-primary w-full" data-testid="local-start"><Play className="h-4 w-4" aria-hidden />Start game</button>
      </form>
    </div>
  );
}

/** Offline pass & play: the whole game runs in this browser with the same rules as the server engine. */
export default function LocalGame() {
  const [game, setGame] = useState(null);
  const [lastEvents, setLastEvents] = useState([]);
  const [log, setLog] = useState([]);
  const [saved, setSaved] = useState(() => {
    const s = store.read(SAVE_KEY);
    return isValidSaved(s) && s.status === 'ACTIVE' ? s : null;
  });
  const [menuOpen, setMenuOpen] = useState(false); // also pauses the local game (no rolls / auto-moves)
  const [resultOpen, setResultOpen] = useState(false);
  const [muted, setMutedState] = useState(isMuted());
  useEffect(() => onMutedChange(setMutedState), []);

  const apply = ({ state, events }) => {
    setGame(state);
    setLastEvents(events);
    setLog((cur) => [...cur, ...events].slice(-60));
    if (state.status === 'ACTIVE') store.write(SAVE_KEY, state);
    else store.remove(SAVE_KEY);
  };

  const start = (names) => { setLog([]); setResultOpen(false); apply(newGame(names)); };
  const resume = () => { setLog([]); setGame(saved); setLastEvents([]); setSaved(null); };
  /** Ends the local game completely: no saved copy, no events, back to the offline setup screen. */
  const endGame = () => {
    store.remove(SAVE_KEY);
    setMenuOpen(false);
    setResultOpen(false);
    setGame(null);
    setLastEvents([]);
    setLog([]);
    setSaved(null);
  };
  useEffect(() => {
    if (!game) return undefined;
    startMusic();
    return () => stopMusic();
  }, [!game]); // eslint-disable-line react-hooks/exhaustive-deps

  const animationsOn = !prefersReducedMotion();
  const { players: shownPlayers, animating } = useAnimatedPlayers(game?.players || [], { enabled: animationsOn });
  const diceRolling = useDiceRoll(lastEvents, null);
  useGameSounds(lastEvents, game?.currentUserId);

  const current = game?.players[game.turnIndex];
  const active = game?.status === 'ACTIVE';
  const settled = !diceRolling && !animating && !menuOpen;
  const canRoll = active && !game.awaitingMove && settled;
  const movable = active && game.awaitingMove && settled ? game.validMoves : [];

  const doRoll = () => { if (canRoll) apply(roll(game, rollValue())); };
  const doMove = (i) => { if (active && game.awaitingMove && game.validMoves.includes(i)) apply(move(game, i)); };

  // one real choice (one legal token, or all legal tokens on the same spot): move it automatically
  const onlyChoice = useMemo(() => {
    if (!active || !game.awaitingMove) return null;
    const moves = game.validMoves;
    return moves.length && new Set(moves.map((i) => current.tokens[i])).size === 1 ? moves[0] : null;
  }, [active, game, current]);
  const autoSeq = useRef(null);
  useEffect(() => {
    if (onlyChoice == null || !settled || autoSeq.current === game.seq) return undefined;
    const t = setTimeout(() => { autoSeq.current = game.seq; apply(move(game, onlyChoice)); }, AUTO_MOVE_DELAY_MS);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [onlyChoice, settled, game?.seq]);

  const finished = game && game.status !== 'ACTIVE';
  useEffect(() => { if (finished) setResultOpen(true); }, [finished]);

  const nameOf = useMemo(() => {
    const m = new Map((game?.players || []).map((p) => [p.userId, p.name]));
    return (id) => m.get(id) || 'Someone';
  }, [game]);

  const turnBanner = useTurnBanner(game?.currentUserId, active, true);
  const notice = useGameNotice(lastEvents, nameOf);

  if (!game) return <Setup onStart={start} saved={saved} onResume={resume} />;
  const bannerPlayer = turnBanner && game.players.find((p) => p.userId === turnBanner.userId);
  const banner = bannerPlayer ? { key: turnBanner.key, color: bannerPlayer.color, text: `${bannerPlayer.name}'s Turn` } : null;

  const winner = game.players.find((p) => p.userId === game.winnerId);
  const turnColor = COLOR_HEX[current.color];
  return (
    <div className="mx-auto grid max-w-6xl gap-6 px-3 py-4 lg:grid-cols-[minmax(0,1fr)_300px]">
      <div className="flex flex-col items-center">
        <div className="mb-2 flex w-full max-w-[640px] items-center justify-between gap-2">
          <button type="button" className="btn-secondary px-3 py-1.5" onClick={() => { play('click'); setMenuOpen(true); }} aria-label="Open game menu" data-testid="menu-button">
            <Menu className="h-4 w-4" aria-hidden />Menu
          </button>
          <span className="badge bg-emerald-500/15 text-emerald-300"><Smartphone className="mr-1 h-3 w-3" aria-hidden />Offline</span>
          <button type="button" className="btn-ghost p-2" onClick={() => setMuted(!muted)} aria-label={muted ? 'Unmute sounds' : 'Mute sounds'}>
            {muted ? <VolumeX className="h-5 w-5" aria-hidden /> : <Volume2 className="h-5 w-5" aria-hidden />}
          </button>
        </div>
        <BoardStage
          localPlay
          seats={game.players.map((p) => ({ ...p, avatar: p.avatar || `avatar-0${p.seat + 1}`, isMe: false }))}
          activeColor={active ? current.color : null}
          banner={banner}
          notice={notice}
          dice={{
            value: game.diceValue ?? game.lastDiceValue,
            rolling: diceRolling,
            canRoll,
            onRoll: doRoll,
            label: `Roll dice for ${current.name}`,
          }}
        >
          <LudoBoard
            activeColor={active ? current.color : null}
            players={shownPlayers}
            movableTokens={movable}
            movableUserId={active ? current.userId : null}
            onTokenSelect={doMove}
          />
        </BoardStage>
        <div className="relative mt-3 flex w-full max-w-[640px] items-center justify-between gap-4 rounded-2xl border-2 bg-navy-800/90 p-4 shadow-xl backdrop-blur"
          style={{ borderColor: active ? turnColor : 'rgba(255,255,255,.1)' }} data-testid="local-turn-bar">
          <div className="min-w-0">
            <p className="text-xs uppercase tracking-wide text-slate-400">{active ? 'Turn' : 'Game over'}</p>
            <p className="truncate text-lg font-extrabold leading-tight" style={{ color: active ? turnColor : undefined }} data-testid="local-turn-label">
              {active ? `${current.name}'s turn` : winner ? `${winner.name} won!` : 'Finished'}
            </p>
            {active && (
              <p className="mt-1 text-sm text-slate-300">
                {game.awaitingMove ? (onlyChoice != null ? 'Moving…' : 'Tap a glowing token') : `Pass the phone · ${COLOR_NAME[current.color]} rolls`}
              </p>
            )}
          </div>
        </div>
      </div>

      <aside className="space-y-4" aria-label="Game info">
        <div className="space-y-2">
          {game.players.map((p) => (
            <PlayerCard key={p.userId} name={p.name} avatar={p.avatar || `avatar-0${p.seat + 1}`} color={p.color} active={active && p.userId === current.userId}
              forfeited={p.forfeited} finishedTokens={p.finishedTokens} rank={p.rank || undefined} />
          ))}
        </div>
        <div className="card p-4">
          <h3 className="mb-2 text-sm font-bold">Game log</h3>
          <ol className="max-h-64 space-y-1 overflow-y-auto text-sm text-slate-300" aria-live="polite">
            {[...log].reverse().slice(0, 40).map((e) => <li key={e.eventId}>{describeEvent(e, nameOf)}</li>)}
          </ol>
        </div>
        <button type="button" className="btn-secondary w-full" onClick={() => setMenuOpen(true)}><RotateCcw className="h-4 w-4" aria-hidden />Menu / new game</button>
      </aside>

      {finished && winner && <Confetti />}

      <GameMenu open={menuOpen} onClose={() => setMenuOpen(false)} onLeave={endGame}
        leaveMessage={active ? 'This game on this phone will end and cannot be resumed. You can start a new one with the same names.' : 'Back to the offline menu?'} />

      <Modal open={resultOpen} onClose={() => setResultOpen(false)} title={winner ? `${winner.name} wins! 🎉` : 'Game over'} size="sm"
        footer={<><button className="btn-secondary" onClick={endGame}><X className="h-4 w-4" aria-hidden />Close</button>
          <button className="btn-primary" onClick={() => start(game.players.map((p) => p.name))} data-testid="local-rematch"><RotateCcw className="h-4 w-4" aria-hidden />Play again</button></>}>
        <ol className="space-y-1 text-sm">
          {[...game.players].sort((a, b) => (a.rank || 99) - (b.rank || 99)).map((p) => (
            <li key={p.userId} className="flex items-center justify-between">
              <span className="flex items-center gap-2">{p.rank === 1 && <Trophy className="h-4 w-4 text-yellow-300" aria-hidden />}{p.rank ? `#${p.rank}` : '—'} {p.name}</span>
              <span className="text-slate-400">{p.finishedTokens}/4 home</span>
            </li>
          ))}
        </ol>
      </Modal>
    </div>
  );
}
