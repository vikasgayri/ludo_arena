# Ludo Arena - Production Audit

Audit date: 2026-11-05. Scope: the canonical project at the repository root (`backend/`, `frontend/`, `docs/`).

**Method and honesty note.** Findings below come from (a) reading the source files named in each row, (b) the Maven
Surefire reports from the owner's own `mvn` run on 2026-11-01, (c) `npm ci`, `vitest run` and `vite build` executed in
the audit sandbox on 2026-11-05, and (d) `git` inspection of the owner's repository. Maven Central is blocked from the
sandbox and from the connected device shell, so **nothing in the Spring layer was compiled or started by this audit**.
Items marked *read-only finding* are derived from reading code; they are not reproduced at runtime. Nothing here is
claimed to work unless it says it was executed.

## 1. Repository and structure

| # | Finding | Evidence |
|---|---------|----------|
| R1 | Canonical project is the repository root. A **second full copy** lives in the tracked nested folder `ludo-arena/` (202 tracked files) and a tracked archive `ludo-arena.tar.gz` (~180 KB). | `git ls-files` per top-level entry: backend 114, frontend 83, ludo-arena 202, ludo-arena.tar.gz 1 |
| R2 | The nested copy is **older**: it lacks the root's `SecurityConfig` CORS `@Qualifier` fix, the two test fixes, and `Dockerfile`/`.dockerignore`. Only 4 Java files and `application.yml` differ, plus the two Docker files. | `diff -rq backend ludo-arena/backend` |
| R3 | `application.yml` differs between root and nested copy (see S1). The nested copy has the safe empty defaults. | same |
| R4 | Working tree is clean except one untracked scratch file `_audit_snapshot.tgz` created by this audit (to be removed with owner approval). | `git status --short` |
| R5 | Remote `origin` is a GitHub repository. Whether the commits were ever pushed is not known to this audit. | `git remote -v` |

**Recommended cleanup (not executed, needs approval):** keep the root as canonical; remove `ludo-arena/` and `ludo-arena.tar.gz`
from git in one commit (`git rm -r`), keep a local backup first. Nothing was deleted.

## 2. What works (verified by execution)

| Area | Verified how |
|------|--------------|
| Backend unit tests: 125 tests, 0 failures, 0 errors, 16 skipped | Owner's `mvn clean test` on Java 21, Surefire reports dated 2026-11-01 10:52 |
| Backend jar builds (`ludo-arena-backend-1.0.0.jar`) | Owner's `mvn package`, `target/` present |
| Frontend: 66 Vitest tests in 9 files pass | Executed in sandbox 2026-11-05 |
| Frontend production build succeeds (1725 modules, JS 398 kB / 121 kB gzip) | Executed in sandbox 2026-11-05 |

## 3. What is NOT verified

* **Application startup.** The CORS bean ambiguity (`corsConfigurationSource` vs `mvcHandlerMappingIntrospector`) was fixed with
  `@Qualifier("corsConfigurationSource")` in `SecurityConfig.filterChain`. No one has confirmed startup after that fix.
* **All 16 skipped tests** are `ApiIntegrationTest` (13) and `GameFlowWebSocketTest` (3); they are gated on `TEST_MONGODB_URI` and
  have **never run**. REST + Mongo behaviour and a full two-client STOMP game are therefore unproven.
* Mongo positional/array updates (`players.$`, `players.$[]`, `appliedRefs` `$push`+`$slice`), TTL indexes, unique indexes with
  `auto-index-creation`.
* Email delivery, Razorpay (adapter written, never run against the gateway), live browser <-> backend play, mobile layout against real data.

## 4. Feature status

| Feature | Status | Notes |
|---------|--------|-------|
| Register / login / refresh rotation / logout | Implemented, unit-tested (12 AuthService tests); runtime unverified | `AuthService`, opaque refresh tokens, reuse detection |
| Password reset | **Partial** - link token (30 min, hashed), not email OTP | Spec asks for OTP; see A2 |
| Email verification | **Missing** | No field, no flow |
| 2P / 4P Ludo engine | Implemented, 59 engine-level tests pass | Backend-authoritative |
| Private/public rooms, codes, ready, auto-start | Implemented; unit tests with mocks | Race behaviour unproven without Mongo |
| WebSocket sync, reconnect token, timeouts, forfeit | Implemented; `StompAuthInterceptor` tested | End-to-end unproven |
| **Play with Computer (bots)** | **Missing** | No bot code at all |
| Game mode selection page | **Missing** | Routes: `/lobby`, `/rooms/create`, `/rooms/join` only |
| Bot difficulty selection UI | **Missing** | |
| Dice/token animation, sound | **Missing** | Tokens jump; `sound` preference stored, no audio assets |
| Capture/winner celebration, result screen | Partial | Event log text only; to be confirmed in Phase 6 |
| Wallet ledger, idempotency | Implemented, 7 unit tests | Bug W1 below |
| Store / inventory / equip | Implemented | Bug W1; lost update L1 |
| Leaderboard, match history, notifications, achievements | Implemented | Public profile has API but no UI route |
| Admin: users, matches, transactions, store, reports, logs, settings, stats | Implemented | **Announcements missing**; no force-end match |
| Payments (Razorpay) | Implemented, **disabled by default**, never run | No refund handling |
| Draw / abandon | **Missing** | `MatchStatus.ABANDONED` exists, nothing ever sets it |
| Vercel config | **Missing** | No `vercel.json`, SPA rewrite absent |
| Render config | Partial | `backend/Dockerfile` exists (multi-stage Java 21); no `render.yaml`, no health-check docs |

