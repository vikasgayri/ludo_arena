import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError, renderAt, USER } from './helpers';

vi.mock('../lib/api', async (orig) => {
  const actual = await orig();
  return { ...actual, AuthApi: { login: vi.fn(), register: vi.fn(), me: vi.fn(), logout: vi.fn(), forgot: vi.fn(), reset: vi.fn() } };
});

import { AuthApi } from '../lib/api';
import { useAuthStore } from '../store/authStore';
import { tokens } from '../lib/tokens';
import Login from '../pages/Login';
import Register from '../pages/Register';
import ForgotPassword from '../pages/ForgotPassword';
import ResetPassword from '../pages/ResetPassword';

const AUTH = { accessToken: 'acc', refreshToken: 'ref', expiresInSeconds: 900, tokenType: 'Bearer', user: USER };

beforeEach(() => {
  vi.clearAllMocks();
  tokens.clear();
  useAuthStore.setState({ user: null, status: 'anonymous' });
});

describe('Login', () => {
  it('validates fields client-side without calling the API', async () => {
    renderAt(<Login />, { route: '/login', path: '/login' });
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByText(/valid email/i)).toBeInTheDocument();
    expect(screen.getByText(/password is required/i)).toBeInTheDocument();
    expect(AuthApi.login).not.toHaveBeenCalled();
  });

  it('signs in, stores tokens and navigates to the dashboard', async () => {
    AuthApi.login.mockResolvedValue(AUTH);
    renderAt(<Login />, { route: '/login', path: '/login' });
    await userEvent.type(screen.getByLabelText(/email/i), 'asha@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'secret123');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/dashboard'));
    expect(AuthApi.login).toHaveBeenCalledWith({ email: 'asha@example.com', password: 'secret123' });
    expect(tokens.getAccess()).toBe('acc');
    expect(useAuthStore.getState().user.name).toBe('Asha');
  });

  it('shows the server error and stays on the page for bad credentials', async () => {
    AuthApi.login.mockRejectedValue(apiError(401, 'INVALID_CREDENTIALS', 'Invalid email or password'));
    renderAt(<Login />, { route: '/login', path: '/login' });
    await userEvent.type(screen.getByLabelText(/email/i), 'asha@example.com');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrongpass1');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    expect(useAuthStore.getState().status).toBe('anonymous');
    expect(tokens.getAccess()).toBeNull();
  });
});

describe('Register', () => {
  const fill = async ({ name = 'Asha', email = 'asha@example.com', password = 'secret123', confirm = 'secret123' } = {}) => {
    await userEvent.type(screen.getByLabelText('Display name'), name);
    await userEvent.type(screen.getByLabelText('Email'), email);
    await userEvent.type(screen.getByLabelText('Password'), password);
    await userEvent.type(screen.getByLabelText('Confirm password'), confirm);
    await userEvent.click(screen.getByRole('button', { name: /create account/i }));
  };

  it('rejects weak passwords and mismatches locally', async () => {
    renderAt(<Register />, { route: '/register', path: '/register' });
    await fill({ password: 'abcdefgh', confirm: 'different1' });
    expect(await screen.findByText(/must be 8-72 characters/i)).toBeInTheDocument();
    expect(screen.getByText(/do not match/i)).toBeInTheDocument();
    expect(AuthApi.register).not.toHaveBeenCalled();
  });

  it('registers and lands on the dashboard', async () => {
    AuthApi.register.mockResolvedValue(AUTH);
    renderAt(<Register />, { route: '/register', path: '/register' });
    await fill();
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/dashboard'));
    expect(AuthApi.register).toHaveBeenCalledWith({ name: 'Asha', email: 'asha@example.com', password: 'secret123' });
  });

  it('surfaces field errors returned by the server (e.g. duplicate email)', async () => {
    AuthApi.register.mockRejectedValue(apiError(400, 'VALIDATION_ERROR', 'Validation failed', { email: 'Email already registered' }));
    renderAt(<Register />, { route: '/register', path: '/register' });
    await fill();
    expect(await screen.findByText('Email already registered')).toBeInTheDocument();
  });
});

describe('Forgot / reset password', () => {
  it('shows a neutral confirmation after requesting a reset link', async () => {
    AuthApi.forgot.mockResolvedValue(null);
    renderAt(<ForgotPassword />, { route: '/forgot-password', path: '/forgot-password' });
    await userEvent.type(screen.getByLabelText(/email/i), 'asha@example.com');
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }));
    expect(await screen.findByRole('status')).toHaveTextContent(/if that email is registered/i);
  });

  it('refuses to render the reset form without a token', () => {
    renderAt(<ResetPassword />, { route: '/reset-password', path: '/reset-password' });
    expect(screen.getByText(/reset link invalid/i)).toBeInTheDocument();
  });

  it('submits the token with the new password and returns to login', async () => {
    AuthApi.reset.mockResolvedValue(null);
    renderAt(<ResetPassword />, { route: '/reset-password?token=tok123', path: '/reset-password' });
    await userEvent.type(screen.getByLabelText('New password'), 'newpass123');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'newpass123');
    await userEvent.click(screen.getByRole('button', { name: /update password/i }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
    expect(AuthApi.reset).toHaveBeenCalledWith('tok123', 'newpass123');
  });
});
