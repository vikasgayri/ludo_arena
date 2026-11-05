# Ludo Arena - Implementation Roadmap

Based on `PRODUCTION_AUDIT.md` (2026-11-05). Work is done on the existing project; nothing is rewritten.

**Constraint that shapes the plan:** Maven Central is unreachable from the build sandbox, so Spring code cannot be compiled or
tested there. For every backend change the plan is: (1) write the code and tests carefully, (2) verify what can be verified
in the sandbox (framework-free engine/bot code via `javac` harness, frontend via `npm`), (3) the owner runs
`mvn clean test` on the Mac and pastes the output, (4) fix and repeat. A phase is only called complete after step 3 is green.

**Stop-and-ask list (never done automatically):** deleting files/folders (including the nested `ludo-arena/` copy,
`ludo-arena.tar.gz`, `_audit_snapshot.tgz`), removing features, destructive schema changes, deleting DB data, changing
production credentials, `git push`, deploying, enabling live payments, rewriting git history.

## Phase 0 - Audit and docs (this deliverable)
Files: `docs/PRODUCTION_AUDIT.md`, `docs/IMPLEMENTATION_ROADMAP.md`, `docs/TESTING_REPORT.md`. Done.

## Phase 1 - Backend stabilization (needs owner's Mac)
| Step | Action | Files |
|------|--------|-------|
| 1.1 | Remove hard-coded admin defaults (S1); fix `DataSeeder` Javadoc; document env vars | `application.yml`, `DataSeeder`, `.env.example` |
| 1.2 | Owner: `mvn clean test` + start Mongo + `mvn spring-boot:run` with `JWT_SECRET`; confirm startup after the CORS fix; hit `/actuator/health`, register, login, protected and admin routes | - |
| 1.3 | Owner: `TEST_MONGODB_URI=mongodb://localhost:27017/ludo_arena_test mvn test` to run the 16 skipped integration tests; fix whatever fails (positional updates, indexes) | tests/services as needed |
| 1.4 | Fix W1 (failed purchase blocks forever): reopen a FAILED *insufficient-funds* ledger entry atomically (`FAILED -> PENDING` guarded by `appliedRefs` not containing the reference) and add a balance pre-check in `StoreService.purchase` | `WalletService`, `StoreService`, `WalletServiceTest` |
| 1.5 | Fix L1 (lost updates): replace whole-document `save()` on `User` by targeted `$set` updates through a small `UserWriter` helper | `UserService`, `StoreService`, `AuthService`, `AdminService` |
| 1.6 | M1: make finalization re-runnable (per-player `finalizedPlayers` marker + startup/scheduled sweep for FINISHED matches without `finalized`) | `MatchService`, `Match` |

Exit: `mvn clean test` green on the Mac, startup log pasted, integration tests green or each failure explained.

## Phase 2 - Auth completeness
* Replace link-reset with **email OTP**: 6-digit code, hashed, 10-minute expiry, 5 attempts max, per-email and per-IP rate limit, single use, revokes all refresh tokens; keep the old link flow working for backward compatibility until the frontend switches. New collection `passwordResetOtps` (TTL index).
* Email verification: add `emailVerified` (default true for existing users so nobody is locked out), verification OTP on register, gate signup bonus and rewards behind it (also S5).
* Per-account login throttling (S3); keep generic error messages; never log or return hashes.
* Tests: OTP happy path, expiry, wrong code x5, reuse, rate limit, enumeration safety.

## Phase 3 - Game rules verification
* Add engine tests for: exact finish with every dice value, capture on home-column boundary (never capturable), multiple opponent tokens on one cell, forfeit of the current player mid-roll, 4P ranking after two forfeits, duplicate `actionId`, roll after roll, move without roll, move of another colour's token, state after timeout on `awaitingMove`, scripted full 2P and 4P games to completion.
* Decide and document the block/stack rule (current: no blocks).
* Verified in the sandbox with the existing `javac` harness (engine is framework-free).

## Phase 4 - Multiplayer verification
* Run `GameFlowWebSocketTest` (two STOMP clients) on the Mac; add 4-client variant, reconnect-with-token test, duplicate roll event test, simultaneous join of the last seat (race), simultaneous ready, leave during play.
* Abandon handling: if all humans are disconnected for > N seconds, mark `ABANDONED` (no rewards, no win) instead of awarding the last remaining account a win; wire the existing enum and the admin chart.
* Economy guard (S5): reward only if the winner made >= K moves and opponents did not forfeit before turn K.

