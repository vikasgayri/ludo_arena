import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Plus, RefreshCw } from 'lucide-react';
import { RoomApi, errorMessage } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import { toast } from '../store/toastStore';
import GameRoomCard from '../components/GameRoomCard';
import LoadingSkeleton from '../components/LoadingSkeleton';
import { EmptyState, ErrorState, PageHeader, Pagination } from '../components/ui';

export default function Lobby() {
  const navigate = useNavigate();
  const [mode, setMode] = useState('');
  const [page, setPage] = useState(0);
  const [joining, setJoining] = useState(null);
  const rooms = useAsync(() => RoomApi.list({ maxPlayers: mode || undefined, page, size: 12 }), [mode, page]);

  // Light polling keeps the lobby fresh without a dedicated socket topic.
  const { reload } = rooms;
  const silent = useCallback(() => { if (document.visibilityState === 'visible') reload(); }, [reload]);
  useEffect(() => { const t = setInterval(silent, 8000); return () => clearInterval(t); }, [silent]);

  const join = async (room) => {
    setJoining(room.id);
    try {
      const joined = await RoomApi.join(room.id);
      navigate(`/room/${joined.id}`);
    } catch (e) {
      toast.error(errorMessage(e));
      reload();
    } finally {
      setJoining(null);
    }
  };

  const data = rooms.data;
  return (
    <div>
      <PageHeader
        title="Lobby"
        subtitle="Open public rooms waiting for players"
        actions={(
          <>
            <button className="btn-secondary" onClick={reload} aria-label="Refresh rooms"><RefreshCw className="h-4 w-4" aria-hidden />Refresh</button>
            <Link to="/rooms/create" className="btn-primary"><Plus className="h-4 w-4" aria-hidden />Create room</Link>
          </>
        )}
      />
      <div className="mb-4 flex flex-wrap gap-2" role="group" aria-label="Filter by mode">
        {[['', 'All'], ['2', '2 players'], ['3', '3 players'], ['4', '4 players']].map(([v, label]) => (
          <button key={v} className={mode === v ? 'btn-primary' : 'btn-secondary'} aria-pressed={mode === v} onClick={() => { setMode(v); setPage(0); }}>{label}</button>
        ))}
      </div>
      {rooms.loading && !data ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{Array.from({ length: 6 }).map((_, i) => <LoadingSkeleton key={i} variant="card" />)}</div>
      ) : rooms.error && !data ? (
        <ErrorState message={rooms.error} onRetry={reload} />
      ) : !data?.items.length ? (
        <EmptyState title="No open rooms" message="Be the first — create a room and invite a friend with its code." action={<Link to="/rooms/create" className="btn-primary mt-2">Create a room</Link>} />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.items.map((r) => <GameRoomCard key={r.id} room={r} onJoin={join} joining={joining === r.id} disabled={!!joining} />)}
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
