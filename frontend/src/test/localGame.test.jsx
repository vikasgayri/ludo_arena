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
});
