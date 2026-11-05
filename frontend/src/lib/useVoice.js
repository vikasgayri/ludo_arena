import { useCallback, useEffect, useRef, useState } from 'react';
import { VoiceApi } from './api';
import { VoiceClient, voiceSupported } from './voice';
import { setVoiceHandler } from '../store/gameStore';

const FALLBACK_ICE = [{ urls: ['stun:stun.l.google.com:19302'] }];

/** React glue for VoiceClient: one client per match page, closed when the match ends or the page unmounts. */
export function useVoice({ match, userId, sendVoice }) {
  const supported = voiceSupported();
  const enabled = !!match && !match.vsComputer && match.status === 'ACTIVE';
  const [snap, setSnap] = useState({ status: 'off', error: null, muted: false, localSpeaking: false, peers: {} });
  const clientRef = useRef(null);
  const peerIds = (match?.players || []).filter((p) => !p.bot && !p.forfeited && p.userId !== userId).map((p) => p.userId);
  const peerIdsRef = useRef(peerIds);
  peerIdsRef.current = peerIds;
  const sendRef = useRef(sendVoice);
  sendRef.current = sendVoice;
  const matchId = match?.matchId;

  useEffect(() => {
    setVoiceHandler((msg) => { if (msg && msg.matchId === matchId) clientRef.current?.handle(msg); });
    return () => setVoiceHandler(null);
  }, [matchId]);

  // hang up when the page closes, the match ends, or this player left it
  useEffect(() => () => { clientRef.current?.leave(); clientRef.current = null; }, []);
  const me = (match?.players || []).find((p) => p.userId === userId);
  useEffect(() => {
    if (!enabled || me?.forfeited) clientRef.current?.leave();
  }, [enabled, me?.forfeited]);

  const peerKey = peerIds.join(',');
  useEffect(() => { clientRef.current?.syncPeers(); }, [peerKey]);

  const join = useCallback(async () => {
    if (!supported || !userId) return;
    if (!clientRef.current) {
      clientRef.current = new VoiceClient({
        myId: userId,
        peerIds: () => peerIdsRef.current,
        send: (to, type, payload) => sendRef.current?.(to, type, payload),
        onChange: setSnap,
      });
    }
    let ice = FALLBACK_ICE;
    try {
      const list = await VoiceApi.iceServers();
      if (Array.isArray(list) && list.length) ice = list.map((s) => ({ urls: s.urls, ...(s.username ? { username: s.username, credential: s.credential } : {}) }));
    } catch { /* STUN fallback */ }
    await clientRef.current.join(ice);
  }, [supported, userId]);

  return {
    supported,
    enabled,
    peerIds,
    ...snap,
    join,
    leave: () => clientRef.current?.leave(),
    setMuted: (m) => clientRef.current?.setMuted(m),
    setDeafened: (id, d) => clientRef.current?.setDeafened(id, d),
    retry: (id) => clientRef.current?.retry(id),
  };
}
