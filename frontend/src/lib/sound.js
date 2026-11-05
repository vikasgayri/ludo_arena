// Tiny synthesized sound effects (WebAudio, no audio files to download). Purely cosmetic.
const STORAGE_KEY = 'la_muted';

let ctx = null;
let muted = (() => {
  try { return localStorage.getItem(STORAGE_KEY) === '1'; } catch { return false; }
})();
const listeners = new Set();

export function isMuted() { return muted; }

export function setMuted(value) {
  muted = !!value;
  try { localStorage.setItem(STORAGE_KEY, muted ? '1' : '0'); } catch { /* storage unavailable */ }
  listeners.forEach((fn) => fn(muted));
}

export function onMutedChange(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

function audio() {
  if (typeof window === 'undefined') return null;
  const AC = window.AudioContext || window.webkitAudioContext;
  if (!AC) return null;
  if (!ctx) {
    try { ctx = new AC(); } catch { return null; }
  }
  if (ctx.state === 'suspended') ctx.resume().catch(() => {});
  return ctx;
}

function tone(freq, { duration = 0.08, type = 'sine', volume = 0.08, delay = 0, slideTo } = {}) {
  const a = audio();
  if (!a) return;
  const t0 = a.currentTime + delay;
  const osc = a.createOscillator();
  const gain = a.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t0);
  if (slideTo) osc.frequency.exponentialRampToValueAtTime(slideTo, t0 + duration);
  gain.gain.setValueAtTime(0.0001, t0);
  gain.gain.exponentialRampToValueAtTime(volume, t0 + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.0001, t0 + duration);
  osc.connect(gain).connect(a.destination);
  osc.start(t0);
  osc.stop(t0 + duration + 0.02);
}

const SOUNDS = {
  dice: () => { for (let i = 0; i < 5; i += 1) tone(180 + Math.random() * 160, { duration: 0.04, type: 'square', volume: 0.03, delay: i * 0.07 }); },
  step: () => tone(520, { duration: 0.05, type: 'triangle', volume: 0.05 }),
  capture: () => tone(440, { duration: 0.28, type: 'sawtooth', volume: 0.06, slideTo: 110 }),
  home: () => { tone(660, { duration: 0.12 }); tone(880, { duration: 0.16, delay: 0.1 }); },
  turn: () => tone(760, { duration: 0.09, type: 'sine', volume: 0.06 }),
  win: () => [523, 659, 784, 1047].forEach((f, i) => tone(f, { duration: 0.22, delay: i * 0.13, volume: 0.09 })),
  lose: () => [392, 330, 262].forEach((f, i) => tone(f, { duration: 0.25, delay: i * 0.15, volume: 0.07 })),
};

export function play(name) {
  if (muted) return;
  try { SOUNDS[name]?.(); } catch { /* audio is best-effort */ }
}
