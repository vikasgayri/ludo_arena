import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError, renderAt, USER } from './helpers';

vi.mock('../lib/api', async (orig) => {
  const actual = await orig();
  return {
    ...actual,
    RoomApi: { list: vi.fn(), create: vi.fn(), join: vi.fn(), get: vi.fn(), active: vi.fn(), leave: vi.fn(), ready: vi.fn() },
    WalletApi: { get: vi.fn(), transactions: vi.fn(), purchases: vi.fn(), dailyBonus: vi.fn() },
    MatchApi: { list: vi.fn(), get: vi.fn(), events: vi.fn() },
    LeaderboardApi: { top: vi.fn(), me: vi.fn() },
    StoreApi: { items: vi.fn(), purchase: vi.fn(), equip: vi.fn(), unequip: vi.fn(), inventory: vi.fn() },
    PaymentApi: { status: vi.fn(), createOrder: vi.fn(), verify: vi.fn() },
    UserApi: { achievements: vi.fn(), update: vi.fn(), me: vi.fn() },
  };
});

import { LeaderboardApi, MatchApi, PaymentApi, RoomApi, StoreApi, UserApi, WalletApi } from '../lib/api';
import { useAuthStore } from '../store/authStore';
import { useWalletStore } from '../store/walletStore';
import { useToastStore } from '../store/toastStore';
import Dashboard from '../pages/Dashboard';
import Lobby from '../pages/Lobby';
import CreateRoom from '../pages/CreateRoom';
import JoinRoom from '../pages/JoinRoom';
import Wallet from '../pages/Wallet';
import CoinStore from '../pages/CoinStore';
import Achievements from '../pages/Achievements';
import LoadingSkeleton from '../components/LoadingSkeleton';
import { EmptyState, ErrorState } from '../components/ui';

const WALLET = { coinBalance: 1250, currencyType: 'NON_REDEEMABLE', redeemable: false, notice: 'Coins are virtual entertainment currency.', dailyBonusAvailable: true, dailyBonusCoins: 50 };
const page = (items, extra = {}) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra });
const room = (id, n = 1, max = 4) => ({ id, code: 'ABC123', name: `Room ${id}`, hostId: 'x', maxPlayers: max, status: 'WAITING', isPrivate: false, matchId: null,
  players: Array.from({ length: n }, (_, i) => ({ userId: `p${i}`, name: `P${i}`, avatar: 'avatar-01', ready: false, host: i === 0 })) });

beforeEach(() => {
  vi.clearAllMocks();
  useAuthStore.setState({ user: USER, status: 'authenticated' });
  useWalletStore.setState({ wallet: null, loading: false, error: null });
});

