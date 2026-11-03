import { useMemo } from 'react';
import { BASE_AREA, BASE_SLOTS, CENTER, HOME_COLUMN, SAFE_CELLS, TRACK, cellFor, stackOffset } from '../lib/board';
import { BOARD_SKINS } from '../lib/cosmetics';
import { COLOR_HEX, COLOR_SOFT } from '../lib/format';
import PlayerToken from './PlayerToken';

/**
 * 15x15 SVG Ludo board. Pure renderer: it draws the server's token positions and highlights the tokens the server says
 * can move (`validMoves`). It never decides what is legal.
 *
 * players: [{ userId, color, tokens:[steps x4] }]
 */
export default function LudoBoard({ players = [], movableTokens = [], movableUserId = null, onTokenSelect, skin = 'default' }) {
  const theme = BOARD_SKINS[skin] || BOARD_SKINS.default;

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
    }));
    return items;
  }, [players]);

  const activeColors = new Set(players.map((p) => p.color));

  return (
    <svg viewBox="0 0 15 15" className="h-auto w-full max-w-[640px] touch-manipulation rounded-2xl shadow-2xl" role="group" aria-label="Ludo board" data-testid="ludo-board">
      <rect x="0" y="0" width="15" height="15" fill={theme.bg} />

      {/* base yards */}
      {Object.entries(BASE_AREA).map(([color, [x, y]]) => (
        <g key={color} opacity={activeColors.has(color) || activeColors.size === 0 ? 1 : 0.35}>
          <rect x={x} y={y} width="6" height="6" fill={COLOR_HEX[color]} />
          <rect x={x + 0.9} y={y + 0.9} width="4.2" height="4.2" rx="0.6" fill={COLOR_SOFT[color]} />
          {BASE_SLOTS[color].map(([c, r]) => <circle key={`${c}-${r}`} cx={c + 0.5} cy={r + 0.5} r="0.5" fill={COLOR_HEX[color]} opacity="0.35" />)}
        </g>
      ))}

      {/* track cells */}
      {TRACK.map(([c, r], i) => (
        <g key={`t${i}`}>
          <rect x={c} y={r} width="1" height="1" fill={theme.track} stroke={theme.line} strokeWidth="0.04" />
          {SAFE_CELLS.includes(i) && (
            <text x={c + 0.5} y={r + 0.68} textAnchor="middle" fontSize="0.6" fill={theme.center} opacity="0.55" aria-hidden>★</text>
          )}
        </g>
      ))}
      {/* start squares tinted */}
      {Object.entries({ RED: 0, GREEN: 13, YELLOW: 26, BLUE: 39 }).map(([color, idx]) => {
        const [c, r] = TRACK[idx];
        return <rect key={`s${color}`} x={c} y={r} width="1" height="1" fill={COLOR_HEX[color]} opacity="0.85" stroke={theme.line} strokeWidth="0.04" />;
      })}

      {/* home columns */}
      {Object.entries(HOME_COLUMN).map(([color, cells]) => cells.map(([c, r]) => (
        <rect key={`h${color}${c}${r}`} x={c} y={r} width="1" height="1" fill={COLOR_HEX[color]} opacity="0.75" stroke={theme.line} strokeWidth="0.04" />
      )))}

      {/* centre */}
      <g>
        <polygon points="6,6 9,6 7.5,7.5" fill={COLOR_HEX.GREEN} />
        <polygon points="9,6 9,9 7.5,7.5" fill={COLOR_HEX.YELLOW} />
        <polygon points="9,9 6,9 7.5,7.5" fill={COLOR_HEX.BLUE} />
        <polygon points="6,9 6,6 7.5,7.5" fill={COLOR_HEX.RED} />
        <circle cx={CENTER[0] + 0.5} cy={CENTER[1] + 0.5} r="0.28" fill={theme.center} />
      </g>

      {/* tokens (movable ones drawn last so they sit on top and are clickable) */}
      {[...placed].sort((a, b) => Number(isMovable(a)) - Number(isMovable(b))).map((t) => (
        <PlayerToken
          key={t.key}
          color={t.color}
          index={t.index}
          cx={t.x}
          cy={t.y}
          movable={isMovable(t)}
          onSelect={onTokenSelect}
        />
      ))}
    </svg>
  );

  function isMovable(t) {
    return movableUserId != null && t.userId === movableUserId && movableTokens.includes(t.index);
  }
}
