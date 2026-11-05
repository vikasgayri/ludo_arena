import { useEffect, useState } from 'react';
import { AlertTriangle, Loader2 } from 'lucide-react';
import { API_MISCONFIGURED, API_ORIGIN } from '../lib/api';

const SLOW_AFTER_MS = 2500;
const RETRY_MS = 4000;
const GIVE_UP_MS = 120000;
const BLOCKED_AFTER = 4; // consecutive "server answered but CORS failed" probes (~12 s)

/**
 * Pings the backend health endpoint once when the app opens. Free hosting (e.g. Render free) puts the server to sleep
 * when idle; the first request can take ~30-60 s. Instead of looking broken, the app says the server is waking up.
 * Also warns loudly when a production build has no backend URL configured.
 */
export default function ServerStatus({ fetchImpl } = {}) {
  const [state, setState] = useState('checking'); // checking | slow | ok | down | blocked

  useEffect(() => {
    if (API_MISCONFIGURED) return undefined;
    const doFetch = fetchImpl || ((url, opts) => fetch(url, opts));
    let cancelled = false;
    let retry;
    let opaqueInARow = 0;
    const started = Date.now();
    const slow = setTimeout(() => { if (!cancelled) setState((s) => (s === 'checking' ? 'slow' : s)); }, SLOW_AFTER_MS);
    const ping = async () => {
      try {
        const res = await doFetch(`${API_ORIGIN}/actuator/health`, { cache: 'no-store' });
        if (res.ok) {
          if (!cancelled) setState('ok');
          return;
        }
      } catch { /* not up yet */ }
      if (cancelled) return;
      // The normal (CORS) request failed. A no-cors probe tells "server is up but refuses this site's origin"
      // apart from "server unreachable": an opaque response means the server answered.
      // A waking host's proxy can also answer opaquely, so only report "blocked" when it persists; keep retrying
      // either way, a later successful CORS ping clears the banner.
      let answered = false;
      try {
        const probe = await doFetch(`${API_ORIGIN}/actuator/health`, { mode: 'no-cors', cache: 'no-store' });
        answered = !!probe && probe.type === 'opaque';
      } catch { /* really unreachable */ }
      if (cancelled) return;
      opaqueInARow = answered ? opaqueInARow + 1 : 0;
      if (opaqueInARow >= BLOCKED_AFTER) {
        setState('blocked');
      } else if (Date.now() - started > GIVE_UP_MS) {
        setState('down');
        return;
      } else {
        setState((s) => (s === 'checking' ? 'slow' : s));
      }
      retry = setTimeout(ping, RETRY_MS);
    };
    ping();
    return () => { cancelled = true; clearTimeout(slow); clearTimeout(retry); };
  }, [fetchImpl]);

  if (API_MISCONFIGURED) {
    return (
      <div role="alert" className="fixed inset-x-0 top-0 z-[70] flex items-center justify-center gap-2 bg-red-600 px-4 py-2 text-center text-sm font-medium text-white">
        <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden />
        Backend URL is not configured for this build (set VITE_API_BASE_URL and VITE_WS_URL, then redeploy).
      </div>
    );
  }
  if (state === 'blocked') {
    return (
      <div role="alert" data-testid="server-blocked" className="fixed inset-x-0 top-0 z-[70] flex items-center justify-center gap-2 bg-red-600 px-4 py-2 text-center text-sm font-medium text-white">
        <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden />
        The game server is running but does not allow this website address ({typeof window !== 'undefined' ? window.location.origin : ''}). Add it to FRONTEND_URL on the server, or open the main site address.
      </div>
    );
  }
  if (state === 'slow') {
    return (
      <div role="status" data-testid="server-waking" className="fixed inset-x-0 top-0 z-[70] flex items-center justify-center gap-2 bg-amber-500 px-4 py-2 text-center text-sm font-medium text-navy-950">
        <Loader2 className="h-4 w-4 shrink-0 animate-spin" aria-hidden />
        Game server is waking up… this can take up to a minute on the first visit.
      </div>
    );
  }
  if (state === 'down') {
    return (
      <div role="alert" data-testid="server-down" className="fixed inset-x-0 top-0 z-[70] flex items-center justify-center gap-2 bg-red-600 px-4 py-2 text-center text-sm font-medium text-white">
        <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden />
        Can&apos;t reach the game server right now. Please try again in a moment.
      </div>
    );
  }
  return null;
}
