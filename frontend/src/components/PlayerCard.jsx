import { Bot, Crown, Trophy, WifiOff } from 'lucide-react';
import { COLOR_HEX } from '../lib/format';
import { Avatar } from './ui';

/** Player strip used in the game room and lobby. */
export default function PlayerCard({ name, avatar, color, active = false, you = false, host = false, ready, connected = true, forfeited = false, finishedTokens, rank, subtitle, bot = false, botDifficulty, thinking = false }) {
  return (
    <div
      className={`flex items-center gap-3 rounded-xl border p-3 transition ${active ? 'border-brand-400 bg-brand-500/10 shadow-glow' : 'border-white/5 bg-navy-800/70'} ${forfeited ? 'opacity-50' : ''}`}
      data-testid="player-card"
      data-active={active ? 'true' : 'false'}
    >
      <div className="relative">
        <Avatar avatar={avatar} name={name} size={40} />
        {color && <span className="absolute -bottom-0.5 -right-0.5 h-4 w-4 rounded-full border-2 border-navy-800" style={{ background: COLOR_HEX[color] }} aria-label={color.toLowerCase()} />}
      </div>
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-1.5 truncate font-semibold">
          {name}
          {you && <span className="badge bg-brand-500/20 text-brand-400">You</span>}
          {host && <Crown className="h-3.5 w-3.5 text-yellow-300" aria-label="Host" />}
          {bot && (
            <span className="badge bg-accent/15 text-accent-400" data-testid="bot-badge">
              <Bot className="mr-1 h-3 w-3" aria-hidden />Computer{botDifficulty ? ` · ${botDifficulty.charAt(0)}${botDifficulty.slice(1).toLowerCase()}` : ''}
            </span>
          )}
        </p>
        <p className="truncate text-xs text-slate-400">
          {forfeited ? 'Left the game' : thinking ? <span className="animate-pulse text-accent-400" data-testid="bot-thinking">Thinking…</span> : subtitle ?? (finishedTokens != null ? `${finishedTokens}/4 home` : '')}
        </p>
      </div>
      {ready != null && (
        <span className={`badge ${ready ? 'bg-emerald-500/15 text-emerald-300' : 'bg-white/10 text-slate-400'}`}>{ready ? 'Ready' : 'Not ready'}</span>
      )}
      {!connected && !forfeited && <WifiOff className="h-4 w-4 text-yellow-300" aria-label="Disconnected" />}
      {rank > 0 && <span className="flex items-center gap-1 text-sm font-bold text-yellow-300"><Trophy className="h-4 w-4" aria-hidden />#{rank}</span>}
    </div>
  );
}
