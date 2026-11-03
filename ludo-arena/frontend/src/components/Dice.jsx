import { DICE_SKINS } from '../lib/cosmetics';

const PIPS = {
  1: [[2, 2]],
  2: [[1, 1], [3, 3]],
  3: [[1, 1], [2, 2], [3, 3]],
  4: [[1, 1], [3, 1], [1, 3], [3, 3]],
  5: [[1, 1], [3, 1], [2, 2], [1, 3], [3, 3]],
  6: [[1, 1], [3, 1], [1, 2], [3, 2], [1, 3], [3, 3]],
};

/** Presentational dice. The value always comes from the server; `rolling` is only a visual state. */
export default function Dice({ value, rolling = false, skin = 'default', size = 72, onClick, disabled, label }) {
  const s = DICE_SKINS[skin] || DICE_SKINS.default;
  const pips = PIPS[value] || [];
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
      className={`inline-flex rounded-2xl shadow-lg transition ${rolling ? 'dice-rolling' : ''} ${interactive && !disabled ? 'cursor-pointer hover:scale-105 ring-2 ring-brand-400 shadow-glow' : ''} ${disabled ? 'opacity-70' : ''}`}
    >
      <svg width={size} height={size} viewBox="0 0 4 4" role="img" aria-hidden>
        <rect x="0.1" y="0.1" width="3.8" height="3.8" rx="0.7" fill={s.face} stroke={s.border} strokeWidth="0.08" />
        {pips.map(([cx, cy]) => <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r="0.24" fill={s.pip} />)}
        {!value && <text x="2" y="2.6" textAnchor="middle" fontSize="2" fill={s.pip} opacity="0.5">?</text>}
      </svg>
    </Tag>
  );
}
