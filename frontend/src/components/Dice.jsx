import { useEffect, useId, useRef, useState } from 'react';
import { DICE_SKINS } from '../lib/cosmetics';

const PIPS = {
  1: [[2, 2]],
  2: [[1, 1], [3, 3]],
  3: [[1, 1], [2, 2], [3, 3]],
  4: [[1, 1], [3, 1], [1, 3], [3, 3]],
  5: [[1, 1], [3, 1], [2, 2], [1, 3], [3, 3]],
  6: [[1, 1], [3, 1], [1, 2], [3, 2], [1, 3], [3, 3]],
};

/**
 * Presentational dice. The value always comes from the server; while `rolling` the faces shuffle purely for show,
 * then the server value "lands" with a small bounce.
 */
export default function Dice({ value, rolling = false, skin = 'default', size = 72, onClick, disabled, label }) {
  const s = DICE_SKINS[skin] || DICE_SKINS.default;
  const gid = useId().replace(/[^a-zA-Z0-9]/g, '');
  const [face, setFace] = useState(null);
  const [landed, setLanded] = useState(0);
  const wasRolling = useRef(rolling);

  useEffect(() => {
    if (!rolling) {
      if (wasRolling.current) setLanded((n) => n + 1); // replay the landing bounce
      wasRolling.current = false;
      setFace(null);
      return undefined;
    }
    wasRolling.current = true;
    const t = setInterval(() => setFace(1 + Math.floor(Math.random() * 6)), 85);
    return () => clearInterval(t);
  }, [rolling]);

  const shown = rolling && face ? face : value;
  const pips = PIPS[shown] || [];
  const interactive = typeof onClick === 'function';
  const Tag = interactive ? 'button' : 'div';
  return (
    <Tag
      type={interactive ? 'button' : undefined}
      onClick={interactive ? onClick : undefined}
      disabled={interactive ? disabled : undefined}
      aria-label={label || (value ? `Dice showing ${value}` : 'Dice')}
      data-testid="dice"
      data-value={value ?? ''}
      data-rolling={rolling ? 'true' : 'false'}
      className={`inline-flex rounded-2xl shadow-lg transition ${rolling ? 'dice-rolling' : ''} ${interactive && !disabled ? 'cursor-pointer hover:scale-105 ring-2 ring-brand-400 shadow-glow' : ''} ${disabled ? 'opacity-95' : ''}`}
    >
      <svg key={landed} className={landed ? 'dice-land' : ''} width={size} height={size} viewBox="0 0 4 4" role="img" aria-hidden>
        <defs>
          <linearGradient id={`shine-${gid}`} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="#fff" stopOpacity="0.6" />
            <stop offset="100%" stopColor="#fff" stopOpacity="0" />
          </linearGradient>
          <radialGradient id={`face-${gid}`} cx="35%" cy="30%" r="80%">
            <stop offset="0%" stopColor={s.face} />
            <stop offset="100%" stopColor={s.border} />
          </radialGradient>
          <radialGradient id={`pip-${gid}`} cx="35%" cy="35%" r="70%">
            <stop offset="0%" stopColor={s.pip} stopOpacity="0.75" />
            <stop offset="100%" stopColor={s.pip} />
          </radialGradient>
        </defs>
        <rect x="0.1" y="0.22" width="3.8" height="3.8" rx="0.7" fill="#000" opacity="0.18" />
        <rect x="0.1" y="0.1" width="3.8" height="3.8" rx="0.7" fill={`url(#face-${gid})`} stroke={s.border} strokeWidth="0.08" />
        <rect x="0.25" y="0.25" width="3.5" height="1.6" rx="0.55" fill={`url(#shine-${gid})`} />
        {pips.map(([cx, cy]) => <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r="0.27" fill={`url(#pip-${gid})`} />)}
        {!shown && <text x="2" y="2.6" textAnchor="middle" fontSize="2" fill={s.pip} opacity="0.5">?</text>}
      </svg>
    </Tag>
  );
}
