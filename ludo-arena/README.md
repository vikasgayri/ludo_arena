# LUDO ARENA

A full-stack multiplayer Ludo platform: a **Spring Boot 3 / Java 21** backend with MongoDB, JWT auth and a native **STOMP-over-WebSocket** game server, and a **React + Vite** frontend.

Coins in this product are **virtual, non-redeemable entertainment currency**. There is no betting, wagering, cash prize, withdrawal or entry-fee pool anywhere in the code, and there never should be.

> **Verification status (read this first).** See [TODO.md](TODO.md) for the honest, current list. In short: the Ludo game engine (59 tests) and the frontend (build + 66 tests) have been executed and pass. The Spring Boot layer (REST, security, Mongo, STOMP) was written but **could not be compiled in the authoring sandbox** because Maven Central was unreachable, so `mvn test` has not been run against it yet. Run the commands in [Testing](#testing) before relying on it.

## Repository layout

```
ludo-arena/
  backend/    Spring Boot app (Maven)        -> port 8080
  frontend/   React + Vite app               -> port 5173
  docs/       API.md, WEBSOCKET.md, MONGODB.md
  .env.example   every environment variable, both apps
```

The two apps are independent: the frontend only needs `VITE_API_BASE_URL` and `VITE_WS_URL`.

## Quick start (local)

Prerequisites: JDK 21, Maven 3.9+, Node 20+, and MongoDB (local or Atlas).

```bash
# 1. MongoDB
docker run -d --name ludo-mongo -p 27017:27017 mongo:7

# 2. Backend
cd backend
export MONGODB_URI=mongodb://localhost:27017/ludo_arena
export JWT_SECRET="$(openssl rand -base64 48)"
export FRONTEND_URL=http://localhost:5173
export MAIL_LOG_LINKS=true                   # dev only: logs password-reset links
export ADMIN_BOOTSTRAP_EMAIL=admin@example.com   # optional first SUPER_ADMIN
export ADMIN_BOOTSTRAP_PASSWORD='ChangeMe12345'
mvn spring-boot:run

# 3. Frontend (new terminal)
cd frontend
cp .env.example .env
npm install
npm run dev                                  # http://localhost:5173
```

The backend refuses to start if `JWT_SECRET` is missing or shorter than 32 bytes. Never commit real values; `.env` files are git-ignored.

## Environment variables

| Variable | App | Purpose |
|---|---|---|
| `MONGODB_URI` | backend | Mongo connection string (local or Atlas `mongodb+srv://…`). |
| `JWT_SECRET` | backend | HS256 signing key, at least 32 bytes. |
| `FRONTEND_URL` | backend | Allowed browser origin(s), comma separated. Used for CORS and the WebSocket origin check, and to build reset links. |
| `ADMIN_BOOTSTRAP_EMAIL` / `_PASSWORD` | backend | Optional. Creates the first `SUPER_ADMIN` if none exists. |
| `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | backend | SMTP for password-reset mail. |
| `MAIL_LOG_LINKS` | backend | Dev only: log the reset link instead of mailing it. |
| `TRUST_PROXY` | backend | Set `true` behind a trusted reverse proxy so rate limiting uses `X-Forwarded-For`. |
| `PAYMENTS_ENABLED`, `PAYMENT_KEY_ID`, `PAYMENT_KEY_SECRET`, `PAYMENT_WEBHOOK_SECRET` | backend | Payments. **Off by default.** |
| `VITE_API_BASE_URL` | frontend | e.g. `http://localhost:8080/api/v1`. |
| `VITE_WS_URL` | frontend | e.g. `http://localhost:8080/ws` (`http→ws`, `https→wss` is applied automatically). |

`backend/application.yml.example` documents the full `app.*` configuration tree.

## Architecture

```
Browser (React, Zustand)
   │  REST  /api/v1/**   (Axios, Bearer JWT, auto-refresh on 401)
   │  STOMP /ws          (@stomp/stompjs, JWT in the CONNECT frame header)
   ▼
Spring Boot
   ├─ security/   JwtAuthFilter (loads the user from the DB every request), RateLimitFilter
   ├─ controller/ thin REST controllers, ApiResponse envelope
   ├─ service/    Auth, User, Wallet, Store, Room, Match, Leaderboard, Notification, Report, Admin, Payment
   ├─ game/       framework-free Ludo engine (rules, dice, movement, turns)  ← unit-tested
   ├─ websocket/  STOMP auth/authorisation interceptor, @MessageMapping controller, presence tracking
   └─ MongoDB     12 collections (see docs/MONGODB.md)
```

Design decisions worth knowing:

- **The server is authoritative.** Dice come from a server-side `SecureRandom`. The client sends only "roll" and "move token N" with an `actionId`; the server validates turn order, legality and exact-finish rules, then broadcasts the new state. The frontend never computes rules: it draws the `validMoves` list the server sends.
- **Engine isolation.** `com.ludoarena.game` has no Spring dependency beyond the `@Service` annotation, so it is testable without a container (a scripted-dice subclass drives deterministic tests).
- **Idempotency and ordering.** Every broadcast carries a monotonically increasing `seq` and per-event `eventId`; the client ignores stale or duplicate frames. Client `actionId`s make retried roll/move commands safe.
- **Reconnection.** A player who drops stays in the match. On reconnect the client sends `/app/match.requestState` with a rotating reconnect token and receives a full snapshot on `/user/queue/state`. Unattended turns time out (30 s default) and are auto-played; three missed turns forfeits.
- **Wallet integrity.** The ledger is append-only. Each credit/debit carries a unique `referenceId` (the idempotency key), and the balance is changed with a guarded atomic `$inc` that also records the applied reference, so a retried or crashed operation applies exactly once. No Mongo multi-document transaction is required, so it also works on a standalone Mongo.
- **Admin protection is server-side.** Every `/api/v1/admin/**` route needs `ADMIN`/`SUPER_ADMIN` (filter chain **and** `@PreAuthorize`); role changes, manual credits and platform settings need `SUPER_ADMIN`. Hiding routes in the UI is only a convenience.

## Game rules implemented

- 2-player (Red vs Yellow) and 4-player (Red, Green, Yellow, Blue) modes.
- A token leaves base only on a **6**. Tokens travel a 52-cell track, then a 5-cell home column; **exact roll** is required to finish.
- Landing on an opponent's token on a non-safe cell **captures** it (back to base). Eight cells are safe (each colour's start square and the star square 8 steps after it).
- Extra turn on a six, a capture, or bringing a token home (each configurable by a super admin). Three consecutive sixes forfeit the turn.
- Turn timer with auto-play; last player standing wins on forfeit; finishing order determines rank.