describe('shared states', () => {
  it('LoadingSkeleton, EmptyState and ErrorState render accessibly', async () => {
    const retry = vi.fn();
    const { rerender } = renderAt(<LoadingSkeleton />);
    expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();
    rerender(<ErrorState message="Boom" onRetry={retry} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
    await userEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(retry).toHaveBeenCalled();
    rerender(<EmptyState title="Nada" message="Nothing" />);
    expect(screen.getByTestId('empty-state')).toHaveTextContent('Nada');
  });
});

describe('Dashboard', () => {
  beforeEach(() => {
    RoomApi.active.mockResolvedValue(null);
    MatchApi.list.mockResolvedValue(page([]));
    LeaderboardApi.top.mockResolvedValue(page([]));
    WalletApi.get.mockResolvedValue(WALLET);
  });

  it('shows the user stats, the wallet and empty states for new data', async () => {
    useWalletStore.setState({ wallet: WALLET });
    renderAt(<Dashboard />, { route: '/dashboard', path: '/dashboard' });
    expect(await screen.findByText(/Hey, Asha/)).toBeInTheDocument();
    expect(screen.getByTestId('coin-balance')).toHaveTextContent('1,250');
    expect(await screen.findByText(/no matches yet/i)).toBeInTheDocument();
    expect(await screen.findByText(/no ranked players yet/i)).toBeInTheDocument();
  });

  it('offers to resume an active match', async () => {
    RoomApi.active.mockResolvedValue({ room: room('r1'), matchId: 'm9' });
    useWalletStore.setState({ wallet: WALLET });
    renderAt(<Dashboard />, { route: '/dashboard', path: '/dashboard' });
    await userEvent.click(await screen.findByRole('button', { name: /resume match/i }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/game/m9'));
  });

  it('shows an error state with retry when the match list fails', async () => {
    MatchApi.list.mockRejectedValueOnce(apiError(500, 'INTERNAL', 'Server exploded')).mockResolvedValue(page([]));
    useWalletStore.setState({ wallet: WALLET });
    renderAt(<Dashboard />, { route: '/dashboard', path: '/dashboard' });
    expect(await screen.findByText('Server exploded')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: /try again/i })[0]);
    expect(await screen.findByText(/no matches yet/i)).toBeInTheDocument();
  });

  it('claims the daily bonus and updates the balance', async () => {
    useWalletStore.setState({ wallet: WALLET });
    WalletApi.dailyBonus.mockResolvedValue({ ...WALLET, coinBalance: 1300, dailyBonusAvailable: false });
    renderAt(<Dashboard />, { route: '/dashboard', path: '/dashboard' });
    await userEvent.click(await screen.findByRole('button', { name: /claim daily bonus/i }));
    await waitFor(() => expect(screen.getByTestId('coin-balance')).toHaveTextContent('1,300'));
    expect(screen.getByRole('button', { name: /daily bonus claimed/i })).toBeDisabled();
  });
});

describe('Wallet', () => {
  it('renders the balance, the non-redeemable notice and recent transactions', async () => {
    useWalletStore.setState({ wallet: WALLET });
    WalletApi.transactions.mockResolvedValue(page([{ id: 't1', type: 'DAILY_BONUS', amount: 50, newBalance: 1250, description: 'Daily login bonus', status: 'COMPLETED', createdAt: '2026-10-01T10:00:00Z' }]));
    renderAt(<Wallet />, { route: '/wallet', path: '/wallet' });
    expect(screen.getByTestId('coin-balance')).toHaveTextContent('1,250');
    expect(screen.getByText(/virtual entertainment currency/i)).toBeInTheDocument();
    expect(await screen.findByText('Daily login bonus')).toBeInTheDocument();
    expect(screen.getByText('+50')).toBeInTheDocument();
  });

  it('shows an empty state with no transactions and an error with retry', async () => {
    useWalletStore.setState({ wallet: WALLET });
    WalletApi.transactions.mockResolvedValueOnce(page([]));
    const { unmount } = renderAt(<Wallet />, { route: '/wallet', path: '/wallet' });
    expect(await screen.findByText(/no transactions yet/i)).toBeInTheDocument();
    unmount();
    WalletApi.transactions.mockRejectedValueOnce(apiError(500, 'X', 'Ledger unavailable'));
    renderAt(<Wallet />, { route: '/wallet', path: '/wallet' });
    expect(await screen.findByText('Ledger unavailable')).toBeInTheDocument();
  });
});

describe('Lobby', () => {
  it('lists rooms and joins one', async () => {
    RoomApi.list.mockResolvedValue(page([room('r1', 2), room('r2', 4, 4)]));
    RoomApi.join.mockResolvedValue(room('r1', 3));
    renderAt(<Lobby />, { route: '/lobby', path: '/lobby' });
    const cards = await screen.findAllByTestId('room-card');
    expect(cards).toHaveLength(2);
    expect(within(cards[1]).getByRole('button')).toBeDisabled(); // full room
    await userEvent.click(within(cards[0]).getByRole('button', { name: /join room/i }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/room/r1'));
  });

  it('shows an empty state and re-queries when the mode filter changes', async () => {
    RoomApi.list.mockResolvedValue(page([]));
    renderAt(<Lobby />, { route: '/lobby', path: '/lobby' });
    expect(await screen.findByText(/no open rooms/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '2 players' }));
    await waitFor(() => expect(RoomApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ maxPlayers: '2' })));
  });

  it('shows the server error when joining fails', async () => {
    RoomApi.list.mockResolvedValue(page([room('r1', 1)]));
    RoomApi.join.mockRejectedValue(apiError(409, 'ALREADY_IN_ROOM', 'Leave your current room before joining another one'));
    renderAt(<Lobby />, { route: '/lobby', path: '/lobby' });
    await userEvent.click(await screen.findByRole('button', { name: /join room/i }));
    await waitFor(() => expect(RoomApi.join).toHaveBeenCalled());
    expect(useToastStore.getState().toasts.map((t) => t.message)).toContain('Leave your current room before joining another one');
    expect(screen.queryByTestId('location')).not.toBeInTheDocument(); // stayed on the lobby
  });
});

