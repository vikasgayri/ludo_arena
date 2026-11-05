import { useId, useMemo } from 'react';
import { BASE_AREA, BASE_SLOTS, CENTER, COLOR_START, HOME_COLUMN, SAFE_CELLS, TRACK, cellFor, globalIndex, stackOffset } from '../lib/board';
import { BOARD_SKINS } from '../lib/cosmetics';
import { COLOR_DARK, COLOR_HEX, COLOR_LIGHT } from '../lib/format';
import PlayerToken from './PlayerToken';

const COLORS = ['RED', 'GREEN', 'YELLOW', 'BLUE'];
/** direction of the arrow that points from each colour's last track cell into its home column */
const HOME_ARROW_ROTATION = { RED: 0, GREEN: 90, YELLOW: 180, BLUE: 270 };

function Star({ cx, cy, r, fill, opacity = 1 }) {
  const pts = [];
  for (let i = 0; i < 10; i += 1) {
    const a = (Math.PI / 5) * i - Math.PI / 2;
    const rr = i % 2 === 0 ? r : r * 0.45;
    pts.push(`${(cx + Math.cos(a) * rr).toFixed(3)},${(cy + Math.sin(a) * rr).toFixed(3)}`);
  }
  return <polygon points={pts.join(' ')} fill={fill} opacity={opacity} aria-hidden />;
}

/**
 * 15x15 SVG Ludo board in a classic premium style. Pure renderer: it draws the token positions it is given and
 * highlights the tokens the server says can move (`validMoves`). It never decides what is legal.
 *
 * players: [{ userId, color, tokens:[steps x4] }]
 * activeColor: colour whose turn it is (its yard glows)
 */
