/**
 * Peer-to-peer voice chat (WebRTC mesh: one RTCPeerConnection per other human player).
 * Audio never goes through the game server; the server only relays signalling messages
 * (join / leave / offer / answer / ice) to one addressed player of the same match.
 *
 * Who makes the offer is deterministic so two players joining at the same time do not collide:
 * on "join" from X, the player with the smaller user id creates the offer; the other one answers
 * (and, if it has no connection to X yet, replies "join" so the smaller id starts).
 */
const AUDIO_CONSTRAINTS = { audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }, video: false };
const SPEAKING_LEVEL = 0.04;

function defaultDeps() {
  return {
    getUserMedia: (c) => navigator.mediaDevices.getUserMedia(c),
    createPeer: (config) => new RTCPeerConnection(config),
    createAudio: () => {
      const a = new Audio();
      a.autoplay = true;
      a.setAttribute('playsinline', '');
      return a;
    },
    createAudioContext: () => {
      const AC = window.AudioContext || window.webkitAudioContext;
      return AC ? new AC() : null;
    },
    setInterval: (fn, ms) => setInterval(fn, ms),
    clearInterval: (id) => clearInterval(id),
  };
}

export function voiceSupported() {
  return typeof window !== 'undefined' && !!window.RTCPeerConnection && !!navigator.mediaDevices?.getUserMedia;
}

export class VoiceClient {
  /**
   * @param {object} o
   * @param {string} o.myId
   * @param {() => string[]} o.peerIds other human players currently in the match
   * @param {(to: string, type: string, payload?: object) => boolean} o.send
   * @param {(snapshot: object) => void} o.onChange
   */
  constructor({ myId, peerIds, send, onChange, deps }) {
    this.myId = myId;
    this.peerIds = peerIds;
    this.send = send;
    this.onChange = onChange || (() => {});
    this.deps = { ...defaultDeps(), ...(deps || {}) };
    this.status = 'off'; // off | joining | on | error
    this.error = null;
    this.muted = false;
    this.localStream = null;
    this.iceServers = [];
    this.peers = new Map(); // userId -> { pc, state, audio, pendingIce, speaking, deafened, meter }
    this.meterTimer = null;
    this.audioCtx = null;
    this.localMeter = null;
    this.localSpeaking = false;
  }

  snapshot() {
    const peers = {};
    this.peers.forEach((p, id) => { peers[id] = { state: p.state, speaking: !!p.speaking, deafened: !!p.deafened }; });
    return { status: this.status, error: this.error, muted: this.muted, localSpeaking: this.localSpeaking, peers };
  }

  emit() { this.onChange(this.snapshot()); }

  async join(iceServers = []) {
    if (this.status === 'on' || this.status === 'joining') return;
    this.iceServers = iceServers;
    this.status = 'joining';
    this.error = null;
    this.emit();
    try {
      this.localStream = await this.deps.getUserMedia(AUDIO_CONSTRAINTS);
    } catch (e) {
      this.status = 'error';
      this.error = e && e.name === 'NotAllowedError'
        ? 'Microphone permission was denied. Allow the microphone for this site and try again.'
        : 'Could not start the microphone.';
      this.emit();
      return;
    }
    this.localStream.getAudioTracks().forEach((t) => { t.enabled = !this.muted; });
    this.status = 'on';
    this.startMeters();
    this.emit();
    this.peerIds().forEach((id) => this.send(id, 'join', {}));
  }

  leave() {
    if (this.status === 'off') return;
    this.peers.forEach((_, id) => this.send(id, 'leave', {}));
    [...this.peers.keys()].forEach((id) => this.closePeer(id, false));
    this.localStream?.getTracks().forEach((t) => t.stop());
    this.localStream = null;
    this.stopMeters();
    this.status = 'off';
    this.error = null;
    this.emit();
  }

  setMuted(muted) {
    this.muted = !!muted;
    this.localStream?.getAudioTracks().forEach((t) => { t.enabled = !this.muted; });
    this.emit();
  }

  /** Locally silence one player (does not affect what others hear). */
  setDeafened(userId, deafened) {
    const p = this.peers.get(userId);
    if (!p) return;
    p.deafened = !!deafened;
    if (p.audio) p.audio.muted = p.deafened;
    this.emit();
  }

  /** Drop connections to players who left the match. */
  syncPeers() {
    const allowed = new Set(this.peerIds());
    [...this.peers.keys()].forEach((id) => { if (!allowed.has(id)) this.closePeer(id); });
  }

