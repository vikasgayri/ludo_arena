// Synthesized game audio (WebAudio, no audio files to download). Purely cosmetic.
//
// Signal chain:  effect voices -> sfx bus ─┐
//                music voices  -> music bus ┴-> master gain -> compressor/limiter -> speakers
// Effect peaks were raised from 0.03-0.09 (about -30..-21 dBFS, too quiet on phones) to roughly -10..-6 dBFS; the
// compressor keeps overlapping sounds (dice + steps + capture) from summing into clipping.
const SETTINGS_KEY = 'la_sound';
const LEGACY_MUTED_KEY = 'la_muted';

export const DEFAULT_SOUND_SETTINGS = Object.freeze({ master: 0.8, sfx: 0.9, music: 0.3, muted: false });

const clamp01 = (v, fallback) => {
  const n = Number(v);
  return Number.isFinite(n) ? Math.min(1, Math.max(0, n)) : fallback;
};

function loadSettings() {
  let stored = null;
  try { stored = JSON.parse(localStorage.getItem(SETTINGS_KEY) || 'null'); } catch { stored = null; }
  let legacyMuted = false;
  try { legacyMuted = localStorage.getItem(LEGACY_MUTED_KEY) === '1'; } catch { /* storage unavailable */ }
  const s = stored && typeof stored === 'object' ? stored : {};
  return {
    master: clamp01(s.master, DEFAULT_SOUND_SETTINGS.master),
    sfx: clamp01(s.sfx, DEFAULT_SOUND_SETTINGS.sfx),
    music: clamp01(s.music, DEFAULT_SOUND_SETTINGS.music),
    muted: typeof s.muted === 'boolean' ? s.muted : legacyMuted,
  };
}

let settings = loadSettings();
const listeners = new Set();
const mutedListeners = new Set();

/** Gain actually applied to a channel ('sfx' | 'music'): master x channel, 0 when muted. */
export function effectiveGain(s, channel) {
  if (s.muted) return 0;
  return clamp01(s.master, 0) * clamp01(s[channel], 0);
}

export function getSoundSettings() { return { ...settings }; }

export function setSoundSettings(partial) {
  const prevMuted = settings.muted;
  settings = {
    master: clamp01(partial.master ?? settings.master, settings.master),
    sfx: clamp01(partial.sfx ?? settings.sfx, settings.sfx),
    music: clamp01(partial.music ?? settings.music, settings.music),
    muted: typeof partial.muted === 'boolean' ? partial.muted : settings.muted,
  };
  try {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    localStorage.setItem(LEGACY_MUTED_KEY, settings.muted ? '1' : '0');
  } catch { /* storage unavailable */ }
  applyGains();
  const snapshot = getSoundSettings();
  listeners.forEach((fn) => fn(snapshot));
  if (prevMuted !== settings.muted) mutedListeners.forEach((fn) => fn(settings.muted));
  if (settings.muted || settings.music === 0) stopMusicVoices(); else if (musicWanted) startMusicLoop();
}

