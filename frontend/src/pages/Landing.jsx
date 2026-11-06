import { Link } from 'react-router-dom';
import { Coins, ShieldCheck, Swords, Trophy, Zap } from 'lucide-react';
import LudoBoard from '../components/LudoBoard';

const DEMO_PLAYERS = [
  { userId: 'a', color: 'RED', tokens: [-1, 4, 20, -1] },
  { userId: 'b', color: 'GREEN', tokens: [-1, -1, 12, 53] },
  { userId: 'c', color: 'YELLOW', tokens: [30, -1, -1, 8] },
  { userId: 'd', color: 'BLUE', tokens: [-1, 14, -1, -1] },
];

const FEATURES = [
  { icon: Swords, title: 'Real-time multiplayer', text: '2 or 4 players, live over WebSocket. Reconnect any time and pick up where you left off.' },
  { icon: ShieldCheck, title: 'Fair by design', text: 'Dice are rolled on the server with a secure RNG. Every rule is enforced server-side.' },
  { icon: Coins, title: 'Virtual coins only', text: 'Earn coins by playing and spend them on cosmetics. No betting, no cash-outs.' },
  { icon: Trophy, title: 'Ranks & achievements', text: 'Climb the leaderboard, level up and unlock achievements.' },
];

export default function Landing() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-8">
      <section className="grid items-center gap-10 lg:grid-cols-2">
        <div>
          <span className="badge bg-brand-500/20 text-brand-400"><Zap className="mr-1 h-3 w-3" aria-hidden />Free to play</span>
          <h1 className="mt-4 text-4xl font-extrabold tracking-tight sm:text-5xl">The classic game of Ludo, <span className="bg-gradient-to-r from-brand-400 to-accent-400 bg-clip-text text-transparent">built for the arena.</span></h1>
          <p className="mt-4 max-w-xl text-slate-300">Create a room, invite friends and race your four tokens home. Fast matchmaking, fair dice and cosmetic rewards — all with virtual coins that never touch real money.</p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Link to="/register" className="btn-primary px-6 py-3">Create free account</Link>
            <Link to="/login" className="btn-secondary px-6 py-3">I already have one</Link>
            <Link to="/local" className="btn-ghost px-6 py-3" data-testid="landing-local">Play offline with friends</Link>
          </div>
        </div>
        <div className="flex justify-center"><LudoBoard players={DEMO_PLAYERS} /></div>
      </section>
      <section className="mt-16 grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Features">
        {FEATURES.map(({ icon: Icon, title, text }) => (
          <div key={title} className="card">
            <Icon className="h-6 w-6 text-brand-400" aria-hidden />
            <h2 className="mt-3 font-bold">{title}</h2>
            <p className="mt-1 text-sm text-slate-400">{text}</p>
          </div>
        ))}
      </section>
    </div>
  );
}