## 5. Security findings

Severity: Critical / High / Medium / Low. All are *read-only findings* unless stated.

| ID | Sev | Finding | Location | Fix (planned) |
|----|-----|---------|----------|---------------|
| S1 | **High** | **Default bootstrap SUPER_ADMIN credentials are committed**: `${ADMIN_BOOTSTRAP_EMAIL:vikasgayri@gmail.com}` / `${ADMIN_BOOTSTRAP_PASSWORD:vikas12345}`. They are in `HEAD` (verified with `git show HEAD:...`). `DataSeeder` Javadoc claims "No credentials are hard-coded", which is false. Today the 9-character default is rejected by the `< 10` check, so no admin is created from it - but any longer value, or a copy of this file elsewhere, becomes a known-password super admin. If the repo was ever pushed to GitHub the string is public. | `backend/src/main/resources/application.yml` lines 41-43 | Empty defaults (as in the nested copy). Owner: rotate any real account that used this password; consider history rewrite only if pushed (needs approval). |
| S2 | Medium | Access + refresh tokens stored in `localStorage` (readable by any XSS). | `frontend/src/lib/tokens.js` | Keep (SPA trade-off) but add CSP headers on Vercel, no `dangerouslySetInnerHTML`; document. |
| S3 | Medium | Rate limiter is per client IP only, fixed window; no per-account lockout; with `TRUST_PROXY=true` the **first** `X-Forwarded-For` entry is used, which a client can forge unless the proxy overwrites it. | `RateLimitFilter.clientIp` | Add per-email counter on login; use last-trusted-hop logic. |
| S4 | Medium | No inbound WebSocket rate limiting or message-size limits; any authenticated user can flood `/app/**`. | `WebSocketConfig` | Add `setMessageSizeLimit`, per-session throttle in the interceptor. |
| S5 | Medium | Economy abuse: win reward is granted when the opponent is *forfeited*, and signup bonus is per account with no email verification. Two accounts in a private 2P room: one idles for 3 x 30 s turns, the other collects `matchWinRewardCoins`. Coins are non-redeemable but buy store items. | `MatchService.finalizeMatch`, `AuthService.register` | Require minimum number of own moves/turns for reward; no reward when opponent forfeited before N turns; email verification gate on bonuses. |
| S6 | Low | Suspended user keeps an open STOMP session until disconnect; status is checked only on CONNECT. Admin suspension does forfeit their running match. | `StompAuthInterceptor` | Re-check status on SEND (cached) or drop sessions on suspension. |
| S7 | Low | `GET /rooms/{id}` and join accept a raw ObjectId for **private** rooms, bypassing the code. Ids are unguessable but appear in DTOs the members see. | `RoomService.resolve/join` | Reject join-by-id for private rooms unless already a member. |
| S8 | Low | Password policy: 8-72 chars with a letter and digit. Adequate. No breached-password check. | `Requests.PASSWORD_RULE` | Optional. |
| S9 | Info | Good: BCrypt(12), constant-time dummy hash on unknown email, opaque hashed rotating refresh tokens with family revocation, reset tokens hashed, role read from DB on each request (suspension/role change immediate), admin routes guarded three times, `ApiResponse` never includes `passwordHash`, payments off by default, HMAC signature compare is constant-time, JWT secret required >= 32 bytes. | various | - |

## 6. Correctness / data-consistency findings

