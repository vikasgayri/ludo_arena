import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import ServerStatus from '../components/ServerStatus';
import { API_ORIGIN } from '../lib/api';

describe('ServerStatus (sleeping backend notice)', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('shows nothing when the server answers quickly', async () => {
    const fetchImpl = vi.fn().mockResolvedValue({ ok: true });
    render(<ServerStatus fetchImpl={fetchImpl} />);
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
    expect(fetchImpl).toHaveBeenCalledWith(`${API_ORIGIN}/actuator/health`, expect.anything());
    expect(screen.queryByTestId('server-waking')).toBeNull();
  });

  it('explains that the server is waking up, then disappears once it is healthy', async () => {
    const fetchImpl = vi.fn()
      .mockRejectedValueOnce(new Error('sleeping'))
      .mockResolvedValueOnce({ ok: false, status: 503 })
      .mockResolvedValue({ ok: true });
    render(<ServerStatus fetchImpl={fetchImpl} />);
    await act(async () => { await vi.advanceTimersByTimeAsync(2600); });
    expect(screen.getByTestId('server-waking')).toHaveTextContent(/waking up/i);
    await act(async () => { await vi.advanceTimersByTimeAsync(9000); });
    expect(screen.queryByTestId('server-waking')).toBeNull();
    expect(fetchImpl).toHaveBeenCalledTimes(3);
  });

  it('API_ORIGIN strips the /api/v1 suffix', () => {
    expect(API_ORIGIN.endsWith('/api/v1')).toBe(false);
  });
});
