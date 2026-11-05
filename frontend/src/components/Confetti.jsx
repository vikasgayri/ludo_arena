import { useMemo } from 'react';

const COLORS = ['#e53935', '#2e9e4f', '#f7b500', '#1e78d7', '#ffffff', '#a78bfa'];

/** Short celebratory confetti burst (CSS only; hidden for reduced-motion users). */
export default function Confetti({ pieces = 70 }) {
  const items = useMemo(() => Array.from({ length: pieces }, (_, i) => ({
    left: Math.random() * 100,
    delay: Math.random() * 0.8,
    duration: 2.2 + Math.random() * 1.6,
    color: COLORS[i % COLORS.length],
    rotate: Math.random() * 360,
  })), [pieces]);
  return (
    <div aria-hidden data-testid="confetti">
      {items.map((p, i) => (
        <span
          key={i}
          className="confetti-piece"
          style={{ left: `${p.left}vw`, background: p.color, animationDelay: `${p.delay}s`, animationDuration: `${p.duration}s`, transform: `rotate(${p.rotate}deg)` }}
        />
      ))}
    </div>
  );
}
