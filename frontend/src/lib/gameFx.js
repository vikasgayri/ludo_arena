import { useEffect, useRef, useState } from 'react';
import { STEP_MS } from './animation';
import { play } from './sound';

/** Minimum time the dice shuffles before it shows the server's value. */
export const DICE_ROLL_MS = 360;

/** How long the dice still has to shuffle when the result arrives, if this player started rolling at `startedAt`. */
export function remainingRollMs(startedAt, now = Date.now()) {
  if (!startedAt || now - startedAt > 5000) return DICE_ROLL_MS; // someone else's roll: show a full (short) shuffle
  return Math.max(0, DICE_ROLL_MS - (now - startedAt));
}

/**
 * True while the dice shuffles. A roll this player started (tap time in `localRollRef.current`) shuffles from the tap
 * itself, so the network round trip is hidden inside the animation instead of being added on top of it.
 */
export function useDiceRoll(lastUpdateEvents, localRollRef) {
  const [rolling, setRolling] = useState(false);
  const timer = useRef(null);
  useEffect(() => {
    if (!lastUpdateEvents?.some((e) => e.type === 'DICE_ROLLED')) return undefined;
    const startedAt = localRollRef?.current || null;
    if (localRollRef) localRollRef.current = null;
    const left = remainingRollMs(startedAt);
    clearTimeout(timer.current);
    if (left <= 0) {
      setRolling(false);
      return undefined;
    }
    setRolling(true);
    timer.current = setTimeout(() => setRolling(false), left);
    return undefined;
  }, [lastUpdateEvents, localRollRef]);
  useEffect(() => () => clearTimeout(timer.current), []);
  return rolling;
}

/**
 * Plays sounds for the events of the latest update, timed to match the board animation
 * (steps while the token walks, capture / home after it arrives).
 */
export function useGameSounds(lastUpdateEvents, myUserId) {
  const timers = useRef([]);
  useEffect(() => {
    const events = lastUpdateEvents || [];
    if (!events.length) return;
    const at = (ms, name) => timers.current.push(setTimeout(() => play(name), ms));
    let walkMs = 0;
    events.forEach((e) => {
      const d = e.data || {};
      switch (e.type) {
        case 'DICE_ROLLED': play('dice'); break;
        case 'TOKEN_MOVED': {
          const from = Number(d.from);
          const to = Number(d.to);
          const cells = from < 0 ? 1 : Math.max(1, Math.min(7, to - from));
          for (let i = 0; i < cells; i += 1) at(i * STEP_MS, 'step');
          walkMs = cells * STEP_MS;
          break;
        }
        case 'TOKEN_CAPTURED': at(walkMs, 'capture'); break;
        case 'TOKEN_HOME': at(walkMs, 'home'); break;
        case 'TURN_CHANGED': if (d.nextUserId === myUserId) at(walkMs + 120, 'turn'); break;
        case 'MATCH_FINISHED': at(walkMs + 200, d.winnerId === myUserId ? 'win' : 'lose'); break;
        default: break;
      }
    });
  }, [lastUpdateEvents, myUserId]);
  useEffect(() => () => timers.current.forEach(clearTimeout), []);
}
