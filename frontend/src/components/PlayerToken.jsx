import { COLOR_DARK, COLOR_HEX } from '../lib/format';
import { STEP_MS } from '../lib/animation';

/**
 * One 3D-looking pawn at (cx, cy) in board units. The outer group glides between cells with a CSS transition (smooth
 * step-by-step walking); the inner group carries the "movable" pulse so the two transforms never fight.
 * `gradientId` points to the colour's radial gradient defined once by the board.
 */
export default function PlayerToken({ color, cx, cy, index, movable = false, onSelect, radius = 0.36, label, gradientId, scale = 1 }) {
  const fill = gradientId ? `url(#${gradientId})` : COLOR_HEX[color] || '#999';
  const handle = () => { if (movable && onSelect) onSelect(index); };
  const r = radius * scale;
  return (
    <g
      style={{ transform: `translate(${cx}px, ${cy}px)`, transition: `transform ${STEP_MS - 10}ms cubic-bezier(.3,.7,.4,1)` }}
      role={movable ? 'button' : 'img'}
      tabIndex={movable ? 0 : -1}
      aria-label={label || `${color.toLowerCase()} token ${index + 1}${movable ? ', movable' : ''}`}
      data-testid={`token-${color}-${index}`}
      data-movable={movable ? 'true' : 'false'}
      onClick={handle}
      // a tap/click must not give the token keyboard focus: browsers draw their focus ring in board units,
      // which shows up as a huge blue/white blob on the scaled SVG board (keyboard focus via Tab still works)
      onMouseDown={(e) => e.preventDefault()}
      onKeyDown={(e) => { if (movable && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); handle(); } }}
      className={`token-g ${movable ? 'cursor-pointer' : ''}`}
    >
      {/* larger invisible hit area for fingers on mobile */}
      {movable && <circle r={0.62} fill="transparent" />}
      <g className={movable ? 'token-movable' : ''} style={{ transformBox: 'fill-box', transformOrigin: 'center' }}>
        <ellipse rx={r * 0.95} ry={r * 0.42} cy={r * 0.72} fill="rgba(0,0,0,.28)" />
        {movable && <circle r={r + 0.12} fill="#fff" className="token-halo" />}
        <circle r={r} fill={fill} stroke={COLOR_DARK[color] || '#333'} strokeWidth="0.05" />
        <circle r={r * 0.62} fill="none" stroke="rgba(255,255,255,.85)" strokeWidth="0.06" />
        <ellipse rx={r * 0.38} ry={r * 0.22} cx={-r * 0.22} cy={-r * 0.36} fill="rgba(255,255,255,.55)" />
        <text textAnchor="middle" dy="0.11" fontSize={0.3 * scale} fontWeight="800" fill="#fff" stroke={COLOR_DARK[color] || '#333'} strokeWidth="0.02" paintOrder="stroke" style={{ pointerEvents: 'none' }}>{index + 1}</text>
      </g>
    </g>
  );
}
