# Ludo Arena - Testing Report

Last updated: 2026-11-05. A test is listed as **passed** only if it was executed; the "Executed by / when" column says who ran it.

## 1. Environment limits
* Cloud sandbox and the owner's connected device shell both get `403` from Maven Central; the device shell also has no Maven and only Java 11. Therefore **Maven builds and Spring tests can only be run on the owner's Mac** (Java 21, local `~/.m2`).
* `npm` works in the sandbox. No MongoDB instance is available to the audit.

## 2. Backend (Maven Surefire, owner's Mac, 2026-11-01 10:52)

| Class | Run | Fail | Err | Skip |
|-------|----:|-----:|----:|-----:|
| game.DiceServiceTest | 3 | 0 | 0 | 0 |
| game.LudoEngineServiceTest | 35 | 0 | 0 | 0 |
| game.LudoRulesServiceTest | 10 | 0 | 0 | 0 |
| game.TokenMovementServiceTest | 8 | 0 | 0 | 0 |
| game.TurnManagerServiceTest | 3 | 0 | 0 | 0 |
| security.JwtServiceTest | 5 | 0 | 0 | 0 |
| security.RateLimitFilterTest | 5 | 0 | 0 | 0 |
| security.StompAuthInterceptorTest | 10 | 0 | 0 | 0 |
| service.AuthServiceTest | 12 | 0 | 0 | 0 |
| service.RoomServiceTest | 11 | 0 | 0 | 0 |
| service.WalletServiceTest | 7 | 0 | 0 | 0 |
| integration.ApiIntegrationTest | 13 | 0 | 0 | **13** |
| integration.GameFlowWebSocketTest | 3 | 0 | 0 | **3** |
| **Total** | **125** | **0** | **0** | **16** |

