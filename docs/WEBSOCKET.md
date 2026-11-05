# WebSocket (STOMP)

Native Spring WebSocket + STOMP at `ws://<host>/ws` (plain WebSocket, **no SockJS, no Socket.IO**). Heartbeats 10 s.

## Authentication

Send the access JWT in the STOMP **CONNECT frame header**: `Authorization: Bearer <jwt>`. It is never put in the URL. The connection is rejected if the token is missing, invalid or the account is suspended. Every SUBSCRIBE is authorised: room topics require room membership, match topics require being a player (admins may observe).

## Client → server (`/app/…`)

| Destination | Payload | Effect |
|---|---|---|
| `/app/room.join` | `{roomId}` | Attach this socket to the room's presence. |
| `/app/room.leave` | `{roomId}` | Leave the room. |
| `/app/room.ready` | `{roomId, ready}` | Toggle ready; the match starts when all are ready. |
| `/app/match.rollDice` | `{matchId, actionId}` | Roll the dice (server-side RNG). |
| `/app/match.moveToken` | `{matchId, tokenIndex 0..3, actionId}` | Move a token. |
| `/app/match.requestState` | `{matchId, reconnectToken?}` | Full snapshot (reconnection path). |

`actionId` (≤64 chars) is an idempotency key: replaying one is a no-op (window of the last 100 per match).

## Server → client

| Destination | Payload |
|---|---|
| `/topic/room/{roomId}` | `RoomUpdate { type, room, matchId }`; `type` ∈ `PLAYER_JOINED`, `PLAYER_LEFT`, `PLAYER_READY`, `MATCH_STARTED`, `ROOM_CLOSED`. |
| `/topic/match/{matchId}` | `MatchUpdate { matchId, events[], state }` after every accepted action. |
| `/user/queue/state` | `StateSnapshot { state, reconnectToken }` in reply to `requestState`. |
| `/user/queue/errors` | `{ code, message }` when an action is rejected. |

`state` (`MatchStateDto`) includes `players[].tokens` (relative steps: `-1` base, `0..50` track, `51..55` home column, `56` finished), `currentUserId`, `diceValue`, `awaitingMove`, **`validMoves`** (token indexes the current player may move), `turnDeadline` (epoch ms), `serverTime`, and `seq`.

Events (`type`): `MATCH_STARTED`, `DICE_ROLLED`, `TOKEN_MOVED`, `TOKEN_CAPTURED`, `TOKEN_HOME`, `NO_MOVES`, `TRIPLE_SIX`, `TURN_CHANGED`, `TURN_TIMEOUT`, `PLAYER_FORFEITED`, `PLAYER_DISCONNECTED`, `PLAYER_RECONNECTED`, `MATCH_FINISHED`. Each has a unique `eventId` and increasing `seq`.

## Client rules (as implemented in `frontend/src/store/gameStore.js`)

1. Ignore any update whose `state.seq` is lower than the one held; de-duplicate events by `eventId`.
2. After (re)connecting, send `requestState` with the stored reconnect token and replace local state with the snapshot. The server rotates the token on each request.
3. Never compute rules locally; enable only the tokens in `validMoves`.

## In-match chat (online matches only)

* Send: `/app/match.chat` with `{ "matchId": "...", "text": "..." }`. The sender is the authenticated session user.
  Text is trimmed, control characters removed, max 200 characters; max 5 messages per 10 s per user
  (`CHAT_RATE_LIMITED` on `/user/queue/errors`). Not available in matches against the computer (`CHAT_UNAVAILABLE`).
* Receive: subscribe `/topic/match/{matchId}/chat` (same rule as the match topic: participants and admins only).
  Payload: `{ id, matchId, userId, name, color, text, at }`.
* History: `GET /api/v1/matches/{id}/chat` returns the last 50 messages, oldest first. Messages expire after 30 days.

## Voice chat (online matches only, WebRTC)

Audio is peer-to-peer between browsers (one RTCPeerConnection per other human player); the server never receives audio.
* ICE servers: `GET /api/v1/voice/ice-servers` (authenticated) -> `[{ urls, username?, credential? }]` (STUN, plus TURN when configured).
* Signalling send: `/app/match.voice` with `{ matchId, to, type, payload }`, `type` = `join | leave | offer | answer | ice`.
  The server checks that sender and `to` are both human players of the running match (not vs computer), limits the
  payload to 20 000 characters and 120 signals per 10 s per user, then delivers to that one user.
* Signalling receive: `/user/queue/voice` -> `{ matchId, from, fromName, type, payload }`.
* Offer rule (no collisions): on `join` from X, the player with the smaller user id sends the offer; the other replies `join`.
