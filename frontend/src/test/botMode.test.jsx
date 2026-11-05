import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError, renderAt } from './helpers';

vi.mock('../lib/api', async (orig) => {
  const actual = await orig();
  return { ...actual, RoomApi: { createBotGame: vi.fn() } };
});

import { RoomApi } from '../lib/api';
import PlayMode from '../pages/PlayMode';
import PlayerCard from '../components/PlayerCard';
import { describeEvent } from '../lib/events';

beforeEach(() => vi.clearAllMocks());

describe('PlayMode (game mode selection)', () => {
  it('offers online, private and computer modes', () => {
    renderAt(<PlayMode />, { route: '/play', path: '/play' });
    expect(screen.getByTestId('mode-online')).toHaveAttribute('href', '/lobby');
    expect(screen.getByTestId('mode-private')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /play with computer/i })).toBeInTheDocument();
    // defaults: 1 computer, medium
    expect(screen.getByRole('radio', { name: /computer · 2 players/i })).toBeChecked();
    expect(screen.getByRole('radio', { name: /^Medium/ })).toBeChecked();
  });

  it('starts a bot match with the chosen seats and difficulty and opens the game', async () => {
    RoomApi.createBotGame.mockResolvedValue({ room: { id: 'r1' }, matchId: 'm1' });
    renderAt(<PlayMode />, { route: '/play', path: '/play' });

    await userEvent.click(screen.getByRole('radio', { name: /computers · 4 players/i }));
    await userEvent.click(screen.getByRole('radio', { name: /^Hard/ }));
    await userEvent.click(screen.getByRole('button', { name: /start match/i }));

    expect(RoomApi.createBotGame).toHaveBeenCalledWith({ players: 4, difficulty: 'HARD' });
    expect(await screen.findByTestId('location')).toHaveTextContent('/game/m1');
  });

  it('shows the server error (e.g. already in a room) and stays on the page', async () => {
    RoomApi.createBotGame.mockRejectedValue(apiError(409, 'ALREADY_IN_ROOM', 'Leave your current room before joining another one'));
    renderAt(<PlayMode />, { route: '/play', path: '/play' });

    await userEvent.click(screen.getByRole('button', { name: /start match/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/leave your current room/i);
    expect(screen.getByRole('button', { name: /start match/i })).toBeEnabled();
  });
});

describe('bot presentation', () => {
  it('PlayerCard marks computer players and shows the thinking state', () => {
    render(<PlayerCard name="Master Bot" avatar="avatar-10" color="GREEN" bot botDifficulty="HARD" thinking finishedTokens={0} />);
    expect(screen.getByTestId('bot-badge')).toHaveTextContent('Computer · Hard');
    expect(screen.getByTestId('bot-thinking')).toBeInTheDocument();
  });

  it('PlayerCard for a human has no bot badge', () => {
    render(<PlayerCard name="Asha" avatar="avatar-01" color="RED" finishedTokens={1} />);
    expect(screen.queryByTestId('bot-badge')).toBeNull();
    expect(screen.getByText('1/4 home')).toBeInTheDocument();
  });

  it('describes an abandoned match', () => {
    expect(describeEvent({ type: 'MATCH_ABANDONED', data: {} }, () => 'Someone')).toMatch(/abandoned/i);
  });
});
