import { create } from 'zustand';
import { WalletApi, errorMessage } from '../lib/api';

export const useWalletStore = create((set) => ({
  wallet: null,
  loading: false,
  error: null,

  async load() {
    set({ loading: true, error: null });
    try {
      const wallet = await WalletApi.get();
      set({ wallet, loading: false });
      return wallet;
    } catch (e) {
      set({ loading: false, error: errorMessage(e) });
      return null;
    }
  },

  setWallet(wallet) { set({ wallet }); },
  reset() { set({ wallet: null, loading: false, error: null }); },
}));
