import { useEffect, useState } from 'react';
import { LogOut, Music, Play, Volume2, VolumeX } from 'lucide-react';
import { getSoundSettings, onSoundSettingsChange, play, setSoundSettings } from '../lib/sound';
import Modal, { ConfirmModal } from './Modal';

const pct = (v) => Math.round(v * 100);

export function SoundSettings() {
  const [s, setS] = useState(getSoundSettings());
  useEffect(() => onSoundSettingsChange(setS), []);
  const slider = (key, label, Icon) => (
    <label className="block" key={key}>
      <span className="mb-1 flex items-center justify-between text-sm">
        <span className="flex items-center gap-2">{Icon && <Icon className="h-4 w-4 text-slate-400" aria-hidden />}{label}</span>
        <span className="tabular-nums text-slate-400" data-testid={`vol-${key}-value`}>{pct(s[key])}%</span>
      </span>
      <input type="range" min="0" max="100" step="5" value={pct(s[key])} disabled={s.muted}
        className="w-full accent-brand-500 disabled:opacity-40" aria-label={`${label} volume`} data-testid={`vol-${key}`}
        onChange={(e) => setSoundSettings({ [key]: Number(e.target.value) / 100 })}
        onPointerUp={() => key !== 'music' && play('step')} />
    </label>
  );
  return (
    <div className="space-y-4" data-testid="sound-settings">
      <button type="button" className={`flex w-full items-center justify-between rounded-xl border p-3 text-left ${s.muted ? 'border-red-400/40 bg-red-500/10' : 'border-white/10'}`}
        onClick={() => setSoundSettings({ muted: !s.muted })} aria-pressed={s.muted} data-testid="mute-all">
        <span className="flex items-center gap-2 text-sm font-semibold">{s.muted ? <VolumeX className="h-4 w-4" aria-hidden /> : <Volume2 className="h-4 w-4" aria-hidden />}{s.muted ? 'Sound is off' : 'Sound is on'}</span>
        <span className="text-xs text-slate-400">{s.muted ? 'Tap to unmute' : 'Tap to mute'}</span>
      </button>
      {slider('master', 'Master', Volume2)}
      {slider('sfx', 'Sound effects', null)}
      {slider('music', 'Background music', Music)}
    </div>
  );
}

/**
 * In-game menu: Resume, Sound settings, Leave game (always behind a confirmation).
 * `onLeave` does the real leaving (online: the authenticated leave/forfeit call; offline: ending the local game).
 */
export default function GameMenu({ open, onClose, onLeave, leaveMessage, online, leaving = false }) {
  const [view, setView] = useState('main');
  const [confirm, setConfirm] = useState(false);
  useEffect(() => { if (open) setView('main'); }, [open]);
  return (
    <>
      <Modal open={open && !confirm} onClose={onClose} title={view === 'sound' ? 'Sound settings' : 'Game menu'} size="sm">
        {view === 'main' ? (
          <div className="space-y-2" data-testid="game-menu">
            {online && <p className="text-xs text-slate-400">Online games keep running while this menu is open — your turn timer does not stop.</p>}
            <button type="button" className="btn-primary w-full" onClick={() => { play('click'); onClose(); }} data-testid="menu-resume"><Play className="h-4 w-4" aria-hidden />Resume game</button>
            <button type="button" className="btn-secondary w-full" onClick={() => { play('click'); setView('sound'); }} data-testid="menu-sound"><Volume2 className="h-4 w-4" aria-hidden />Sound settings</button>
            <button type="button" className="btn-secondary w-full text-red-300" onClick={() => { play('click'); setConfirm(true); }} data-testid="menu-leave"><LogOut className="h-4 w-4" aria-hidden />Leave game</button>
          </div>
        ) : (
          <div className="space-y-4">
            <SoundSettings />
            <button type="button" className="btn-secondary w-full" onClick={() => setView('main')}>Back</button>
          </div>
        )}
      </Modal>
      <ConfirmModal open={open && confirm} title="Leave this game?" danger busy={leaving} confirmLabel="Leave game"
        message={leaveMessage} onConfirm={async () => { await onLeave(); setConfirm(false); }} onClose={() => setConfirm(false)} />
    </>
  );
}
