import { Loader2, Mic, MicOff, PhoneOff, RotateCcw, Volume2, VolumeX } from 'lucide-react';
import { COLOR_HEX } from '../lib/format';

const STATE_LABEL = { connecting: 'Connecting…', connected: 'Connected', reconnecting: 'Reconnecting…', failed: 'Connection failed' };

/** Voice chat controls for online matches. `voice` is the object returned by useVoice(). */
export default function VoicePanel({ voice, players = [], className = '' }) {
  const others = players.filter((p) => voice.peerIds.includes(p.userId));
  const on = voice.status === 'on';

  return (
    <section className={`card p-4 ${className}`} aria-label="Voice chat" data-testid="voice-panel">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-bold"><Mic className="h-4 w-4" aria-hidden />Voice</h3>
        {on && (
          <span className={`badge ${voice.localSpeaking ? 'bg-emerald-500/20 text-emerald-300' : 'bg-white/10 text-slate-300'}`} data-testid="voice-me">
            {voice.muted ? 'Mic muted' : voice.localSpeaking ? 'You are speaking' : 'Mic on'}
          </span>
        )}
      </div>

      {!voice.supported && <p className="text-sm text-slate-400">This browser does not support voice chat.</p>}

      {voice.supported && voice.status === 'off' && (
        <>
          <button type="button" className="btn-primary w-full" onClick={voice.join} disabled={!others.length} data-testid="voice-join">
            <Mic className="h-4 w-4" aria-hidden />Join voice
          </button>
          <p className="mt-2 text-xs text-slate-400">Talk with the other players. Audio goes directly between players and is not recorded.</p>
        </>
      )}

      {voice.status === 'joining' && (
        <p className="flex items-center gap-2 text-sm text-slate-300"><Loader2 className="h-4 w-4 animate-spin" aria-hidden />Starting microphone…</p>
      )}

      {voice.status === 'error' && (
        <div className="space-y-2">
          <p role="alert" className="rounded-xl bg-red-500/10 px-3 py-2 text-sm text-red-300">{voice.error}</p>
          <button type="button" className="btn-secondary w-full" onClick={voice.join}>Try again</button>
        </div>
      )}

      {on && (
        <>
          <div className="mb-3 grid grid-cols-2 gap-2">
            <button type="button" className={voice.muted ? 'btn-secondary' : 'btn-primary'} onClick={() => voice.setMuted(!voice.muted)} data-testid="voice-mute"
              aria-label={voice.muted ? 'Unmute microphone' : 'Mute microphone'}>
              {voice.muted ? <MicOff className="h-4 w-4" aria-hidden /> : <Mic className="h-4 w-4" aria-hidden />}{voice.muted ? 'Unmute' : 'Mute'}
            </button>
            <button type="button" className="btn-secondary text-red-300" onClick={voice.leave} data-testid="voice-leave">
              <PhoneOff className="h-4 w-4" aria-hidden />Leave
            </button>
          </div>
          <ul className="space-y-1.5 text-sm">
            {others.map((p) => {
              const peer = voice.peers[p.userId];
              return (
                <li key={p.userId} className="flex items-center gap-2" data-testid={`voice-peer-${p.userId}`}>
                  <span className={`h-2.5 w-2.5 rounded-full ${peer?.speaking ? 'ring-4 ring-emerald-400/40' : ''}`} style={{ background: COLOR_HEX[p.color] }} aria-hidden />
                  <span className="min-w-0 flex-1 truncate">{p.name}</span>
                  <span className={`text-xs ${peer?.state === 'connected' ? 'text-emerald-300' : peer?.state === 'failed' ? 'text-red-300' : 'text-slate-400'}`}>
                    {peer ? (peer.speaking ? 'Speaking' : STATE_LABEL[peer.state] || '') : 'Not in voice'}
                  </span>
                  {peer?.state === 'failed' && (
                    <button type="button" className="btn-ghost p-1" onClick={() => voice.retry(p.userId)} aria-label={`Reconnect to ${p.name}`}><RotateCcw className="h-4 w-4" aria-hidden /></button>
                  )}
                  {peer && (
                    <button type="button" className="btn-ghost p-1" onClick={() => voice.setDeafened(p.userId, !peer.deafened)}
                      aria-label={peer.deafened ? `Unmute ${p.name}` : `Mute ${p.name}`}>
                      {peer.deafened ? <VolumeX className="h-4 w-4" aria-hidden /> : <Volume2 className="h-4 w-4" aria-hidden />}
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
        </>
      )}
    </section>
  );
}