describe('Create / join room', () => {
  it('creates a room with the chosen options and navigates to its waiting room', async () => {
    RoomApi.create.mockResolvedValue(room('new1'));
    renderAt(<CreateRoom />, { route: '/rooms/create', path: '/rooms/create' });
    const name = screen.getByLabelText('Room name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Friday Ludo');
    await userEvent.click(screen.getByLabelText(/^2/));
    await userEvent.click(screen.getByLabelText('Private room'));
    await userEvent.click(screen.getByRole('button', { name: /create room/i }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/room/new1'));
    expect(RoomApi.create).toHaveBeenCalledWith({ name: 'Friday Ludo', maxPlayers: 2, isPrivate: true });
  });

  it('validates the room name', async () => {
    renderAt(<CreateRoom />, { route: '/rooms/create', path: '/rooms/create' });
    const name = screen.getByLabelText('Room name');
    await userEvent.clear(name);
    await userEvent.type(name, 'ab');
    await userEvent.click(screen.getByRole('button', { name: /create room/i }));
    expect(await screen.findByText(/3-40 characters/i)).toBeInTheDocument();
    expect(RoomApi.create).not.toHaveBeenCalled();
  });

  it('validates the join code format and joins by code', async () => {
    RoomApi.join.mockResolvedValue(room('r7'));
    renderAt(<JoinRoom />, { route: '/rooms/join', path: '/rooms/join' });
    await userEvent.type(screen.getByLabelText('Room code'), 'abc');
    await userEvent.click(screen.getByRole('button', { name: /join room/i }));
    expect(await screen.findByText(/6 letters or digits/i)).toBeInTheDocument();
    expect(RoomApi.join).not.toHaveBeenCalled();
    await userEvent.type(screen.getByLabelText('Room code'), '123');
    await userEvent.click(screen.getByRole('button', { name: /join room/i }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/room/r7'));
    expect(RoomApi.join).toHaveBeenCalledWith('ABC123');
  });

  it('shows the server message for an invalid or full room', async () => {
    RoomApi.join.mockRejectedValue(apiError(404, 'ROOM_NOT_FOUND', 'Room not found'));
    renderAt(<JoinRoom />, { route: '/rooms/join', path: '/rooms/join' });
    await userEvent.type(screen.getByLabelText('Room code'), 'ZZZ999');
    await userEvent.click(screen.getByRole('button', { name: /join room/i }));
    expect(await screen.findByText('Room not found')).toBeInTheDocument();
  });
});

describe('Coin store', () => {
  const items = [
    { id: 'i1', sku: 'board-neon', name: 'Neon Board', description: 'Glow', category: 'BOARD_SKIN', priceCoins: 500, rarity: 'rare', assetKey: 'board-neon', active: true, owned: false, equipped: false },
    { id: 'i2', sku: 'board-royal', name: 'Royal Board', description: 'Gold', category: 'BOARD_SKIN', priceCoins: 5000, rarity: 'epic', assetKey: 'board-royal', active: true, owned: false, equipped: false },
    { id: 'i3', sku: 'board-forest', name: 'Forest Board', description: 'Green', category: 'BOARD_SKIN', priceCoins: 100, rarity: 'common', assetKey: 'board-forest', active: true, owned: true, equipped: false },
  ];
  beforeEach(() => {
    useWalletStore.setState({ wallet: WALLET });
    StoreApi.items.mockResolvedValue(items);
    PaymentApi.status.mockResolvedValue({ enabled: false, message: null, packages: [] });
  });

  it('shows ownership, affordability and the disabled-payments note', async () => {
    renderAt(<CoinStore />, { route: '/store', path: '/store' });
    const cards = await screen.findAllByTestId('store-item');
    expect(cards).toHaveLength(3);
    expect(within(cards[1]).getByRole('button')).toBeDisabled();
    expect(within(cards[1]).getByText(/not enough coins/i)).toBeInTheDocument();
    expect(within(cards[2]).getByRole('button', { name: /equip/i })).toBeInTheDocument();
    expect(await screen.findByText(/coin purchases are not available/i)).toBeInTheDocument();
    expect(screen.getByText(/cannot be withdrawn/i)).toBeInTheDocument();
  });

  it('confirms before purchasing, then updates the wallet from the server response', async () => {
    StoreApi.purchase.mockResolvedValue({ purchase: {}, wallet: { ...WALLET, coinBalance: 750 } });
    renderAt(<CoinStore />, { route: '/store', path: '/store' });
    const cards = await screen.findAllByTestId('store-item');
    await userEvent.click(within(cards[0]).getByRole('button'));
    expect(StoreApi.purchase).not.toHaveBeenCalled();
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /^buy$/i }));
    await waitFor(() => expect(StoreApi.purchase).toHaveBeenCalledWith('i1'));
    await waitFor(() => expect(useWalletStore.getState().wallet.coinBalance).toBe(750));
  });
});

describe('Achievements', () => {
  it('renders progress and handles an empty list', async () => {
    UserApi.achievements.mockResolvedValueOnce([
      { key: 'first_win', title: 'First Win', description: 'Win a match', unlocked: true, progress: 1, target: 1 },
      { key: 'ten_wins', title: 'Veteran', description: 'Win 10', unlocked: false, progress: 4, target: 10 },
    ]);
    const { unmount } = renderAt(<Achievements />, { route: '/achievements', path: '/achievements' });
    expect(await screen.findByText('1 of 2 unlocked')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: /veteran progress/i })).toHaveAttribute('aria-valuenow', '40');
    unmount();
    UserApi.achievements.mockResolvedValueOnce([]);
    renderAt(<Achievements />, { route: '/achievements', path: '/achievements' });
    expect(await screen.findByText(/no achievements available/i)).toBeInTheDocument();
  });
});
