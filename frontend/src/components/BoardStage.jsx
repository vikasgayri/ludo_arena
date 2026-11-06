import { Bot, Mic, WifiOff } from 'lucide-react';
import { homeCorner } from '../lib/board';
import { COLOR_HEX } from '../lib/format';
import Dice from './Dice';
import { Avatar } from './ui';

// Sizes in px. The dice dock sits right next to each player's avatar, at the corner of that player's home yard.
const STRIP = 90; // height of the player strips above and below the board
const DICE = 50;
const AVATAR = 38;
const PAD = 6;
const GAP = 6;
const DOCK_OFFSET = PAD + AVATAR + GAP; // distance of the dice from the outer edge
const DICE_BOX = DICE + 16; // dice + "ROLL DICE" prompt
const DICE_TOP = (STRIP - DICE_BOX) / 2;

/** Where the dice docks for a colour, computed from the colour's real home corner on the board. */
export function diceDockStyle(color) {
  const corner = homeCorner(color);
  if (!corner) return null;
  return {
    left: corner.side === 'left' ? `${DOCK_OFFSET}px` : `calc(100% - ${DOCK_OFFSET + DICE}px)`,
    top: corner.edge === 'top' ? `${DICE_TOP}px` : `calc(100% - ${STRIP - DICE_TOP}px)`,
  };
}

function Pod({ seat, side, active, speaking, localPlay }) {
  const color = COLOR_HEX[seat.color];
  const status = seat.forfeited ? 'Left the game'
    : active ? (seat.isMe || localPlay ? 'YOUR TURN' : 'TURN')
      : `${seat.finishedTokens ?? 0}/4 home`;
  return (
    <div
      className={`pod flex h-full min-w-0 items-center rounded-2xl border transition-colors ${side === 'right' ? 'flex-row-reverse text-right' : ''} ${active ? 'pod-active border-transparent bg-white/10' : 'border-white/5 bg-white/[0.03]'} ${seat.forfeited ? 'opacity-40' : ''}`}
      style={{ '--pc': color, gap: GAP, paddingLeft: PAD, paddingRight: PAD }}
      data-testid={`pod-${seat.color}`}
      data-active={active ? 'true' : 'false'}
    >
      <div className={`relative shrink-0 rounded-full ${active ? 'pod-avatar-active' : ''}`} style={{ boxShadow: `0 0 0 2.5px ${color}` }}>
        <Avatar avatar={seat.avatar} name={seat.name} size={AVATAR} />
        {speaking && <Mic className="absolute -bottom-1 -right-1 h-4 w-4 rounded-full bg-emerald-500 p-0.5 text-white" aria-label="speaking" />}
        {!seat.connected && !seat.forfeited && <WifiOff className="absolute -bottom-1 -right-1 h-4 w-4 rounded-full bg-navy-900 p-0.5 text-yellow-300" aria-label="disconnected" />}
      </div>
      {/* dice dock: only the active panel makes room for the dice, the others keep the space for the name */}
      <div className="pod-dock shrink-0" style={{ width: active ? DICE : 0, height: DICE, marginLeft: active ? 0 : -GAP }} aria-hidden />
      <div className="min-w-0 flex-1 leading-tight">
        <p className={`flex items-center gap-1 truncate text-sm font-bold ${side === 'right' ? 'justify-end' : ''}`} style={active ? { color } : undefined}>
          {seat.bot && <Bot className="h-3.5 w-3.5 shrink-0 text-accent-400" aria-label="computer" />}
          <span className="truncate">{seat.isMe ? 'You' : seat.name}</span>
        </p>
        <p className={`truncate text-[10px] font-extrabold uppercase ${active ? 'pod-status-active' : 'text-slate-400'}`}
          style={active ? { color } : undefined} data-testid={`pod-status-${seat.color}`}>{status}</p>
      </div>
    </div>
  );
}

