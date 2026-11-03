import { Lock, Users } from 'lucide-react';
import { Avatar } from './ui';

export default function GameRoomCard({ room, onJoin, joining, disabled }) {
  const full = room.players.length >= room.maxPlayers;
  return (
    <article className="card flex flex-col gap-3" data-testid="room-card">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="truncate font-bold">{room.name}</h3>
          <p className="text-xs text-slate-400">{room.maxPlayers}-player match</p>
        </div>
        {room.isPrivate && <Lock className="h-4 w-4 text-slate-400" aria-label="Private room" />}
      </div>
      <div className="flex items-center justify-between">
        <div className="flex -space-x-2">
          {room.players.map((p) => <Avatar key={p.userId} avatar={p.avatar} name={p.name} size={30} />)}
        </div>
        <span className="flex items-center gap-1 text-sm text-slate-300"><Users className="h-4 w-4" aria-hidden />{room.players.length}/{room.maxPlayers}</span>
      </div>
      <button className="btn-primary" disabled={full || joining || disabled} onClick={() => onJoin(room)}>
        {full ? 'Room full' : joining ? 'Joining…' : 'Join room'}
      </button>
    </article>
  );
}