## API, WebSocket and data docs

- [docs/API.md](docs/API.md): every REST endpoint, auth model, error format.
- [docs/WEBSOCKET.md](docs/WEBSOCKET.md): STOMP destinations, payloads, reconnection protocol.
- [docs/MONGODB.md](docs/MONGODB.md): collections, key fields and indexes.

## Testing

```bash
# Backend: unit + Mockito + MockMvc/WebSocket tests
cd backend
mvn test                                   # unit tests (game engine, security, services)

# Optional full integration tests against a real Mongo
docker run -d -p 27017:27017 mongo:7
TEST_MONGODB_URI=mongodb://localhost:27017/ludo_arena_test mvn test

# Frontend
cd frontend
npm test            # Vitest + React Testing Library
npm run build       # production build
```

Integration tests (`ApiIntegrationTest`, `GameFlowWebSocketTest`) are skipped unless `TEST_MONGODB_URI` is set.

## Deployment

**MongoDB Atlas.** Create a cluster, a database user and an IP/VPC allow-list entry, then set `MONGODB_URI` to the `mongodb+srv://` string. Indexes are created automatically on first start (`auto-index-creation: true`); for large production datasets create them ahead of time from docs/MONGODB.md.

**Backend on Render (or any container host).** Build with `mvn -DskipTests package`, run `java -jar target/ludo-arena-*.jar`. Set `MONGODB_URI`, `JWT_SECRET`, `FRONTEND_URL` (your Vercel URL), `TRUST_PROXY=true`, `PORT` is honoured. Health endpoint: `/actuator/health`. Use a plan that keeps WebSockets open (no sleeping instances during matches).

**Frontend on Vercel.** Root directory `frontend`, build `npm run build`, output `dist`. Set `VITE_API_BASE_URL=https://<backend>/api/v1` and `VITE_WS_URL=https://<backend>/ws`. Add a rewrite of all paths to `/index.html` for client-side routing (Vercel does this automatically for Vite projects).

## Scaling notes

- The simple in-memory STOMP broker and the per-match in-process lock are correct for **one backend instance**. To scale horizontally, switch to a broker relay (RabbitMQ/ActiveMQ STOMP or Redis-backed fan-out) and replace the in-process match lock with a distributed lock or sticky routing by `matchId`. The wallet and room logic already rely on atomic Mongo operations, not in-process state.
- The rate limiter is in-memory per instance; use a shared store (Redis) when running several.
- Matches persist after every action, so a restarted instance resumes active matches; the timeout scheduler picks up overdue turns on start.

## Payment architecture (disabled by default)

Payments exist so the platform *can* sell virtual-coin packs through a provider after legal and provider approval. With `PAYMENTS_ENABLED=false` (default) every payment mutation returns `403 PAYMENTS_DISABLED` and the store hides coin packs.

When enabled: the server creates a provider order (amount comes from a server-side package list, never from the client); the browser completes checkout with the provider; the server **verifies the HMAC signature** on the payment (and independently on the provider **webhook**) before crediting coins; credits use the order id as the wallet idempotency key, so replays and duplicate webhooks cannot double-credit; an hourly job reconciles orders still `CREATED` against the provider. The Razorpay adapter is behind a `PaymentProvider` interface. It has **not** been exercised against the live gateway.

Coins can never be withdrawn, transferred between users, or staked in a match.

## Security summary

BCrypt (cost 12) with timing equalisation on unknown emails; 15-minute access JWT plus opaque, hashed, rotating refresh tokens with reuse detection; hashed single-use password-reset tokens (30 min); per-IP rate limits on auth endpoints; strict CORS and WebSocket origin allow-list; suspended accounts are rejected immediately (the user is re-loaded on each request); password hashes are never serialised; Bean Validation on every request body and WebSocket payload; STOMP subscriptions are authorised per room/match.
