import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Bot, Globe, KeyRound, Smartphone, Swords } from 'lucide-react';
import { RoomApi, errorMessage } from '../lib/api';
import { toast } from '../store/toastStore';
import { PageHeader, Spinner } from '../components/ui';

export const DIFFICULTIES = [
  { value: 'EASY', label: 'Easy', hint: 'Plays random legal moves. Good for learning.' },
  { value: 'MEDIUM', label: 'Medium', hint: 'Captures, unlocks and avoids obvious danger.' },
  { value: 'HARD', label: 'Hard', hint: 'Weighs every move: safety, threats and home progress.' },
];

/** Game mode selection: online lobby, private rooms, or a practice match against the computer. */
export default function PlayMode() {
  const navigate = useNavigate();
  const [players, setPlayers] = useState(2);
  const [difficulty, setDifficulty] = useState('MEDIUM');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const startBotGame = async () => {
    setBusy(true);
    setError(null);
    try {
      const res = await RoomApi.createBotGame({ players, difficulty });
      toast.success('Match against the computer started');
      navigate(`/game/${res.matchId}`);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader title="Play" subtitle="Choose how you want to play." />

      <div className="grid gap-4 md:grid-cols-2">
        <Link to="/lobby" className="card flex items-start gap-4 transition hover:border-brand-400/50" data-testid="mode-online">
          <div className="rounded-xl bg-white/5 p-3 text-accent-400"><Globe className="h-6 w-6" aria-hidden /></div>
          <div>
            <h2 className="font-bold">Online match</h2>
            <p className="text-sm text-slate-400">Join an open public table with other players.</p>
          </div>
        </Link>
        <div className="card flex items-start gap-4" data-testid="mode-private">
          <div className="rounded-xl bg-white/5 p-3 text-yellow-300"><KeyRound className="h-6 w-6" aria-hidden /></div>
          <div className="min-w-0 flex-1">
            <h2 className="font-bold">Private room</h2>
            <p className="text-sm text-slate-400">Play with friends using a 6-character room code.</p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Link to="/rooms/create" className="btn-secondary"><Swords className="h-4 w-4" aria-hidden />Create</Link>
              <Link to="/rooms/join" className="btn-secondary">Join with code</Link>
            </div>
          </div>
        </div>
      </div>

      <Link to="/local" className="card mt-4 flex items-start gap-4 transition hover:border-brand-400/50" data-testid="mode-local">
        <div className="rounded-xl bg-white/5 p-3 text-emerald-300"><Smartphone className="h-6 w-6" aria-hidden /></div>
        <div>
          <h2 className="font-bold">Offline · pass &amp; play</h2>
          <p className="text-sm text-slate-400">2–4 friends on one phone. No internet needed, take turns on the same screen.</p>
        </div>
      </Link>

      <section className="card mt-4 space-y-5" aria-labelledby="vs-computer" data-testid="mode-bot">
        <div className="flex items-start gap-4">
          <div className="rounded-xl bg-white/5 p-3 text-brand-400"><Bot className="h-6 w-6" aria-hidden /></div>
          <div>
            <h2 id="vs-computer" className="font-bold">Play with Computer</h2>
            <p className="text-sm text-slate-400">Practice against bots. Practice matches do not award coins or leaderboard XP.</p>
          </div>
        </div>

        <fieldset>
          <legend className="label">Opponents</legend>
          <div className="grid grid-cols-3 gap-3">
            {[2, 3, 4].map((n) => (
              <label key={n} className={`cursor-pointer rounded-xl border p-3 text-center transition ${players === n ? 'border-brand-400 bg-brand-500/10' : 'border-white/10 hover:bg-white/5'}`}>
                <input type="radio" name="bot-players" className="sr-only" checked={players === n} onChange={() => setPlayers(n)} />
                <span className="block text-2xl font-bold">{n - 1}</span>
                <span className="text-xs text-slate-400">{n === 2 ? 'computer · 2 players' : `computers · ${n} players`}</span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="label">Difficulty</legend>
          <div className="grid gap-3 sm:grid-cols-3">
            {DIFFICULTIES.map((d) => (
              <label key={d.value} className={`cursor-pointer rounded-xl border p-3 transition ${difficulty === d.value ? 'border-brand-400 bg-brand-500/10' : 'border-white/10 hover:bg-white/5'}`}>
                <input type="radio" name="bot-difficulty" className="sr-only" value={d.value} checked={difficulty === d.value} onChange={() => setDifficulty(d.value)} />
                <span className="block font-semibold">{d.label}</span>
                <span className="text-xs text-slate-400">{d.hint}</span>
              </label>
            ))}
          </div>
        </fieldset>

        {error && <p role="alert" className="rounded-xl bg-red-500/10 px-3 py-2 text-sm text-red-300">{error}</p>}
        <button type="button" className="btn-primary w-full sm:w-auto" onClick={startBotGame} disabled={busy}>
          {busy ? <Spinner className="h-4 w-4" /> : <Bot className="h-4 w-4" aria-hidden />}Start match
        </button>
      </section>
    </div>
  );
}
