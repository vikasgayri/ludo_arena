import { useEffect } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import { AppLayout, GuestOnly, PublicLayout, RequireAdmin, RequireAuth } from './components/Layouts';
import ToastContainer from './components/Toast';
import ServerStatus from './components/ServerStatus';

import Landing from './pages/Landing';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import Dashboard from './pages/Dashboard';
import Lobby from './pages/Lobby';
import PlayMode from './pages/PlayMode';
import CreateRoom from './pages/CreateRoom';
import JoinRoom from './pages/JoinRoom';
import RoomLobby from './pages/RoomLobby';
import GameRoom from './pages/GameRoom';
import LocalGame from './pages/LocalGame';
import Wallet from './pages/Wallet';
import CoinStore from './pages/CoinStore';
import Transactions from './pages/Transactions';
import MatchHistory from './pages/MatchHistory';
import Leaderboard from './pages/Leaderboard';
import Profile from './pages/Profile';
import Achievements from './pages/Achievements';
import Settings from './pages/Settings';
import Notifications from './pages/Notifications';
import NotFound from './pages/NotFound';
import AdminDashboard from './pages/admin/AdminDashboard';
import AdminUsers from './pages/admin/AdminUsers';
import AdminMatches from './pages/admin/AdminMatches';
import AdminTransactions from './pages/admin/AdminTransactions';
import AdminStats from './pages/admin/AdminStats';
import AdminStore from './pages/admin/AdminStore';
import AdminReports from './pages/admin/AdminReports';
import AdminLogs from './pages/admin/AdminLogs';
import AdminSettings from './pages/admin/AdminSettings';

export default function App() {
  const init = useAuthStore((s) => s.init);
  useEffect(() => { init(); }, [init]);
  // the offline pass & play game never talks to the server, so it never shows server banners
  const offline = useLocation().pathname === '/local';

  return (
    <>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route path="/" element={<Landing />} />
          <Route path="/local" element={<LocalGame />} />
        </Route>
        <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
        <Route path="/register" element={<GuestOnly><Register /></GuestOnly>} />
        <Route path="/forgot-password" element={<GuestOnly><ForgotPassword /></GuestOnly>} />
        <Route path="/reset-password" element={<ResetPassword />} />

        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/play" element={<PlayMode />} />
          <Route path="/lobby" element={<Lobby />} />
          <Route path="/rooms/create" element={<CreateRoom />} />
          <Route path="/rooms/join" element={<JoinRoom />} />
          <Route path="/room/:roomId" element={<RoomLobby />} />
          <Route path="/game/:matchId" element={<GameRoom />} />
          <Route path="/wallet" element={<Wallet />} />
          <Route path="/store" element={<CoinStore />} />
          <Route path="/transactions" element={<Transactions />} />
          <Route path="/matches" element={<MatchHistory />} />
          <Route path="/leaderboard" element={<Leaderboard />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/achievements" element={<Achievements />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="/notifications" element={<Notifications />} />
        </Route>

        <Route element={<RequireAuth><RequireAdmin><AppLayout admin /></RequireAdmin></RequireAuth>}>
          <Route path="/admin" element={<AdminDashboard />} />
          <Route path="/admin/users" element={<AdminUsers />} />
          <Route path="/admin/matches" element={<AdminMatches />} />
          <Route path="/admin/transactions" element={<AdminTransactions />} />
          <Route path="/admin/stats" element={<AdminStats />} />
          <Route path="/admin/store" element={<AdminStore />} />
          <Route path="/admin/reports" element={<AdminReports />} />
          <Route path="/admin/logs" element={<AdminLogs />} />
          <Route path="/admin/settings" element={<AdminSettings />} />
        </Route>

        <Route path="/home" element={<Navigate to="/" replace />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
      {!offline && <ServerStatus />}
      <ToastContainer />
    </>
  );
}