export function onSoundSettingsChange(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

// ---- old API (kept: used by the in-game mute buttons)
export function isMuted() { return settings.muted; }
export function setMuted(value) { setSoundSettings({ muted: !!value }); }
export function onMutedChange(fn) {
  mutedListeners.add(fn);
  return () => mutedListeners.delete(fn);
}

// ---------------------------------------------------------------- audio graph
let ctx = null;
let bus = null; // { master, sfx, music }
let injected = false;

/** Tests: route all audio through the given (e.g. Offline) AudioContext. */
export function __setAudioContextForTests(audioContext) {
  ctx = audioContext;
  bus = null;
  injected = !!audioContext;
}

function audio() {
  if (!ctx) {
    if (typeof window === 'undefined') return null;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    try { ctx = new AC(); } catch { return null; }
  }
  if (!injected && ctx.state === 'suspended') ctx.resume().catch(() => {});
  if (!bus) {
    try {
      const master = ctx.createGain();
      const limiter = ctx.createDynamicsCompressor();
      limiter.threshold.value = -10;
      limiter.knee.value = 6;
      limiter.ratio.value = 8;
      limiter.attack.value = 0.003;
      limiter.release.value = 0.15;
      const sfx = ctx.createGain();
      const music = ctx.createGain();
      sfx.connect(master);
      music.connect(master);
      master.connect(limiter).connect(ctx.destination);
      bus = { master, sfx, music };
      applyGains();
    } catch {
      return null;
    }
  }
  return ctx;
}

function applyGains() {
  if (!bus) return;
  bus.master.gain.value = settings.muted ? 0 : settings.master;
  bus.sfx.gain.value = settings.sfx;
  bus.music.gain.value = settings.music;
}

function tone(freq, { duration = 0.08, type = 'sine', volume = 0.3, delay = 0, slideTo, out = 'sfx' } = {}) {
  const a = audio();
  if (!a || !bus) return;
  const t0 = a.currentTime + delay;
  const osc = a.createOscillator();
  const gain = a.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t0);
  if (slideTo) osc.frequency.exponentialRampToValueAtTime(slideTo, t0 + duration);
  gain.gain.setValueAtTime(0.0001, t0);
  gain.gain.exponentialRampToValueAtTime(volume, t0 + 0.012);
  gain.gain.exponentialRampToValueAtTime(0.0001, t0 + duration);
  osc.connect(gain).connect(bus[out]);
  osc.start(t0);
  osc.stop(t0 + duration + 0.03);
  return osc;
}

/** Short filtered-noise burst: makes the dice sound like a real rattle instead of a beep. */
function rattle(delay, volume) {
  const a = audio();
  if (!a || !bus) return;
  const len = Math.floor(a.sampleRate * 0.035);
  const buf = a.createBuffer(1, len, a.sampleRate);
  const data = buf.getChannelData(0);
  for (let i = 0; i < len; i += 1) data[i] = (Math.random() * 2 - 1) * (1 - i / len);
  const src = a.createBufferSource();
  src.buffer = buf;
  const band = a.createBiquadFilter();
  band.type = 'bandpass';
  band.frequency.value = 1800 + Math.random() * 900;
  band.Q.value = 1.2;
  const gain = a.createGain();
  gain.gain.value = volume;
  src.connect(band).connect(gain).connect(bus.sfx);
  src.start(a.currentTime + delay);
}

// Peak levels are balanced by ear: frequent sounds (step) quieter, rare events (capture, win) louder.
const SOUNDS = {
  dice: () => {
    for (let i = 0; i < 6; i += 1) {
      rattle(i * 0.055, 0.55);
      tone(220 + Math.random() * 180, { duration: 0.035, type: 'triangle', volume: 0.12, delay: i * 0.055 });
    }
  },
  step: () => tone(600, { duration: 0.07, type: 'triangle', volume: 0.45 }),
  capture: () => {
    tone(520, { duration: 0.3, type: 'sawtooth', volume: 0.26, slideTo: 110 });
    tone(130, { duration: 0.22, type: 'sine', volume: 0.35, delay: 0.05 });
  },
  home: () => { tone(660, { duration: 0.14, volume: 0.32 }); tone(990, { duration: 0.2, delay: 0.11, volume: 0.32 }); },
  turn: () => { tone(740, { duration: 0.1, volume: 0.3 }); tone(988, { duration: 0.14, volume: 0.28, delay: 0.09 }); },
  turnOther: () => tone(620, { duration: 0.1, volume: 0.3 }),
  noMove: () => tone(300, { duration: 0.2, type: 'triangle', volume: 0.5, slideTo: 220 }),
  click: () => tone(1000, { duration: 0.045, type: 'triangle', volume: 0.35 }),
  chat: () => { tone(880, { duration: 0.06, volume: 0.22 }); tone(1175, { duration: 0.08, volume: 0.22, delay: 0.07 }); },
  win: () => [523, 659, 784, 1047].forEach((f, i) => tone(f, { duration: 0.26, delay: i * 0.13, volume: 0.34 })),
  lose: () => [392, 330, 262].forEach((f, i) => tone(f, { duration: 0.28, delay: i * 0.15, volume: 0.26 })),
};
export const SOUND_NAMES = Object.keys(SOUNDS);

