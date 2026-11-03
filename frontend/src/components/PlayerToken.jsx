import { COLOR_HEX } from '../lib/format';

/** One token drawn as an SVG group at (cx, cy) in board units. `movable` is set by the server's validMoves. */
export default function PlayerToken({ color, cx, cy, index, movable = false, onSelect, radius = 0.36, label }) {
  const fill = COLOR_HEX[color] || '#999';
  const handle = () => { if (movable && onSelect) onSelect(index); };
  return (
    <g
      transform={`translate(${cx} ${cy})`}
      className={movable ? 'token-movable' : ''}
      style={{ transformBox: 'fill-box', transformOrigin: 'center' }}
      role={movable ? 'button' : 'img'}
      tabIndex={movable ? 0 : -1}
      aria-label={label || `${color.toLowerCase()} token ${index + 1}${movable ? ', movable' : ''}`}
      data-testid={`token-${color}-${index}`}
      data-movable={movable ? 'true' : 'false'}
      onClick={handle}
      onKeyDown={(e) => { if (movable && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); handle(); } }}
    >
      <circle r={radius + 0.06} fill="rgba(0,0,0,.35)" cy="0.05" />
      <circle r={radius} fill={fill} stroke="#fff" strokeWidth="0.07" />
      <circle r={radius * 0.45} fill="rgba(255,255,255,.35)" />
      <text textAnchor="middle" dy="0.12" fontSize="0.34" fontWeight="700" fill="#fff" style={{ pointerEvents: 'none' }}>{index + 1}</text>
      {movable && <circle r={radius + 0.14} fill="none" stroke="#fff" strokeWidth="0.06" strokeDasharray="0.15 0.12" />}
    </g>
  );
}
