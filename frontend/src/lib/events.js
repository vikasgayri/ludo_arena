/** Human-readable text for a server GameEvent. Pure presentation — no rules. `nameOf` returns "You" for the viewer. */
const poss = (n) => (n === 'You' ? 'Your' : `${n}'s`);

export function describeEvent(e, nameOf) {
  const who = nameOf(e.userId);
  const you = who === 'You';
  const d = e.data || {};
  switch (e.type) {
    case 'MATCH_STARTED': return `Match started — ${nameOf(d.firstUserId)} ${nameOf(d.firstUserId) === 'You' ? 'go' : 'goes'} first`;
    case 'DICE_ROLLED': return `${who} rolled a ${d.value}`;
    case 'TOKEN_MOVED': return `${who} moved token ${Number(d.tokenIndex) + 1}`;
    case 'TOKEN_CAPTURED': return `${who} captured ${poss(nameOf(d.victimUserId))} token!`;
    case 'TOKEN_HOME': return `${who} brought token ${Number(d.tokenIndex) + 1} home`;
    case 'NO_MOVES': return you ? 'You have no legal move' : `${who} has no legal move`;
    case 'TRIPLE_SIX': return `${who} rolled three sixes — turn forfeited`;
    case 'TURN_CHANGED': return d.extraTurn
      ? `${nameOf(d.nextUserId)} ${nameOf(d.nextUserId) === 'You' ? 'roll' : 'rolls'} again`
      : `${poss(nameOf(d.nextUserId))} turn`;
    case 'TURN_TIMEOUT': return `${who} ran out of time (auto-played)`;
    case 'PLAYER_FORFEITED': return `${who} left the game`;
    case 'PLAYER_DISCONNECTED': return `${who} disconnected`;
    case 'PLAYER_RECONNECTED': return `${who} reconnected`;
    case 'MATCH_FINISHED': {
      if (!d.winnerId) return 'The match has ended';
      const w = nameOf(d.winnerId);
      return w === 'You' ? 'You win the match!' : `${w} wins the match!`;
    }
    case 'MATCH_ABANDONED': return 'The match was abandoned — no winner';
    default: return e.type;
  }
}
