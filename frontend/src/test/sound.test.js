import { beforeEach, describe, expect, it, vi } from 'vitest';

async function freshSound() {
  vi.resetModules();
  return import('../lib/sound');
}

beforeEach(() => { localStorage.clear(); });

describe('sound settings', () => {
  it('has sensible defaults (sound on, music quieter than effects)', async () => {
    const s = (await freshSound()).getSoundSettings();
    expect(s.muted).toBe(false);
    expect(s.master).toBeGreaterThan(0.5);
    expect(s.sfx).toBeGreaterThan(s.music);
  });

  it('persists volumes and mute across reloads and clamps bad values', async () => {
    let snd = await freshSound();
    snd.setSoundSettings({ master: 0.5, sfx: 1.7, music: -1 });
    snd.setMuted(true);
    snd = await freshSound();
    expect(snd.getSoundSettings()).toEqual({ master: 0.5, sfx: 1, music: 0, muted: true });
    expect(snd.isMuted()).toBe(true);
  });

  it('keeps the old mute flag working', async () => {
    localStorage.setItem('la_muted', '1');
    expect((await freshSound()).isMuted()).toBe(true);
  });

  it('effective gain = master x channel, zero when muted', async () => {
    const { effectiveGain } = await freshSound();
    expect(effectiveGain({ master: 0.5, sfx: 0.8, music: 0.2, muted: false }, 'sfx')).toBeCloseTo(0.4);
    expect(effectiveGain({ master: 0.5, sfx: 0.8, music: 0.2, muted: false }, 'music')).toBeCloseTo(0.1);
    expect(effectiveGain({ master: 1, sfx: 1, music: 1, muted: true }, 'sfx')).toBe(0);
  });

  it('notifies listeners and the old mute listeners', async () => {
    const snd = await freshSound();
    const all = vi.fn();
    const mute = vi.fn();
    snd.onSoundSettingsChange(all);
    snd.onMutedChange(mute);
    snd.setSoundSettings({ sfx: 0.3 });
    expect(all).toHaveBeenLastCalledWith(expect.objectContaining({ sfx: 0.3 }));
    expect(mute).not.toHaveBeenCalled();
    snd.setMuted(true);
    expect(mute).toHaveBeenCalledWith(true);
  });

  it('playing sounds never throws when the browser has no audio', async () => {
    const snd = await freshSound();
    snd.SOUND_NAMES.forEach((n) => expect(() => snd.play(n)).not.toThrow());
    expect(() => { snd.startMusic(); snd.stopMusic(); }).not.toThrow();
    expect(snd.isMusicPlaying()).toBe(false);
  });
});