| ID | Sev | Finding | Location |
|----|-----|---------|----------|
| W1 | **High** | **A failed store purchase blocks that item forever.** On `INSUFFICIENT_COINS`, `WalletService.settle` marks ledger entry `PURCHASE:{user}:{item}` as FAILED. Every later attempt finds that entry in `apply()` (`status != PENDING` -> returned unchanged), so `StoreService.purchase` throws `PURCHASE_FAILED` even after the user earns the coins. There is no balance pre-check. | `WalletService.apply/settle`, `StoreService.purchase` |
| L1 | Medium | **Lost updates from whole-document `save()`** on `User`: `UserService.update`, `StoreService.equip/unequip`, `AuthService.resetPassword/changePassword`, `AdminService.updateStatus/updateRole` read a `User`, change a field, and `save` the entire document. Concurrent `$inc stats.*` / `activeRoomId` changes made by `MatchService.finalizeMatch`/`RoomService` in between are overwritten (stats roll back, stale `activeRoomId`). | listed methods |
| M1 | Medium | **Match finalization is not crash-safe.** `finalized=true` is set first, then stats/XP/coins/notifications are applied per player inside try/catch. A crash or exception after the claim means rewards are never retried. Also, between saving `status=FINISHED` and the claim there is no recovery sweep. | `MatchService.finalizeMatch` |
| M2 | Medium | Single-instance only: per-match `ReentrantLock` map, in-memory STOMP broker, in-memory rate limiter and `SessionTracker`. Documented in README; fine for one Render instance, unsafe for 2+. The `locks` map is never pruned (slow memory leak). | `MatchService`, `WebSocketConfig` |
| M3 | Medium | If every human disconnects, auto-play continues until forfeits; last non-forfeited player "wins". No ABANDONED/draw outcome. | `LudoEngineService.forfeit/handleTimeout` |
| M4 | Low | `RoomService.tryStart` failure after `matchService.startMatch` has saved the match leaves an orphan ACTIVE match. | `RoomService.tryStart` |
| M5 | Low | `processTimeouts` runs every second and loads each overdue match one by one (indexed by `status_deadline`); fine at small scale. | `MatchService.processTimeouts` |
| P1 | Medium | Payments: no refund/dispute events handled; `createOrder` not rate-limited; never executed against Razorpay test mode. Crediting design itself is sound (signature verified server-side, amount checked on webhook, unique `PAY:{paymentId}` ledger key). | `PaymentService`, `RazorpayProvider` |
| A1 | Low | `DataSeeder` Javadoc contradicts `application.yml` (see S1). | - |
| A2 | Medium | Password reset is link based; no OTP, no attempt counter. | `AuthService.forgotPassword/resetPassword` |

## 7. Game engine review (read + 59 passing engine tests)

Verified by reading `LudoRulesService`, `TokenMovementService`, `TurnManagerService`, `LudoEngineService`:

* 52-cell track, per-colour start offsets, 6 steps in the home column, 56 = finished; exact roll required to finish; six needed to leave base. **Correct.**
* Safe cells (starts + stars) protect all tokens; captures send tokens to base; extra turn on 6 / capture / home (configurable); three consecutive sixes voids the turn. **Correct.**
* Dice only from `SecureRandom` on the server; roll when `awaitingMove` or `diceValue != null` is rejected (stale dice reuse impossible); `actionId` window of 100 gives duplicate-request idempotency; all mutation under per-match lock. **Sound for one instance.**
* **Rule gap to decide:** stacked own tokens ("blocks") and two opponents' tokens landing together are not treated specially; every opponent token on a non-safe cell is captured. This is a legitimate house-rule choice; documented, not a bug.
* No draw outcome (a game always has a winner or becomes won by forfeit).
* Bot readiness: `PlayerState.userId` is a string, so bots can reuse the engine as seats. Bots must **not** appear in `Match.playerIds` (used for history, active match lookup and wallet/stat updates) and `finalizeMatch` must skip them.

## 8. API / integration notes

* Response envelope and error codes are consistent (`GlobalExceptionHandler`); validation uses Bean Validation records in `Requests`.
* `docs/API.md` and `docs/WEBSOCKET.md` exist; they must be extended for bots and announcements.
* Frontend `VITE_API_BASE_URL` / `VITE_WS_URL` are env-driven. Refresh is single-flight on 401; STOMP re-authenticates in `beforeConnect`.
* Frontend admin pages have no Vitest coverage.

## 9. Deployment notes

* `backend/Dockerfile`: multi-stage `maven:3.9-eclipse-temurin-21` -> `eclipse-temurin:21-jre`, `mvn clean package -DskipTests`, port 8080. Observations: tests are skipped in the image build (acceptable if CI runs them); no non-root user; no dependency-layer caching (`pom.xml` is not copied first); no `HEALTHCHECK` (Render uses its own `/actuator/health`, which is permitted by `SecurityConfig`).
* Required runtime env: `MONGODB_URI`, `JWT_SECRET` (>= 32 bytes; startup fails without it - good), `FRONTEND_URL`, `PORT`, `TRUST_PROXY=true` behind Render, mail variables only if email is wanted.
* Frontend: needs `vercel.json` SPA rewrite, `VITE_*` envs. Not deployed; **nothing will be deployed without approval.**

## 10. Test coverage gaps

No unit tests exist for `MatchService`, `StoreService`, `AdminService`, `PaymentService`/`RazorpayProvider`, `LeaderboardService`, `NotificationService`, `GameSocketController`, controllers (no MockMvc slice tests), or any bot logic. No Playwright/E2E suite. Admin pages and the mobile layout have no automated tests.

## 11. Priority order (see IMPLEMENTATION_ROADMAP.md)

1. S1 (credentials), W1 (purchase bug), L1 (lost updates) - small, safe, high value.
2. Run the skipped integration tests against a real Mongo (owner's Mac) and confirm startup.
3. Bot mode (largest missing feature), then OTP reset, announcements, economy-abuse guards, frontend polish, deployment files.
