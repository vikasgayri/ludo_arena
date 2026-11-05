import { beforeEach, describe, expect, it, vi } from 'vitest';
import { VoiceClient } from '../lib/voice';

class FakePC {
  constructor(config) {
    this.config = config;
    this.tracks = [];
    this.remoteDescription = null;
    this.localDescription = null;
    this.added = [];
    this.closed = false;
    FakePC.all.push(this);
  }
  addTrack(t) { this.tracks.push(t); }
  async createOffer() { return { type: 'offer', sdp: 'offer-sdp' }; }
  async createAnswer() { return { type: 'answer', sdp: 'answer-sdp' }; }
  async setLocalDescription(d) { this.localDescription = d; }
  async setRemoteDescription(d) { this.remoteDescription = d; }
  async addIceCandidate(c) { this.added.push(c); }
  close() { this.closed = true; }
}
FakePC.all = [];

function track() { return { enabled: true, stopped: false, stop() { this.stopped = true; } }; }

function makeClient(myId, peers = ['b']) {
  const sent = [];
  const states = [];
  const t = track();
  const stream = { getTracks: () => [t], getAudioTracks: () => [t] };
  const client = new VoiceClient({
    myId,
    peerIds: () => peers,
    send: (to, type, payload) => { sent.push({ to, type, payload }); return true; },
    onChange: (s) => states.push(s),
    deps: {
      getUserMedia: vi.fn().mockResolvedValue(stream),
      createPeer: (cfg) => new FakePC(cfg),
      createAudio: () => ({ play: () => Promise.resolve(), pause() {}, muted: false, srcObject: null }),
      createAudioContext: () => null,
      setInterval: () => 1,
      clearInterval: () => {},
    },
  });
  return { client, sent, states, track: t };
}

beforeEach(() => { FakePC.all = []; });

describe('VoiceClient signalling', () => {
  it('joining asks for the microphone and announces itself to every other player', async () => {
    const { client, sent } = makeClient('a', ['b', 'c']);
    await client.join([{ urls: ['stun:x'] }]);
    expect(client.status).toBe('on');
    expect(client.deps.getUserMedia).toHaveBeenCalledWith(expect.objectContaining({ audio: expect.any(Object), video: false }));
    expect(sent.map((s) => [s.to, s.type])).toEqual([['b', 'join'], ['c', 'join']]);
  });

  it('the smaller user id makes the offer when the other side joins', async () => {
    const { client, sent } = makeClient('a');
    await client.join([]);
    sent.length = 0;
    await client.handle({ from: 'b', type: 'join', payload: {} });
    expect(sent).toEqual([{ to: 'b', type: 'offer', payload: { type: 'offer', sdp: 'offer-sdp' } }]);
    expect(client.peers.get('b').pc.tracks).toHaveLength(1); // our mic is sent
  });

  it('the larger user id replies with join instead of offering (no offer collision)', async () => {
    const { client, sent } = makeClient('b', ['a']);
    await client.join([]);
    sent.length = 0;
    await client.handle({ from: 'a', type: 'join', payload: {} });
    expect(sent).toEqual([{ to: 'a', type: 'join', payload: {} }]);
    expect(FakePC.all).toHaveLength(0);
  });

  it('answers an offer and applies ICE candidates that arrived before the offer', async () => {
    const { client, sent } = makeClient('b', ['a']);
    await client.join([]);
    sent.length = 0;
    await client.handle({ from: 'a', type: 'offer', payload: { type: 'offer', sdp: 'x' } });
    await client.handle({ from: 'a', type: 'ice', payload: { candidate: 'c1' } });
    const pc = client.peers.get('a').pc;
    expect(pc.remoteDescription).toEqual({ type: 'offer', sdp: 'x' });
    expect(sent[0]).toEqual({ to: 'a', type: 'answer', payload: { type: 'answer', sdp: 'answer-sdp' } });
    expect(pc.added).toEqual([{ candidate: 'c1' }]);
  });

  it('queues early ICE candidates until the answer arrives', async () => {
    const { client } = makeClient('a');
    await client.join([]);
    await client.handle({ from: 'b', type: 'join', payload: {} });
    const pc = client.peers.get('b').pc;
    await client.handle({ from: 'b', type: 'ice', payload: { candidate: 'early' } });
    expect(pc.added).toEqual([]);
    await client.handle({ from: 'b', type: 'answer', payload: { type: 'answer', sdp: 'y' } });
    expect(pc.added).toEqual([{ candidate: 'early' }]);
  });

  it('ignores signals from people who are not players of this match, and while not in voice', async () => {
    const { client, sent } = makeClient('a', ['b']);
    await client.handle({ from: 'b', type: 'join', payload: {} }); // not joined yet
    expect(FakePC.all).toHaveLength(0);
    await client.join([]);
    sent.length = 0;
    await client.handle({ from: 'mallory', type: 'offer', payload: { type: 'offer', sdp: 'evil' } });
    expect(FakePC.all).toHaveLength(0);
    expect(sent).toHaveLength(0);
  });

  it('mute disables the microphone track; leave hangs up everyone and stops the mic', async () => {
    const { client, sent, track: t } = makeClient('a');
    await client.join([]);
    await client.handle({ from: 'b', type: 'join', payload: {} });
    client.setMuted(true);
    expect(t.enabled).toBe(false);
    client.setMuted(false);
    expect(t.enabled).toBe(true);
    sent.length = 0;
    const pc = client.peers.get('b').pc;
    client.leave();
    expect(sent).toEqual([{ to: 'b', type: 'leave', payload: {} }]);
    expect(pc.closed).toBe(true);
    expect(t.stopped).toBe(true);
    expect(client.status).toBe('off');
  });

  it('a peer leaving closes only that connection', async () => {
    const { client } = makeClient('a', ['b', 'c']);
    await client.join([]);
    await client.handle({ from: 'b', type: 'join', payload: {} });
    await client.handle({ from: 'c', type: 'join', payload: {} });
    await client.handle({ from: 'b', type: 'leave', payload: {} });
    expect([...client.peers.keys()]).toEqual(['c']);
  });

  it('reports a denied microphone permission clearly', async () => {
    const { client } = makeClient('a');
    client.deps.getUserMedia = vi.fn().mockRejectedValue(Object.assign(new Error('denied'), { name: 'NotAllowedError' }));
    await client.join([]);
    expect(client.status).toBe('error');
    expect(client.error).toMatch(/permission/i);
  });

  it('passes the ICE servers to every peer connection', async () => {
    const { client } = makeClient('a');
    const ice = [{ urls: ['stun:s'] }, { urls: ['turn:t'], username: 'u', credential: 'p' }];
    await client.join(ice);
    await client.handle({ from: 'b', type: 'join', payload: {} });
    expect(FakePC.all[0].config).toEqual({ iceServers: ice });
  });
});