// the same effect fired again within this window (e.g. two updates in a burst) is dropped, not stacked
const MIN_GAP_MS = { step: 45, dice: 250, turn: 400, turnOther: 400, click: 40 };
const lastPlayed = {};

export function play(name) {
  if (settings.muted || effectiveGain(settings, 'sfx') === 0 || !SOUNDS[name]) return;
  const now = Date.now();
  const gap = MIN_GAP_MS[name] || 0;
  if (gap && lastPlayed[name] && now - lastPlayed[name] < gap) return;
  lastPlayed[name] = now;
  try { SOUNDS[name](); } catch { /* audio is best-effort */ }
}

// ------------------------------------------------------------- background music
// A soft pentatonic loop, scheduled a little ahead of time. Starts only on game screens and only after the
// browser allowed audio (first tap), so it never blasts on page load.
const MUSIC_NOTES = [261.63, 329.63, 392.0, 440.0, 392.0, 329.63, 293.66, 392.0];
const BASS = [130.81, 110.0, 98.0, 110.0];
const NOTE_S = 0.42;
let musicWanted = false;
let musicTimer = null;
let nextNoteAt = 0;
let noteIndex = 0;

function scheduleMusic() {
  const a = audio();
  if (!a || !bus) return;
  if (nextNoteAt < a.currentTime) nextNoteAt = a.currentTime + 0.05;
  while (nextNoteAt < a.currentTime + 1.2) {
    const delay = nextNoteAt - a.currentTime;
    tone(MUSIC_NOTES[noteIndex % MUSIC_NOTES.length], { duration: NOTE_S * 1.6, type: 'sine', volume: 0.14, delay, out: 'music' });
    if (noteIndex % 4 === 0) tone(BASS[(noteIndex / 4) % BASS.length], { duration: NOTE_S * 3.6, type: 'triangle', volume: 0.12, delay, out: 'music' });
    nextNoteAt += NOTE_S;
    noteIndex += 1;
  }
}

function startMusicLoop() {
  if (musicTimer || settings.muted || settings.music === 0) return;
  if (!audio()) return;
  nextNoteAt = 0;
  scheduleMusic();
  musicTimer = setInterval(scheduleMusic, 400);
}

function stopMusicVoices() {
  clearInterval(musicTimer);
  musicTimer = null;
}

let unlockArmed = false;
function onFirstGesture() {
  unlockArmed = false;
  window.removeEventListener('pointerdown', onFirstGesture);
  window.removeEventListener('keydown', onFirstGesture);
  if (musicWanted) startMusicLoop();
}

export function startMusic() {
  musicWanted = true;
  // browsers only allow audio after a user gesture: wait for the first tap instead of starting a muted context
  if (!injected && (!ctx || ctx.state !== 'running') && typeof window !== 'undefined') {
    if (!unlockArmed) {
      unlockArmed = true;
      window.addEventListener('pointerdown', onFirstGesture);
      window.addEventListener('keydown', onFirstGesture);
    }
    return;
  }
  startMusicLoop();
}

export function stopMusic() {
  musicWanted = false;
  stopMusicVoices();
  if (unlockArmed && typeof window !== 'undefined') {
    unlockArmed = false;
    window.removeEventListener('pointerdown', onFirstGesture);
    window.removeEventListener('keydown', onFirstGesture);
  }
}

export function isMusicPlaying() { return !!musicTimer; }
