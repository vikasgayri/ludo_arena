import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { STEP_MS, planFrames, tokenMap, useAnimatedPlayers } from '../lib/animation';
import Dice from '../components/Dice';

describe('planFrames (presentation only)', () => {
  it('walks a single token one cell per frame', () => {
    const frames = planFrames({ 'RED-0': 5, 'RED-1': -1 }, { 'RED-0': 9, 'RED-1': -1 });
    expect(frames).toEqual([{ 'RED-0': 6 }, { 'RED-0': 7 }, { 'RED-0': 8 }, { 'RED-0': 9 }]);
  });

  it('leaves base in one hop', () => {
    expect(planFrames({ 'RED-0': -1 }, { 'RED-0': 0 })).toEqual([{ 'RED-0': 0 }]);
  });

  it('sends a captured token home only after the mover has arrived', () => {
    const frames = planFrames({ 'RED-0': 10, 'YELLOW-2': 40 }, { 'RED-0': 12, 'YELLOW-2': -1 });
    expect(frames).toEqual([{ 'RED-0': 11 }, { 'RED-0': 12 }, { 'YELLOW-2': -1 }]);
  });

  it('snaps big jumps (e.g. after a reconnect) in one frame', () => {
    expect(planFrames({ 'RED-0': 3, 'RED-1': 0 }, { 'RED-0': 30, 'RED-1': 4 })).toEqual([{ 'RED-0': 30, 'RED-1': 4 }]);
  });

  it('does nothing when nothing changed', () => {
    expect(planFrames({ 'RED-0': 3 }, { 'RED-0': 3 })).toEqual([]);
  });

  it('tokenMap keys tokens by colour and index', () => {
    expect(tokenMap([{ color: 'BLUE', tokens: [-1, 4, 56, 0] }])).toEqual({ 'BLUE-0': -1, 'BLUE-1': 4, 'BLUE-2': 56, 'BLUE-3': 0 });
  });
});

function Probe({ players, enabled = true }) {
  const { players: shown, animating } = useAnimatedPlayers(players, { enabled });
  return <div data-testid="probe" data-animating={String(animating)}>{shown[0].tokens.join(',')}</div>;
}

describe('useAnimatedPlayers', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('steps through intermediate cells and ends on the server position', () => {
    const p = (t0) => [{ userId: 'a', color: 'RED', tokens: [t0, -1, -1, -1] }];
    const { rerender } = render(<Probe players={p(5)} />);
    expect(screen.getByTestId('probe')).toHaveTextContent('5,-1,-1,-1');

    rerender(<Probe players={p(8)} />);
    expect(screen.getByTestId('probe')).toHaveTextContent('6,-1,-1,-1');
    expect(screen.getByTestId('probe')).toHaveAttribute('data-animating', 'true');
    act(() => { vi.advanceTimersByTime(STEP_MS); });
    expect(screen.getByTestId('probe')).toHaveTextContent('7,-1,-1,-1');
    act(() => { vi.advanceTimersByTime(STEP_MS * 3); });
    expect(screen.getByTestId('probe')).toHaveTextContent('8,-1,-1,-1');
    expect(screen.getByTestId('probe')).toHaveAttribute('data-animating', 'false');
  });

  it('applies positions immediately when animations are disabled', () => {
    const p = (t0) => [{ userId: 'a', color: 'RED', tokens: [t0, -1, -1, -1] }];
    const { rerender } = render(<Probe players={p(5)} enabled={false} />);
    rerender(<Probe players={p(9)} enabled={false} />);
    expect(screen.getByTestId('probe')).toHaveTextContent('9,-1,-1,-1');
    expect(screen.getByTestId('probe')).toHaveAttribute('data-animating', 'false');
  });
});

describe('Dice rolling state', () => {
  it('marks itself rolling and keeps reporting the server value', () => {
    vi.useFakeTimers();
    const { rerender } = render(<Dice value={3} rolling />);
    expect(screen.getByTestId('dice')).toHaveAttribute('data-rolling', 'true');
    expect(screen.getByTestId('dice')).toHaveAttribute('data-value', '3');
    act(() => { vi.advanceTimersByTime(300); });
    rerender(<Dice value={3} rolling={false} />);
    expect(screen.getByTestId('dice')).toHaveAttribute('data-rolling', 'false');
    expect(screen.getByTestId('dice')).toHaveAttribute('aria-label', 'Dice showing 3');
    vi.useRealTimers();
  });
});
