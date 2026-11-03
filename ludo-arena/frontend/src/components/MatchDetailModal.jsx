import { AdminApi, MatchApi } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import { COLOR_HEX, formatDate } from '../lib/format';
import { describeEvent } from '../lib/events';
import Modal from './Modal';
import LoadingSkeleton from './LoadingSkeleton';
import { ErrorState } from './ui';

function Body({ match, admin }) {
  const events = useAsync(() => (admin ? AdminApi.matchEvents(match.id, { size: 100 }) : MatchApi.events(match.id, { size: 100 })), [match.id]);
  const names = new Map(match.players.map((p) => [p.userId, p.name]));
  const nameOf = (id) => names.get(id) || 'Someone';
  return (
    <div className="space-y-4">
      <dl className="grid grid-cols-2 gap-2 text-sm">
        <dt className="text-slate-400">Started</dt><dd>{formatDate(match.createdAt)}</dd>
        <dt className="text-slate-400">Finished</dt><dd>{formatDate(match.finishedAt)}</dd>
        <dt className="text-slate-400">Mode</dt><dd>{match.mode} players</dd>
        <dt className="text-slate-400">Status</dt><dd>{match.status}</dd>
      </dl>
      <ul className="space-y-1">
        {match.players.map((p) => (
          <li key={p.userId} className="flex items-center justify-between text-sm"><span className="flex items-center gap-2"><span className="h-3 w-3 rounded-full" style={{ background: COLOR_HEX[p.color] }} />{p.name}</span><span className="text-slate-400">{p.rank ? `#${p.rank}` : '—'} · {p.finishedTokens}/4 home</span></li>
        ))}
      </ul>
      <div>
        <h3 className="mb-1 text-sm font-bold">Event log</h3>
        {events.loading ? <LoadingSkeleton rows={3} /> : events.error ? <ErrorState message={events.error} onRetry={events.reload} /> : (
          <ol className="max-h-56 space-y-1 overflow-y-auto text-xs text-slate-300">
            {events.data.items.map((e) => <li key={e.eventId}><span className="text-slate-500">#{e.seq}</span> {describeEvent(e, nameOf)}</li>)}
            {!events.data.items.length && <li className="text-slate-500">No events recorded.</li>}
          </ol>
        )}
      </div>
    </div>
  );
}

export default function MatchDetailModal({ match, onClose, admin = false }) {
  return (
    <Modal open={!!match} title="Match details" onClose={onClose} size="lg">
      {match && <Body match={match} admin={admin} />}
    </Modal>
  );
}
