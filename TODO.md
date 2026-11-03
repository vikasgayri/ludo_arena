# TODO / Status

Last updated: 2026-11-01. This list is deliberately honest about what has and has not been verified.

## Verified (executed)

- [x] Game engine: 59 unit tests pass (compiled with `javac` against local stubs for JUnit/Spring annotations).
- [x] Frontend: `npm run build` succeeds; `npm test` runs 66 Vitest tests, all passing.
- [x] Frontend smoke test in a real Chromium against a throwaway mock API: landing, dashboard, store, admin dashboard render with no console errors and no horizontal scroll at 390 px.
- [x] Backend Java sources parse with `javac` (no syntax errors).

## Not yet verified: do these first

- [ ] **`cd backend && mvn test`**: the Spring layer (security, REST, Mongo, STOMP, services) has never been compiled or run. Expect to fix a handful of compile/wiring errors on the first build.
- [ ] `TEST_MONGODB_URI=… mvn test`: runs `ApiIntegrationTest` (register → login → rooms → wallet → admin guard) and `GameFlowWebSocketTest` (two STOMP clients play to a finished match) against a real Mongo.
- [ ] End-to-end manual run of backend + frontend together (the frontend has only been exercised against mocks and unit tests; no live STOMP session through the UI).
- [ ] Mongo query paths worth a close look when first running: positional updates on `players.$`, `players.$[]`, and the `appliedRefs` `$push/$slice` wallet update.

## Known gaps / not implemented

- [ ] Payments: Razorpay adapter and webhook flow are written but untested against a live gateway; keep `PAYMENTS_ENABLED=false` until reviewed.
- [ ] Horizontal scaling (broker relay, distributed match lock, shared rate limiter), see README "Scaling notes".
- [ ] Sound effects: the `sound` preference is stored but no audio assets are bundled.
- [ ] Token movement animation is positional (tokens jump to the new cell); no tweened path animation yet.
- [ ] Public player profile page (`GET /users/{id}/profile` exists; no UI route).
- [ ] Frontend component tests for the admin pages (they build and render in smoke tests but have no Vitest coverage).
- [ ] Email delivery has not been tested against a real SMTP server.
- [ ] Load testing of the WebSocket path.