## Phase 5 - Play With Computer (bots)
**Design (backend-authoritative, reuses the engine):**
* `BotService` + `BotStrategy` interface (`int chooseMove(GameState, PlayerState, List<Integer> validMoves)`), implementations `EasyStrategy` (uniform random legal move), `MediumStrategy` (priority: capture > unlock > home > progress, avoid landing within 1-6 cells in front of an opponent token on a non-safe cell), `HardStrategy` (score every legal move: capture value, finish, safe-cell landing, leaving danger, opponent progress denial, home-column advance, exposure after move; deterministic tie-break).
* Strategies read `LudoRulesService` for legality/captures; they never roll dice (the engine's `DiceService` rolls for them) and never bypass `engine.rollDice/moveToken`.
* Seats: bot `userId = "BOT:<matchId>:<seat>"`, name/avatar/difficulty stored on `PlayerState` (new fields `bot`, `difficulty`). Bots are **not** added to `Match.playerIds`; `finalizeMatch` skips bots for stats/XP/wallet/leaderboard; the human gets rewards only for bot matches according to a separate (lower) setting to prevent farming.
* Scheduling: reuse the existing per-second scheduler: when the current player is a bot and `turnDeadline` allows, a `BotTurnScheduler` takes the match lock, waits a randomised "thinking" delay (stored as `botActAtEpochMs` in `GameState`, so it survives restarts), then runs roll -> (delay) -> move through `MatchService.act` with a deterministic `actionId`. One decision per tick per match, so turns cannot overlap; restart recovery is free because the due time is persisted.
* API: `POST /api/v1/rooms/bot` `{players: 2|4, difficulty: EASY|MEDIUM|HARD}` creates a private room already in play (match starts immediately). One active room per user rule still applies.
* Tests: strategy unit tests per difficulty with hand-built boards (HARD must capture when possible, must prefer a safe cell over an exposed one; EASY never returns an illegal move over 10k random states), full bot-vs-bot simulations to completion for 2P/4P, restart-recovery test, no-overlap test, rewards-skip test.
* Frontend: game mode selection page (`/play`: Online / Private / Bots), bot setup (players + difficulty), bot avatar and "Computer" label, "thinking" indicator, bot move highlighting, human-turn indicator.

## Phase 6 - Frontend / gameplay
Mode selection page, result screen with winner animation, tweened token movement along the path, dice roll animation, sound effects with mute (bundled small audio files or WebAudio-generated tones, respecting the stored `sound` preference), capture toast, reconnect banner, loading skeletons, responsive checks at 360/768/1280 px, public profile route, a11y labels. `vercel.json` with SPA rewrite.

## Phase 7 - Wallet and store
W1 fix (Phase 1), refund/reversal transaction type with ledger entry, admin adjustment audit, concurrency tests with real Mongo, balance never negative property test.

## Phase 8 - Leaderboard / history / profiles
Recompute-from-matches reconciliation job (admin-triggered) to detect drift between `users.stats`, `leaderboards` and finished matches; tests.

## Phase 9 - Admin panel
Add **announcements** (collection, admin CRUD, public `GET /announcements/active`, banner in `AppLayout`), "active matches" view with force-end (forfeit all) action, admin permission test matrix (USER/ADMIN/SUPER_ADMIN x every `/admin` route), every mutation in `adminLogs`.

## Phase 10 - Payments (test mode only)
Run against Razorpay **test** keys on the Mac only if the owner supplies them; add refund/dispute webhook handling, rate limit on `createOrder`, replay and signature tampering tests with mocked provider (no credentials needed). Live payments stay disabled; coins remain non-redeemable.

## Phase 11 - Security fixes
S2-S7 as listed in the audit; CSP and security headers via `vercel.json`; WebSocket throttling; dependency review (`mvn dependency:tree`, `npm audit`) on the Mac.

## Phase 12 - Docs sync
Update README, `docs/API.md`, `docs/WEBSOCKET.md`, `docs/MONGODB.md`.

## Phase 13 - Full test run
`npm ci && npm run build && npm test`, `mvn clean test`, integration tests, Playwright E2E (register/login, private 2P, public matchmaking, 4P, bot match, wallet, admin, 390 px mobile) against a locally started backend + Mongo on the Mac. `TESTING_REPORT.md` is updated after each run; a test is "passed" only if executed and green.

## Phase 14 - Deployment preparation (no deploy)
Vercel: root `frontend`, build `npm run build`, output `dist`, SPA rewrite, `VITE_API_BASE_URL`/`VITE_WS_URL`. Render: Docker runtime from `backend/Dockerfile` (add dependency-layer caching and non-root user), health check `/actuator/health`, env vars, WSS note. Atlas: indexes are auto-created (`auto-index-creation`), IP access list, backups, least-privilege DB user. Complete `.env.example`. Pre-flight checklist for HTTPS/WSS/CORS (`FRONTEND_URL` exact origin).

## Duplicate structure
Report only: root is canonical; remove nested `ludo-arena/` and `ludo-arena.tar.gz` after the owner approves.
