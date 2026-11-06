import { useEffect, useRef, useState } from 'react';

/**
 * Presentation-only animation of token movement. The server state is always the truth: this only decides HOW the
 * board walks from what is currently drawn to the latest server positions. Nothing here affects game rules.
 */
export const STEP_MS = 210; // time per cell: a token hops one box at a time at a calm, readable pace
const MAX_WALK_STEPS = 7; // a single move is at most 6 cells (+1 when leaving base); larger jumps (reconnect) snap

const key = (color, index) => `${color}-${index}`;

export function tokenMap(players = []) {
  const m = {};
  players.forEach((p) => (p.tokens || []).forEach((s, i) => { m[key(p.color, i)] = s; }));
  return m;
}

/**
 * Plans the frames from `shown` to `target` (both maps key -> steps).
 * Returns a list of frames; each frame is a partial map applied after STEP_MS.
 *  - exactly one token moving forward by a normal dice distance walks cell by cell;
 *  - tokens sent back to base (captured) jump home only after the mover arrived;
 *  - anything else (several movers, big jumps after a reconnect) is applied in one frame.
 */
export function planFrames(shown, target) {
  const forward = [];
  const back = [];
  const other = {};
  Object.keys(target).forEach((k) => {
    const from = shown[k];
    const to = target[k];
    if (from === undefined || from === to) return;
    if (to > from) forward.push({ k, from, to });
    else if (to === -1) back.push(k);
    else other[k] = to;
  });
  const single = forward.length === 1 && Object.keys(other).length === 0 && forward[0].to - forward[0].from <= MAX_WALK_STEPS;
  if (!single) {
    const all = { ...other };
    forward.forEach((f) => { all[f.k] = f.to; });
    back.forEach((k) => { all[k] = -1; });
    return Object.keys(all).length ? [all] : [];
  }
  const { k, from, to } = forward[0];
  const frames = [];
  // leaving base lands on step 0 in a single hop
  for (let s = from < 0 ? 0 : from + 1; s <= to; s += 1) frames.push({ [k]: s });
  if (back.length) {
    const home = {};
    back.forEach((b) => { home[b] = -1; });
    frames.push(home);
  }
  return frames;
}

/**
 * Returns players whose token positions walk smoothly towards the server positions, plus `animating`.
 * `enabled=false` (reduced motion / user preference) applies server positions immediately.
 */
export function useAnimatedPlayers(players, { enabled = true, stepMs = STEP_MS } = {}) {
  const [shown, setShown] = useState(() => tokenMap(players));
  const [animating, setAnimating] = useState(false);
  const timer = useRef(null);
  const shownRef = useRef(shown);
  shownRef.current = shown;

  const targetSig = JSON.stringify(tokenMap(players));
  useEffect(() => {
    const target = JSON.parse(targetSig);
    clearTimeout(timer.current);
    // new keys (first render, new match) and disabled animation: snap
    const known = Object.keys(target).every((k) => k in shownRef.current);
    if (!enabled || !known) {
      setShown(target);
      setAnimating(false);
      return undefined;
    }
    const frames = planFrames(shownRef.current, target);
    if (!frames.length) {
      setAnimating(false);
      return undefined;
    }
    setAnimating(true);
    let i = 0;
    const tick = () => {
      const frame = frames[i]; // read now: the state updater runs later, after i has advanced
      setShown((cur) => ({ ...cur, ...frame }));
      i += 1;
      if (i < frames.length) timer.current = setTimeout(tick, stepMs);
      else timer.current = setTimeout(() => setAnimating(false), stepMs);
    };
    tick();
    return () => clearTimeout(timer.current);
  }, [targetSig, enabled, stepMs]);

  const animated = (players || []).map((p) => ({
    ...p,
    tokens: (p.tokens || []).map((s, i) => {
      const v = shown[key(p.color, i)];
      return v === undefined ? s : v;
    }),
  }));
  return { players: animated, animating };
}

/** True when the OS asks for less motion. */
export function prefersReducedMotion() {
  try {
    return typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
}
