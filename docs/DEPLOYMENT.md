# Ludo Arena - Deployment (Vercel + Render + MongoDB Atlas)

Frontend (React/Vite) on **Vercel**, backend (Spring Boot, REST + WebSocket) on **Render** (Docker), database on
**MongoDB Atlas**. Both services auto-deploy from the GitHub repository when the `main` branch changes.

> The repository still contains an old nested copy in `ludo-arena/`. Both services must build the **root** folders:
> Vercel Root Directory = `frontend`, Render Root Directory = `backend`. If either points at `ludo-arena/...`, new
> changes will never appear.

## 1. MongoDB Atlas

1. **Database Access**: one user for the app (e.g. `vikas`) with *readWrite* on `ludo_arena`. Use a long password with
   letters and digits only (no `@ : / ? # %`), otherwise it must be URL-encoded inside the URI.
2. **Network Access**: Render (free/starter) has no fixed outbound IP, so allow `0.0.0.0/0`. Security then rests on the
   strong password; rotate it if it was ever shared.
3. Connection string for Render (SRV works on Render's Linux DNS):
   `mongodb+srv://vikas:<password>@mycluster.rmhnrps.mongodb.net/ludo_arena?retryWrites=true&w=majority`
   (the `/ludo_arena` part is required, otherwise data goes to a database named `test`).
4. Backups: the free M0 tier has no automated backups; use `mongodump` periodically or upgrade before real launch.
   Indexes are created automatically at startup (`auto-index-creation: true`).

## 2. Render (backend)

Service type **Web Service**, runtime **Docker**, Root Directory `backend`, Dockerfile `backend/Dockerfile`.

| Setting | Value |
|---|---|
| Health Check Path | `/actuator/health` |
| Instance | Free works for testing; it **sleeps after ~15 min idle** (first request then takes ~30-60 s and open games disconnect). For real online play use a paid instance (always on). |

Environment variables:

| Key | Value | Notes |
|---|---|---|
| `MONGODB_URI` | the SRV string above | secret |
| `JWT_SECRET` | output of `openssl rand -base64 48` | secret, >= 32 bytes; changing it logs everyone out |
| `FRONTEND_URL` | `https://<your-app>.vercel.app` | exact origin, **no trailing slash needed** (it is stripped). Several allowed: comma separated. Patterns allowed, e.g. `https://<your-app>.vercel.app,https://*-<team>.vercel.app` for preview deployments. The first entry is used in password-reset links. |
| `TRUST_PROXY` | `true` | Render sits behind a proxy; needed for per-IP rate limiting |
| `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` | optional | creates the first SUPER_ADMIN only if none exists; password >= 10 chars. Remove after first start. |
| `MAIL_ENABLED` + `MAIL_*` | optional | only if password-reset mails should be sent |
| `PAYMENTS_ENABLED` | `false` | keep disabled |

`PORT` is set by Render automatically (Spring uses `server.port=${PORT:8080}`).

Check after deploy: `https://<service>.onrender.com/actuator/health` returns `{"status":"UP"}`.

## 3. Vercel (frontend)

Project settings: Framework **Vite**, Root Directory `frontend`. `frontend/vercel.json` already sets the build
command, output `dist`, the SPA rewrite (so refreshing `/game/...` or opening a shared link does not 404) and basic
security headers.

Environment variables (Production, and Preview if used) - **Vite bakes them in at build time, so redeploy after changing them**:

| Key | Value |
|---|---|
| `VITE_API_BASE_URL` | `https://<service>.onrender.com/api/v1` |
| `VITE_WS_URL` | `https://<service>.onrender.com/ws` (the app converts https -> wss) |

If these are missing, the deployed app shows a red "Backend URL is not configured" bar.

## 4. Deploy

```bash
cd ~/ludo-arena
git add -A backend frontend docs .env.example
git commit -m "Bot mode, smooth animations, classic board, deployment config"
git push origin main
```
Render and Vercel rebuild automatically. (Do not `git add` `_audit_snapshot.tgz`.)

## 5. Smoke test after deploy (phone + laptop)

1. Open the Vercel URL on the phone; if the yellow "server is waking up" bar shows, wait for it to disappear.
2. Register / log in.
3. **Play -> Play with Computer -> 3 computers, Hard -> Start match**: bots roll and move by themselves, tokens walk cell by cell.
4. **Online**: on the laptop create a room (2 players, Private), on the phone join with the code, both press Ready, play a few turns.
5. Turn the phone's Wi-Fi off and on during a match: the "Connection lost - reconnecting" bar appears and the board resyncs.
6. Refresh the browser on `/game/<id>`: the page must load (SPA rewrite) and resume the match.

## 6. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Login fails with a CORS error in the browser console | `FRONTEND_URL` on Render does not match the exact Vercel origin (scheme + host). |
| "Connection lost - reconnecting" never goes away | `VITE_WS_URL` wrong, or Render instance asleep / restarting. |
| 404 when refreshing a page on Vercel | Root Directory not `frontend` (so `vercel.json` is not used). |
| Render deploy fails at startup with `JWT_SECRET must be set` | add `JWT_SECRET`. |
| Startup hangs then `Timed out ... servers=[]` | Atlas Network Access does not allow Render, or wrong URI/password. |
| Local Mac only: `Failed looking up TXT record` | Java DNS issue with `mongodb+srv` on some networks; locally use the non-SRV seed-list URI (see TESTING_REPORT.md). |
