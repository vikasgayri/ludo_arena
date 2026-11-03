// Token persistence. Wrapped in try/catch because storage can be unavailable (private mode, blocked cookies).
const ACCESS = 'la_access';
const REFRESH = 'la_refresh';

function read(key) {
  try { return localStorage.getItem(key); } catch { return null; }
}
function write(key, value) {
  try {
    if (value == null) localStorage.removeItem(key); else localStorage.setItem(key, value);
  } catch { /* ignore */ }
}

let memAccess = null;
let memRefresh = null;
let authLostHandler = null;

export const tokens = {
  getAccess: () => memAccess ?? read(ACCESS),
  getRefresh: () => memRefresh ?? read(REFRESH),
  set(access, refresh) {
    memAccess = access; memRefresh = refresh;
    write(ACCESS, access); write(REFRESH, refresh);
  },
  clear() {
    memAccess = null; memRefresh = null;
    write(ACCESS, null); write(REFRESH, null);
  },
  onAuthLost(fn) { authLostHandler = fn; },
  notifyAuthLost() { if (authLostHandler) authLostHandler(); },
};
