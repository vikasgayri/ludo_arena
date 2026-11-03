# REST API

Base path: `/api/v1`. JSON only. Authenticated routes need `Authorization: Bearer <accessToken>`.

## Envelope

```json
{ "success": true,  "data": { }, "message": null, "timestamp": "2026-11-01T10:00:00Z" }
{ "success": false, "error": { "code": "VALIDATION_FAILED", "message": "…", "fieldErrors": { "email": "…" } }, "timestamp": "…" }
```

Null fields are omitted. Paged responses use `data: { items, page, size, totalItems, totalPages }` (`page` is 0-based, `size` is capped at 100).

Common error codes: `UNAUTHORIZED` (401), `FORBIDDEN` / `ACCOUNT_SUSPENDED` (403), `VALIDATION_FAILED` / `BAD_REQUEST` (400), `*_NOT_FOUND` (404), `DUPLICATE` / `ROOM_FULL` / `ALREADY_IN_ROOM` / `INSUFFICIENT_FUNDS` (409), `RATE_LIMITED` (429, with `Retry-After`), `PAYMENTS_DISABLED` (403).

## Auth model

Access token: HS256 JWT, 15 min. Refresh token: opaque, 14 days, stored hashed, **rotated on every use**; presenting an already-used refresh token revokes the whole token family. The user (and therefore role and suspension) is re-loaded from the database on every request.

## Endpoints

### Auth: `/auth`
| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | `{name,email,password}` → `AuthResponse`. Password: 8–72 chars, a letter and a digit. Grants the signup bonus. |
| POST | `/auth/login` | `{email,password}` → `AuthResponse`. |
| POST | `/auth/refresh` | `{refreshToken}` → new `AuthResponse`. |
| POST | `/auth/logout` | `{refreshToken}` revokes it. |
| GET | `/auth/me` | Current user. |
| POST | `/auth/forgot-password` | `{email}`. Always the same response (no account enumeration). |
| POST | `/auth/reset-password` | `{token,newPassword}`. |

### Users: `/users`
`GET /users/me`, `PUT /users/me` (`name`, `avatar`, `sound`, `animations`), `POST /users/me/password`, `GET /users/me/achievements`, `GET /users/{id}/profile` (public fields only).

### Wallet: `/wallet`
`GET /wallet`, `GET /wallet/transactions?type&page&size`, `GET /wallet/purchases`, `POST /wallet/daily-bonus`.

### Store: `/store`
`GET /store/items`, `GET /store/inventory`, `POST /store/purchase {itemId}`, `POST /store/equip {itemId}`, `POST /store/unequip {category}`.

### Rooms: `/rooms`
`POST /rooms {name,maxPlayers(2|4),isPrivate}`, `GET /rooms?maxPlayers&page&size` (public waiting rooms), `GET /rooms/active`, `GET /rooms/{id}`, `POST /rooms/{idOrCode}/join`, `POST /rooms/{id}/leave`, `POST /rooms/{id}/ready {ready}`. A user can be in one room at a time. The match starts automatically when the table is full and everyone is ready.

### Matches: `/matches`
`GET /matches?status&page&size` (own), `GET /matches/{id}`, `GET /matches/{id}/events` (participants only).

### Other
`GET /leaderboard`, `GET /leaderboard/me`; `GET /notifications?unreadOnly`, `GET /notifications/unread-count`, `PUT /notifications/{id}/read`, `PUT /notifications/read-all`; `POST /reports`, `GET /reports/mine`; `GET /payments/status`, `POST /payments/orders`, `POST /payments/verify`, `POST /payments/webhook` (all mutations 403 unless payments are enabled).

### Admin: `/admin` (ADMIN or SUPER_ADMIN)
`GET /admin/dashboard`, `GET /admin/stats`, `GET /admin/users?q&status&role`, `PUT /admin/users/{id}/status`, `GET /admin/matches`, `GET /admin/matches/{id}[/events]`, `GET /admin/transactions?userId&type&status`, `GET /admin/logs`, `GET|POST /admin/store/items`, `PUT /admin/store/items/{id}`, `GET /admin/reports`, `PUT /admin/reports/{id}`, `GET /admin/settings`.

SUPER_ADMIN only: `PUT /admin/users/{id}/role`, `POST /admin/users/{id}/credit`, `PUT /admin/settings`. Every admin mutation is written to `adminLogs`.
