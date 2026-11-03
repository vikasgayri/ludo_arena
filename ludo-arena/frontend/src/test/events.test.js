import { describe, expect, it } from 'vitest';
import { describeEvent } from '../lib/events';

const names = { u1: 'You', u2: 'Ravi' };
const nameOf = (id) => names[id] || 'Someone';
const ev = (type, userId, data) => ({ type, userId, data });

describe('describeEvent', () => {
  it('uses correct grammar for the viewer and opponents', () => {
    expect(describeEvent(ev('TURN_CHANGED', 'u2', { nextUserId: 'u1', extraTurn: false }), nameOf)).toBe('Your turn');
    expect(describeEvent(ev('TURN_CHANGED', 'u1', { nextUserId: 'u2', extraTurn: false }), nameOf)).toBe("Ravi's turn");
    expect(describeEvent(ev('TURN_CHANGED', 'u1', { nextUserId: 'u1', extraTurn: true }), nameOf)).toBe('You roll again');
    expect(describeEvent(ev('NO_MOVES', 'u1', {}), nameOf)).toBe('You have no legal move');
    expect(describeEvent(ev('NO_MOVES', 'u2', {}), nameOf)).toBe('Ravi has no legal move');
    expect(describeEvent(ev('MATCH_FINISHED', null, { winnerId: 'u1' }), nameOf)).toBe('You win the match!');
    expect(describeEvent(ev('MATCH_FINISHED', null, { winnerId: 'u2' }), nameOf)).toBe('Ravi wins the match!');
  });

  it('describes captures, dice rolls and unknown events', () => {
    expect(describeEvent(ev('TOKEN_CAPTURED', 'u1', { victimUserId: 'u2' }), nameOf)).toBe("You captured Ravi's token!");
    expect(describeEvent(ev('TOKEN_CAPTURED', 'u2', { victimUserId: 'u1' }), nameOf)).toBe("Ravi captured Your token!");
    expect(describeEvent(ev('DICE_ROLLED', 'u2', { value: 6 }), nameOf)).toBe('Ravi rolled a 6');
    expect(describeEvent(ev('SOMETHING_NEW', 'u2', {}), nameOf)).toBe('SOMETHING_NEW');
  });
});
