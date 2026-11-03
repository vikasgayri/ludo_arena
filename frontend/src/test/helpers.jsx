import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

/** Render `ui` at `route`, with a catch-all that prints the location so navigation can be asserted. */
export function renderAt(ui, { route = '/', path = '*' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path={path} element={ui} />
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

import { useLocation } from 'react-router-dom';
function LocationProbe() {
  const l = useLocation();
  return <div data-testid="location">{l.pathname}</div>;
}

export const apiError = (status, code, message, fieldErrors) => Object.assign(new Error(message), {
  response: { status, data: { success: false, error: { code, message, fieldErrors } } },
});

export const USER = {
  id: 'u1', name: 'Asha', email: 'asha@example.com', role: 'USER', status: 'ACTIVE', avatar: 'avatar-01',
  stats: { totalMatches: 10, wins: 4, losses: 6, xp: 450, captures: 12, level: 4, winRate: 40 },
  cosmetics: { boardSkin: null, diceSkin: null, profileTheme: null }, preferences: { sound: true, animations: true },
  activeRoomId: null, createdAt: '2026-01-01T00:00:00Z',
};
