import { describe, expect, it } from 'vitest';
import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderAt } from './helpers';
import LocalGame from '../pages/LocalGame';
import { newGame } from '../lib/localEngine';

const SAVE_KEY = 'ludo-arena:local-game';

describe('Offline pass & play', () => {
  it('starts a 3-player game on one device with the chosen names', async () => {
    renderAt(<LocalGame />, { route: '/local', path: '/local' });
    await userEvent.click(screen.getByText('3').closest('label'));
    await userEvent.type(screen.getByTestId('local-name-0'), 'Aman');
    await userEvent.type(screen.getByTestId('local-name-1'), 'Bina');
    await userEvent.type(screen.getByTestId('local-name-2'), 'Chetan');
    expect(screen.queryByTestId('local-name-3')).not.toBeInTheDocument();
    await userEvent.click(screen.getByTestId('local-start'));

    expect(screen.getByTestId('ludo-board')).toBeInTheDocument();
    expect(screen.getByTestId('local-turn-label').textContent).toMatch(/^(Aman|Bina|Chetan)'s turn$/);
    // three colours on the board, BLUE is not playing
    expect(screen.getByTestId('token-GREEN-0')).toBeInTheDocument();
    expect(screen.queryByTestId('token-BLUE-0')).not.toBeInTheDocument();
    // the player to move has their four tokens highlighted
    const current = screen.getByTestId('local-turn-label').textContent.replace("'s turn", '');
    const color = { Aman: 'RED', Bina: 'GREEN', Chetan: 'YELLOW' }[current];
    expect(screen.getByTestId(`token-${color}-0`)).toHaveAttribute('data-active', 'true');
  });

  it('rolling works without any server and is written to the log', async () => {
    renderAt(<LocalGame />, { route: '/local', path: '/local' });
    await userEvent.click(screen.getByTestId('local-start'));
    await userEvent.click(screen.getByRole('button', { name: /roll dice for/i }));
    expect(screen.getAllByText(/rolled a [1-6]/).length).toBeGreaterThan(0);
  });

  it('offers to resume a game saved on this device', async () => {
    const saved = newGame(['Ravi', 'Sita'], { random: () => 0 }).state;
    saved.players[0].tokens = [5, -1, -1, -1];
    localStorage.setItem(SAVE_KEY, JSON.stringify(saved));
    renderAt(<LocalGame />, { route: '/local', path: '/local' });
    const card = screen.getByTestId('resume-card');
    expect(within(card).getByText('Ravi · Sita')).toBeInTheDocument();
    await act(async () => { await userEvent.click(within(card).getByRole('button', { name: /resume/i })); });
    expect(screen.getByTestId('local-turn-label')).toHaveTextContent("Ravi's turn");
  });

  it('ignores a corrupted save', () => {
    localStorage.setItem(SAVE_KEY, '{"local":true,"players":"nope"}');
    renderAt(<LocalGame />, { route: '/local', path: '/local' });
    expect(screen.queryByTestId('resume-card')).not.toBeInTheDocument();
    expect(screen.getByTestId('local-start')).toBeInTheDocument();
  });

  it('the dice docks at the home of the player to move, and the banner names them', async () => {
    const saved = newGame(['Ravi', 'Sita', 'Gita', 'Mohan'], { random: () => 0.6 }).state; // seat 2 (YELLOW) starts
    localStorage.setItem(SAVE_KEY, JSON.stringify(saved));
    renderAt(<LocalGame />, { route: '/local', path: '/local' });
    await userEvent.click(within(screen.getByTestId('resume-card')).getByRole('button', { name: /resume/i }));
    expect(screen.getByTestId('stage-dice')).toHaveAttribute('data-dock', 'YELLOW');
    expect(screen.getByTestId('pod-YELLOW')).toHaveAttribute('data-active', 'true');
    expect(screen.getByTestId('pod-status-YELLOW')).toHaveTextContent('YOUR TURN');
    expect(screen.getByTestId('turn-banner')).toHaveTextContent("Gita's Turn");
    expect(screen.getByTestId('roll-prompt')).toBeInTheDocument();
  });

  it('opening the menu pauses the game (no rolling) and Resume continues', async () => {
    renderAt(<LocalGame />, { route: '/local', path: '/local' });
    await userEvent.click(screen.getByTestId('local-start'));
    await userEvent.click(screen.getByTestId('menu-button'));
    expect(screen.queryByRole('button', { name: /roll dice for/i })).not.toBeInTheDocument();
    expect(screen.queryByTestId('roll-prompt')).not.toBeInTheDocument();
    await userEvent.click(screen.getByTestId('menu-resume'));
    expect(await screen.findByRole('button', { name: /roll dice for/i })).toBeInTheDocument();
  });

  it('Leave game asks first, then ends the game completely (no saved copy left)', async () => {
    renderAt(<LocalGame />, { route: '/local', path: '/local' });
    await userEvent.click(screen.getByTestId('local-start'));
    expect(localStorage.getItem(SAVE_KEY)).not.toBeNull();
    await userEvent.click(screen.getByTestId('menu-button'));
    await userEvent.click(screen.getByTestId('menu-leave'));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByTestId('ludo-board')).toBeInTheDocument();
    await userEvent.click(screen.getByTestId('menu-button'));
    await userEvent.click(screen.getByTestId('menu-leave'));
    await userEvent.click(screen.getByRole('button', { name: 'Leave game' }));
    expect(screen.queryByTestId('ludo-board')).not.toBeInTheDocument();
    expect(screen.getByTestId('local-start')).toBeInTheDocument();
    expect(screen.queryByTestId('resume-card')).not.toBeInTheDocument();
    expect(localStorage.getItem(SAVE_KEY)).toBeNull();
  });

  it('volume changes in the menu are saved', async () => {
    const { fireEvent } = await import('@testing-library/react');
    renderAt(<LocalGame />, { route: '/local', path: '/local' });
    await userEvent.click(screen.getByTestId('local-start'));
    await userEvent.click(screen.getByTestId('menu-button'));
    await userEvent.click(screen.getByTestId('menu-sound'));
    fireEvent.change(screen.getByTestId('vol-sfx'), { target: { value: '40' } });
    expect(screen.getByTestId('vol-sfx-value')).toHaveTextContent('40%');
    expect(JSON.parse(localStorage.getItem('la_sound')).sfx).toBeCloseTo(0.4);
    await userEvent.click(screen.getByTestId('mute-all'));
    expect(JSON.parse(localStorage.getItem('la_sound')).muted).toBe(true);
    expect(screen.getByTestId('vol-sfx')).toBeDisabled();
    await userEvent.click(screen.getByTestId('mute-all')); // leave sound on for other tests
  });
});