  async handle({ from, type, payload }) {
    if (!from || from === this.myId || !this.peerIds().includes(from)) return; // only players of this match
    if (type === 'leave') { this.closePeer(from); return; }
    if (this.status !== 'on') return; // not in voice: ignore (they get an offer once we join)
    try {
      if (type === 'join') {
        if (this.myId < from) {
          await this.call(from);
        } else if (!this.peers.has(from)) {
          this.send(from, 'join', {});
        }
      } else if (type === 'offer') {
        this.closePeer(from);
        const p = this.createPeer(from);
        await p.pc.setRemoteDescription(payload);
        const answer = await p.pc.createAnswer();
        await p.pc.setLocalDescription(answer);
        this.send(from, 'answer', { type: answer.type, sdp: answer.sdp });
        await this.flushIce(p);
      } else if (type === 'answer') {
        const p = this.peers.get(from);
        if (!p) return;
        await p.pc.setRemoteDescription(payload);
        await this.flushIce(p);
      } else if (type === 'ice') {
        const p = this.peers.get(from);
        if (!p || !payload?.candidate) return;
        if (p.pc.remoteDescription) await p.pc.addIceCandidate(payload);
        else p.pendingIce.push(payload);
      }
    } catch {
      const p = this.peers.get(from);
      if (p) { p.state = 'failed'; this.emit(); }
    }
  }

  async call(id) {
    this.closePeer(id);
    const p = this.createPeer(id);
    const offer = await p.pc.createOffer();
    await p.pc.setLocalDescription(offer);
    this.send(id, 'offer', { type: offer.type, sdp: offer.sdp });
  }

  /** Re-dial one player (e.g. after a failed connection). */
  retry(id) {
    if (this.status !== 'on') return;
    this.closePeer(id);
    if (this.myId < id) this.call(id).catch(() => {});
    else this.send(id, 'join', {});
  }

  createPeer(id) {
    const pc = this.deps.createPeer({ iceServers: this.iceServers });
    const peer = { pc, state: 'connecting', audio: null, pendingIce: [], speaking: false, deafened: false, meter: null };
    this.peers.set(id, peer);
    this.localStream?.getTracks().forEach((t) => pc.addTrack(t, this.localStream));
    pc.onicecandidate = (e) => {
      if (e.candidate) {
        const c = typeof e.candidate.toJSON === 'function' ? e.candidate.toJSON() : e.candidate;
        this.send(id, 'ice', c);
      }
    };
    pc.ontrack = (e) => {
      const stream = (e.streams && e.streams[0]) || null;
      if (!stream) return;
      if (!peer.audio) peer.audio = this.deps.createAudio();
      peer.audio.srcObject = stream;
      peer.audio.muted = peer.deafened;
      const played = peer.audio.play?.();
      if (played && typeof played.catch === 'function') played.catch(() => {});
      peer.meter = this.createMeter(stream);
    };
    pc.onconnectionstatechange = () => {
      const s = pc.connectionState;
      peer.state = s === 'connected' ? 'connected' : (s === 'failed' || s === 'closed') ? 'failed' : peer.state === 'connected' && s === 'disconnected' ? 'reconnecting' : peer.state;
      this.emit();
    };
    this.emit();
    return peer;
  }

  async flushIce(p) {
    const queued = p.pendingIce.splice(0);
    for (const c of queued) {
      try { await p.pc.addIceCandidate(c); } catch { /* stale candidate */ }
    }
  }

  closePeer(id, emit = true) {
    const p = this.peers.get(id);
    if (!p) return;
    try { p.pc.close(); } catch { /* already closed */ }
    if (p.audio) { try { p.audio.pause?.(); } catch { /* ignore */ } p.audio.srcObject = null; }
    this.peers.delete(id);
    if (emit) this.emit();
  }

  // ---- "who is speaking" (best effort; skipped where Web Audio is unavailable) ----
  createMeter(stream) {
    try {
      if (!this.audioCtx) this.audioCtx = this.deps.createAudioContext();
      if (!this.audioCtx) return null;
      const analyser = this.audioCtx.createAnalyser();
      analyser.fftSize = 512;
      this.audioCtx.createMediaStreamSource(stream).connect(analyser);
      const data = new Uint8Array(analyser.fftSize);
      return () => {
        analyser.getByteTimeDomainData(data);
        let sum = 0;
        for (let i = 0; i < data.length; i += 1) { const v = (data[i] - 128) / 128; sum += v * v; }
        return Math.sqrt(sum / data.length);
      };
    } catch {
      return null;
    }
  }

  startMeters() {
    this.localMeter = this.localStream ? this.createMeter(this.localStream) : null;
    this.meterTimer = this.deps.setInterval(() => {
      let changed = false;
      const local = !this.muted && !!this.localMeter && this.localMeter() > SPEAKING_LEVEL;
      if (local !== this.localSpeaking) { this.localSpeaking = local; changed = true; }
      this.peers.forEach((p) => {
        const speaking = !!p.meter && !p.deafened && p.meter() > SPEAKING_LEVEL;
        if (speaking !== p.speaking) { p.speaking = speaking; changed = true; }
      });
      if (changed) this.emit();
    }, 200);
  }

  stopMeters() {
    if (this.meterTimer) this.deps.clearInterval(this.meterTimer);
    this.meterTimer = null;
    this.localMeter = null;
    this.localSpeaking = false;
    try { this.audioCtx?.close?.(); } catch { /* ignore */ }
    this.audioCtx = null;
  }
}