* 109 executed and green; 16 skipped because `TEST_MONGODB_URI` is not set (never run).
* Earlier run had 4 errors (3 in `RateLimitFilterTest`: bare `ObjectMapper` could not serialise `Instant`; 1 in `StompAuthInterceptorTest`: stray `anyString()` matcher). Both were test-code defects; fixed in test code only. Production code was not changed to make tests pass.
* After the later CORS `@Qualifier` change in `SecurityConfig`, `mvn clean test` was not re-run by the audit; the Surefire figures above therefore pre-date that change. **Re-run needed.**
* `mvn clean package` produced `target/ludo-arena-backend-1.0.0.jar` (owner's Mac).

### 2b. Re-run after Phase 1 changes (owner's Mac, 2026-11-05 12:14 IST)
`mvn clean test`: **Tests run: 128, Failures: 0, Errors: 0, Skipped: 16 - BUILD SUCCESS** (93 main + 15 test source files compiled on Java 21). `WalletServiceTest` now 10 tests (3 new: retry of failed debit, still-too-poor retry, failed credit never reopened). The 16 skipped are still the Mongo-gated integration tests.
The L1 change (targeted updates in UserService/StoreService/AuthService/AdminService) was written after this run and is **not yet compiled by anyone**.

## 3. Backend - application startup
**Verified on the owner's Mac, 2026-11-05 12:37 IST** (`java -jar target/ludo-arena-backend-1.0.0.jar`, MongoDB Atlas M0 cluster, region AP_SOUTH_1):
replica set primary discovered, indexes created, `Tomcat started on port 8080`, STOMP simple broker started,
`Started LudoArenaApplication in 6.317 seconds`. This confirms the CORS `@Qualifier` fix at runtime.
* Local-only note: the `mongodb+srv://` string failed on the Mac because Java's JNDI DNS client timed out on the TXT/SRV
  lookup (`dig` resolved fine). Locally the equivalent non-SRV seed-list string is used; on Render the SRV string is fine.
* Startup log showed `Using generated security password` (Spring Boot's default in-memory user). It was unused (JWT-only
  auth) but has been removed by excluding `UserDetailsServiceAutoConfiguration` (awaiting re-run).
* First `/actuator/health` returned `DOWN`: the mail health indicator probed the unused default SMTP host `localhost:587`.
  Fixed with `management.health.mail.enabled: ${MAIL_ENABLED:false}`.
* **Re-run after both fixes (owner's Mac, 2026-11-05 ~12:45 IST):** `GET /actuator/health` -> `{"status":"UP"}`;
  `POST /api/v1/auth/register` -> `success:true` with access + refresh token (user persisted in Atlas `ludo_arena`);
  `GET /api/v1/users/me` without token -> `401`. Health sub-paths (`/actuator/health/mongo`) correctly return 401 (not public).
* Not yet verified: integration tests against a separate test database; login/refresh/admin flows over HTTP; WebSocket play.

## 4. Frontend (executed in sandbox, 2026-11-05)
* `npm ci`: 249 packages installed.
* `npx vitest run`: **9 files, 66 tests, all passed** (api, auth, board, events, gameStore, GameRoom, LudoBoard, pages, ws). Console shows React Router future-flag warnings only.
* `npm run build`: succeeded, 1725 modules; `index.css` 31.9 kB, `index.js` 398.8 kB (121.4 kB gzip).
* No test covers admin pages, store purchase UI, or a live backend.
* **Re-run 2026-11-05 after Phase 5 frontend:** 10 files, **74 tests, all passed** (+6 `botMode.test.jsx`: mode page,
  bot match start/navigation, error path, bot badge, thinking state, abandoned text; +2 `GameRoom.test.jsx`: bot turn
  display, abandoned practice result). `npm run build` OK (JS 405.5 kB / 122.9 kB gzip).
* **Re-run 2026-11-05 after smoothness/theme/deploy work:** 12 files, **86 tests, all passed** (+9 `animation.test.jsx`:
  step planning, capture-after-arrival, snap on reconnect, hook stepping with fake timers, reduced-motion snap, dice
  rolling state; +3 `serverStatus.test.jsx`: sleeping-backend banner). The hook test caught a real bug (frame index read
  lazily inside a state updater skipped the first step and dropped the last one), fixed before shipping. Build OK
  (JS 415.9 kB / 126.8 kB gzip).
* **Visual check (Playwright/Chromium, mocked game state):** desktop 1366x860, iPhone 13 (390 px) and iPhone SE (320 px):
  no console errors, no horizontal scroll (scrollWidth = viewport), turn label wraps on small phones. Sampling the token's
  computed transform during a 4-cell move showed intermediate positions (smooth walk), ending exactly on the target cell.
  Not tested on a physical phone or against the deployed Vercel/Render stack.

* **Re-run 2026-11-06 after 3-player / offline / instant-response work:** 16 files, **127 tests, all passed**
  (+12 `localEngine.test.js`: offline rules port - seats/colours for 2/3/4, six to leave base, exact finish, capture +
  safe stars, triple six, win + ranking, purity, forfeit, save validation; +4 `localGame.test.jsx`: 3-player setup,
  serverless roll, resume, corrupted save ignored; +3 `roomLobby.test.jsx`: host "Start now", non-host/alone hidden;
  +3 `GameRoom.test.jsx`: turn-player token highlight, optimistic token move before the server answers, rejected move
  snaps back; +1 `animation.test.jsx`: dice shuffle time counted from the tap). Build OK (JS 452.7 kB / 137.3 kB gzip).
  Playwright (iPhone 13, production build): offline 3-player game played ~40 actions with no page errors; active
  player's four tokens ringed.

## 5. Not covered by any test today
`MatchService` (finalization, idempotency, timeouts, reconnect), `StoreService`, `AdminService`, `PaymentService`/`RazorpayProvider`, `LeaderboardService`, `NotificationService`, `GameSocketController`, REST controllers (no MockMvc slices without Mongo), bots (do not exist), OTP reset (does not exist), announcements (do not exist), E2E and mobile browser flows, load/WebSocket stress.

## 6. Commands for the owner (Mac terminal)

```bash
# 1. unit tests + package
cd ~/ludo-arena/backend && mvn clean test && mvn clean package

# 2. integration tests (needs MongoDB running locally)
TEST_MONGODB_URI=mongodb://localhost:27017/ludo_arena_test mvn test

# 3. startup check
export JWT_SECRET="$(openssl rand -base64 48)"
export MONGODB_URI=mongodb://localhost:27017/ludo_arena
java -jar target/ludo-arena-backend-1.0.0.jar
curl -s localhost:8080/actuator/health
```

Paste the tail of each output; results are recorded here.

## 7. Change log
| Date | Change |
|------|--------|
| 2026-11-01 | Surefire run: 125 tests, 4 errors (fixed in tests) -> 0 errors |
| 2026-11-05 | Audit; frontend tests/build re-executed (66/66, build OK) |
| 2026-11-05 | Phase 1a: hardcoded admin defaults removed, W1 fixed; `mvn clean test` 128/0/0/16 BUILD SUCCESS |
| 2026-11-05 | Phase 1b: L1 fix; `mvn clean test` 128 -> 133 (M1 finalization tests) BUILD SUCCESS |
| 2026-11-05 | First successful startup against MongoDB Atlas; bot engine 12 tests pass in javac harness (71/71 engine+bot) |
| 2026-11-05 | Health DOWN (mail indicator) fixed; default in-memory user removed; health UP, register OK, 401 OK |
| 2026-11-05 | Phase 5 backend (bot matches): `mvn clean test` **153** run, 0 fail, 0 err, 16 skipped, BUILD SUCCESS |
| 2026-11-05 | Phase 5 frontend (mode page, bot UI): Vitest 74/74, build OK |
| 2026-11-05 | Owner played a bot match locally: works; asked for smoother play + nicer theme + mobile + Vercel/Render readiness |
| 2026-11-05 | Owner deployed on Vercel + Render; CORS origin fixed via FRONTEND_URL; online 2P join works |
| 2026-11-05 | Tap focus-ring blob removed, auto-move for a single choice, in-match chat (backend + UI); Vitest 94/94; backend awaiting `mvn clean test` |
| 2026-11-05 | Voice chat (WebRTC): Vitest 104/104 (+10 VoiceClient unit tests); **real two-browser test** (Chromium, fake mics, simultaneous join): both peers `connected` in ~0.6 s, audio packets flowing both ways, mute disables the track, leave drops the peer, re-join reconnects. Not yet tested on real phones / mobile data; backend VoiceService (8 tests) awaiting `mvn clean test` |
| 2026-11-05 | Smooth token walking, dice shuffle/land, sounds + mute, classic board, mobile layout, vercel.json, origin patterns, Dockerfile; Vitest 86/86; backend change awaiting `mvn clean test` |
| 2026-11-06 | 3-player rooms + host "Start now", offline pass & play (`/local`), turn-player token highlight, latency work (in-memory running matches, broadcast before DB write, ordered write-behind, instant dice + optimistic move). Vitest 127/127, engine harness 74/74; Spring tests (MatchServiceLatencyTest, RoomServiceTest, MatchServiceBotTest) **not executed yet** - Maven Central is blocked in the sandbox, awaiting owner's `mvn clean test` |
