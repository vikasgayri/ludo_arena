import { useEffect, useRef, useState } from 'react';
import { STEP_MS } from './animation';
import { play } from './sound';

export const DICE_ROLL_MS = 550;

/** True for DICE_ROLL_MS after any player's DICE_ROLLED event arrives (the dice shuffles before showing the value). */
export function useDiceRoll(lastUpdateEvents) {
  const [rolling, setRolling] = useState(false);
  const timer = useRef(null);
  useEffect(() => {
    if (!lastUpdateEvents?.some((e) => e.type === 'DICE_ROLLED')) return undefined;
    setRolling(true);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setRolling(false), DICE_ROLL_MS);
    return undefined;
  }, [lastUpdateEvents]);
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