export default function LudoBoard({ players = [], movableTokens = [], movableUserId = null, onTokenSelect, skin = 'default', activeColor = null }) {
  const theme = BOARD_SKINS[skin] || BOARD_SKINS.default;
  const uid = useId().replace(/[^a-zA-Z0-9]/g, '');
  const grad = (color) => `tok-${uid}-${color}`;
  const yard = (color) => `yard-${uid}-${color}`;

  // Group tokens by drawn cell so overlapping tokens get small offsets.
  const placed = useMemo(() => {
    const items = [];
    players.forEach((p) => {
      (p.tokens || []).forEach((steps, index) => {
        const [c, r] = cellFor(p.color, steps, index);
        items.push({ key: `${p.color}-${index}`, color: p.color, userId: p.userId, index, steps, c, r });
      });
    });
    const groups = new Map();
    items.forEach((it) => {
      const k = `${it.c},${it.r}`;
      if (!groups.has(k)) groups.set(k, []);
      groups.get(k).push(it);
    });
    groups.forEach((list) => list.forEach((it, i) => {
      const [dx, dy] = stackOffset(i, list.length);
      it.x = it.c + 0.5 + dx;
      it.y = it.r + 0.5 + dy;
      it.scale = list.length > 1 ? 0.8 : 1;
    }));
    return items;
  }, [players]);

  const activeColors = new Set(players.map((p) => p.color));
  const isMovable = (t) => movableUserId != null && t.userId === movableUserId && movableTokens.includes(t.index);
  // stable DOM order (keys) so CSS transitions are not interrupted; movable tokens are drawn last to stay clickable
  const ordered = [...placed].sort((a, b) => Number(isMovable(a)) - Number(isMovable(b)) || a.key.localeCompare(b.key));

  return (
    <svg viewBox="-0.25 -0.25 15.5 15.5" className="board-svg h-auto w-full max-w-[640px] touch-manipulation select-none" role="group" aria-label="Ludo board" data-testid="ludo-board">
      <defs>
        {COLORS.map((c) => (
          <radialGradient key={c} id={grad(c)} cx="38%" cy="32%" r="70%">
            <stop offset="0%" stopColor={COLOR_LIGHT[c]} />
            <stop offset="55%" stopColor={COLOR_HEX[c]} />
            <stop offset="100%" stopColor={COLOR_DARK[c]} />
          </radialGradient>
        ))}
        {COLORS.map((c) => (
          <linearGradient key={c} id={yard(c)} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={COLOR_HEX[c]} />
            <stop offset="100%" stopColor={COLOR_DARK[c]} />
          </linearGradient>
        ))}
      </defs>

      {/* frame */}
      <rect x="-0.25" y="-0.25" width="15.5" height="15.5" rx="0.55" fill="#3b2a1a" />
      <rect x="-0.08" y="-0.08" width="15.16" height="15.16" rx="0.4" fill="#c99a5b" />
      <rect x="0" y="0" width="15" height="15" rx="0.25" fill={theme.bg} />

      {/* base yards */}
      {Object.entries(BASE_AREA).map(([color, [x, y]]) => {
        const dim = !(activeColors.has(color) || activeColors.size === 0);
        return (
          <g key={color} opacity={dim ? 0.35 : 1}>
            <rect x={x} y={y} width="6" height="6" fill={`url(#${yard(color)})`} />
            <rect x={x + 0.75} y={y + 0.75} width="4.5" height="4.5" rx="0.55" fill="#fff" />
            {BASE_SLOTS[color].map(([c, r]) => (
              <g key={`${c}-${r}`}>
                <circle cx={c + 0.5} cy={r + 0.5} r="0.5" fill={COLOR_HEX[color]} opacity="0.18" />
                <circle cx={c + 0.5} cy={r + 0.5} r="0.5" fill="none" stroke={COLOR_HEX[color]} strokeWidth="0.07" />
              </g>
            ))}
            {activeColor === color && (
              <rect x={x + 0.12} y={y + 0.12} width="5.76" height="5.76" rx="0.3" fill="none" stroke="#fff" strokeWidth="0.14" className="yard-active" data-testid={`yard-active-${color}`} />
            )}
          </g>
        );
      })}

      {/* track cells */}
      {TRACK.map(([c, r], i) => (
        <rect key={`t${i}`} x={c} y={r} width="1" height="1" fill={theme.track} stroke={theme.line} strokeWidth="0.04" />
      ))}

      {/* home columns */}
      {Object.entries(HOME_COLUMN).map(([color, cells]) => cells.map(([c, r]) => (
        <rect key={`h${color}${c}${r}`} x={c} y={r} width="1" height="1" fill={COLOR_HEX[color]} stroke={theme.line} strokeWidth="0.04" />
      )))}

      {/* start squares (coloured, white star) and star safe cells */}
      {Object.entries(COLOR_START).map(([color, idx]) => {
        const [c, r] = TRACK[idx];
        return (
          <g key={`s${color}`}>
            <rect x={c} y={r} width="1" height="1" fill={COLOR_HEX[color]} stroke={theme.line} strokeWidth="0.04" />
            <Star cx={c + 0.5} cy={r + 0.5} r={0.36} fill="#fff" opacity={0.9} />
          </g>
        );
      })}
      {SAFE_CELLS.filter((i) => !Object.values(COLOR_START).includes(i)).map((i) => {
        const [c, r] = TRACK[i];
        return <Star key={`star${i}`} cx={c + 0.5} cy={r + 0.5} r={0.36} fill={theme.line} opacity={0.9} />;
      })}

      {/* arrows into each home column */}
      {COLORS.map((color) => {
        const [c, r] = TRACK[globalIndex(color, 50)];
        return (
          <g key={`arrow${color}`} transform={`translate(${c + 0.5} ${r + 0.5}) rotate(${HOME_ARROW_ROTATION[color]})`} aria-hidden>
            <polygon points="-0.28,-0.22 0.18,0 -0.28,0.22" fill={COLOR_HEX[color]} />
          </g>
        );
      })}

      {/* centre (home) */}
      <g>
        <polygon points="6,6 9,6 7.5,7.5" fill={`url(#${yard('GREEN')})`} />
        <polygon points="9,6 9,9 7.5,7.5" fill={`url(#${yard('YELLOW')})`} />
        <polygon points="9,9 6,9 7.5,7.5" fill={`url(#${yard('BLUE')})`} />
        <polygon points="6,9 6,6 7.5,7.5" fill={`url(#${yard('RED')})`} />
        <circle cx={CENTER[0] + 0.5} cy={CENTER[1] + 0.5} r="0.42" fill="#fff" opacity="0.92" />
        <Star cx={CENTER[0] + 0.5} cy={CENTER[1] + 0.5} r={0.3} fill="#f7b500" />
      </g>

      {/* tokens */}
      {ordered.map((t) => (
        <PlayerToken
          key={t.key}
          color={t.color}
          index={t.index}
          cx={t.x}
          cy={t.y}
          scale={t.scale}
          gradientId={grad(t.color)}
          movable={isMovable(t)}
          onSelect={onTokenSelect}
        />
      ))}
    </svg>
  );
}