/**
 * The play area: player panels at the four corners (each at its colour's home yard), the board in the middle, and one
 * dice that glides to the panel of the player whose turn it is. Pure presentation: who is active, the dice value and
 * whether rolling is allowed all come from the game state (server state online, local engine state offline).
 *
 * seats: [{ userId, name, avatar, color, isMe, bot, forfeited, connected, finishedTokens }]
 * localPlay: pass & play on one device - whoever is to move is "you"
 */
export default function BoardStage({ seats = [], activeColor, speakingIds, dice, banner, notice, localPlay = false, children }) {
  const byCorner = {};
  seats.forEach((s) => {
    const c = homeCorner(s.color);
    if (c) byCorner[`${c.edge}-${c.side}`] = s;
  });
  const dock = diceDockStyle(activeColor) || diceDockStyle(seats[0]?.color) || { left: `${DOCK_OFFSET}px`, top: `${DICE_TOP}px` };
  const strip = (edge) => (
    <div className="grid grid-cols-2 gap-2" style={{ height: STRIP, paddingTop: 4, paddingBottom: 4 }}>
      {['left', 'right'].map((side) => {
        const s = byCorner[`${edge}-${side}`];
        return s
          ? <Pod key={side} seat={s} side={side} active={s.color === activeColor} speaking={speakingIds?.has?.(s.userId)} localPlay={localPlay} />
          : <div key={side} aria-hidden />;
      })}
    </div>
  );
  const activeHex = activeColor ? COLOR_HEX[activeColor] : '#94a3b8';
  return (
    <div className="board-stage relative w-full max-w-[640px]" data-testid="board-stage">
      {strip('top')}
      <div className="relative">
        {children}
        {banner && (
          <div key={banner.key} className="turn-banner pointer-events-none absolute inset-x-0 top-[38%] z-10 flex justify-center" data-testid="turn-banner">
            <div className="rounded-2xl px-6 py-2.5 text-center shadow-2xl" style={{ background: `linear-gradient(135deg, ${COLOR_HEX[banner.color] || '#6366f1'}, rgba(15,23,42,.92))` }}>
              <p className="text-lg font-extrabold uppercase tracking-wide text-white drop-shadow sm:text-2xl">{banner.text}</p>
            </div>
          </div>
        )}
        {notice && (
          <div key={notice.key} className="game-notice pointer-events-none absolute inset-x-0 top-[53%] z-10 flex justify-center px-6" data-testid="game-notice">
            <p className="max-w-[80%] rounded-xl bg-navy-900/85 px-3 py-1.5 text-center text-xs font-semibold text-white shadow-xl ring-1 ring-white/15 sm:text-sm">{notice.text}</p>
          </div>
        )}
        <p className="sr-only" role="status" aria-live="polite">{banner?.text || ''} {notice?.text || ''}</p>
      </div>
      {strip('bottom')}

      <div className="stage-dice absolute z-20 flex flex-col items-center" style={{ ...dock, width: DICE }}
        data-testid="stage-dice" data-dock={activeColor || ''}>
        <div className={`rounded-2xl ${dice?.canRoll ? 'dice-ready' : ''}`} style={{ '--pc': activeHex, boxShadow: activeColor ? `0 0 0 2px ${activeHex}` : undefined }}>
          <Dice value={dice?.value} rolling={!!dice?.rolling} skin={dice?.skin || 'default'} size={DICE}
            onClick={dice?.canRoll ? dice.onRoll : undefined} disabled={!dice?.canRoll} label={dice?.canRoll ? (dice.label || 'Roll dice') : undefined} />
        </div>
        {dice?.canRoll && (
          <span className="roll-prompt mt-1 whitespace-nowrap rounded-full px-2 py-0.5 text-[10px] font-extrabold uppercase tracking-wider text-white"
            style={{ background: activeHex }} data-testid="roll-prompt" aria-hidden>Roll dice</span>
        )}
      </div>
    </div>
  );
}
