import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LudoBoard from '../components/LudoBoard';
import Dice from '../components/Dice';

const players = [
  { userId: 'a', color: 'RED', tokens: [-1, -1, 5, 56] },
  { userId: 'b', color: 'YELLOW', tokens: [-1, 10, -1, -1] },
];

describe('LudoBoard', () => {
  it('renders every token of every player', () => {
    render(<LudoBoard players={players} />);
    expect(screen.getByTestId('ludo-board')).toBeInTheDocument();
    expect(screen.getAllByTestId(/^token-/)).toHaveLength(8);
  });

  it('only marks the server-provided valid moves of the acting user as movable', () => {
    render(<LudoBoard players={players} movableTokens={[2, 3]} movableUserId="a" />);
    expect(screen.getByTestId('token-RED-2')).toHaveAttribute('data-movable', 'true');
    expect(screen.getByTestId('token-RED-3')).toHaveAttribute('data-movable', 'true');
    expect(screen.getByTestId('token-RED-0')).toHaveAttribute('data-movable', 'false');
    // the opponent's token with the same index is never movable
    expect(screen.getByTestId('token-YELLOW-3')).toHaveAttribute('data-movable', 'false');
  });

  it('calls onTokenSelect with the token index when a movable token is clicked, and ignores others', async () => {
    const onSelect = vi.fn();
    render(<LudoBoard players={players} movableTokens={[2]} movableUserId="a" onTokenSelect={onSelect} />);
    await userEvent.click(screen.getByTestId('token-RED-2'));
    expect(onSelect).toHaveBeenCalledWith(2);
    await userEvent.click(screen.getByTestId('token-RED-0'));
    expect(onSelect).toHaveBeenCalledTimes(1);
  });

  it('supports keyboard activation of a movable token', async () => {
    const onSelect = vi.fn();
    render(<LudoBoard players={players} movableTokens={[2]} movableUserId="a" onTokenSelect={onSelect} />);
    screen.getByTestId('token-RED-2').focus();
    await userEvent.keyboard('{Enter}');
    expect(onSelect).toHaveBeenCalledWith(2);
  });

  it('renders nothing movable when no user is acting', () => {
    render(<LudoBoard players={players} movableTokens={[2]} movableUserId={null} />);
    expect(screen.getAllByTestId(/^token-/).every((t) => t.getAttribute('data-movable') === 'false')).toBe(true);
  });
});

describe('Dice', () => {
  it('shows the server value and is clickable only when an onClick is given', async () => {
    const onClick = vi.fn();
    const { rerender } = render(<Dice value={4} />);
    expect(screen.getByTestId('dice')).toHaveAttribute('data-value', '4');
    expect(screen.getByTestId('dice').tagName).toBe('DIV');
    rerender(<Dice value={4} onClick={onClick} />);
    await userEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledTimes(1);
    rerender(<Dice value={4} onClick={onClick} disabled />);
    expect(screen.getByRole('button')).toBeDisabled();
  });
});
