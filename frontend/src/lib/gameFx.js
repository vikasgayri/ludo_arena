import { useEffect, useRef, useState } from 'react';
import { STEP_MS } from './animation';
import { play } from './sound';

/** Minimum time the dice shuffles before it shows the server's value. */
export const DICE_ROLL_MS = 700; // medium: long enough to see the dice tumble, short enough not to wait

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
        // the turn chime is played by useTurnBanner (one place, so it is never doubled)
        case 'MATCH_FINISHED': at(walkMs + 200, d.winnerId === myUserId ? 'win' : 'lose'); break;
        default: break;
      }
    });
  }, [lastUpdateEvents, myUserId]);
  useEffect(() => () => timers.current.forEach(clearTimeout), []);
}

export const TURN_BANNER_MS = 1500;
export const NOTICE_MS = 1900;

/**
 * Shows a short "YOUR TURN!" / "<name>'s Turn" banner each time the turn passes to a different player, and plays the
 * turn chime once. The player to move always comes from the game state handed in (server state online, local engine
 * state offline); nothing here decides whose turn it is. An extra turn (same player again) shows no new banner.
 */
export function useTurnBanner(currentUserId, active, isMine) {
  const [banner, setBanner] = useState(null);
  const last = useRef(null);
  const count = useRef(0);
  const timer = useRef(null);
  useEffect(() => {
    if (!active) { last.current = null; return; }
    if (!currentUserId || last.current === currentUserId) return;
    last.current = currentUserId;
    count.current += 1;
    setBanner({ key: count.current, userId: currentUserId });
    play(isMine ? 'turn' : 'turnOther');
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setBanner(null), TURN_BANNER_MS);
  }, [currentUserId, active, isMine]);
  useEffect(() => () => clearTimeout(timer.current), []);
  return banner;
}

/**
 * A short message for things that happen without a token moving: no legal move, three sixes, a player leaving.
 * Shown after the dice has landed so the rolled value is visible first.
 */
export function useGameNotice(lastUpdateEvents, nameOf) {
  const [notice, setNotice] = useState(null);
  const timers = useRef([]);
  const count = useRef(0);
  useEffect(() => {
    const events = lastUpdateEvents || [];
    let text = null;
    let sound = null;
    let wait = 0;
    events.forEach((e) => {
      const who = nameOf(e.userId);
      const you = who === 'You';
      if (e.type === 'NO_MOVES') {
        text = `${you ? 'No legal move' : `${who} has no legal move`} with a ${e.data?.value} — turn passes`;
        sound = 'noMove';
        wait = DICE_ROLL_MS;
      } else if (e.type === 'TRIPLE_SIX') {
        text = `${you ? 'You' : who} rolled three sixes — turn lost`;
        sound = 'noMove';
        wait = DICE_ROLL_MS;
      } else if (e.type === 'PLAYER_FORFEITED') {
        text = `${you ? 'You' : who} left the game`;
      }
    });
    if (!text) return;
    count.current += 1;
    const key = count.current;
    timers.current.push(setTimeout(() => {
      setNotice({ key, text });
      if (sound) play(sound);
    }, wait));
    timers.current.push(setTimeout(() => setNotice((n) => (n && n.key === key ? null : n)), wait + NOTICE_MS));
  }, [lastUpdateEvents]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => () => timers.current.forEach(clearTimeout), []);
  return notice;
}
