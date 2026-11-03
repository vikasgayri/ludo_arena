import { useNavigate } from 'react-router-dom';
import { Badge, EmptyState, ErrorState } from './ui';
import LoadingSkeleton from './LoadingSkeleton';
import { COLOR_HEX, formatDate } from '../lib/format';

const STATUS_TONE = { ACTIVE: 'blue', FINISHED: 'green', ABANDONED: 'gray' };

export default function MatchHistoryTable({ matches, loading, error, onRetry, admin = false, onSelect }) {
  const navigate = useNavigate();
  if (loading && !matches) return <LoadingSkeleton variant="table" rows={5} />;
  if (error) return <ErrorState message={error} onRetry={onRetry} />;
  if (!matches?.length) return <EmptyState title="No matches yet" message="Matches you play will show up here." />;
  return (
    <div className="overflow-x-auto rounded-2xl border border-white/5">
      <table className="min-w-full divide-y divide-white/5">
        <thead className="bg-navy-800">
          <tr>
            <th className="th">Date</th><th className="th">Mode</th><th className="th">Players</th>
            <th className="th">Status</th>{!admin && <th className="th">Result</th>}<th className="th" />
          </tr>
        </thead>
        <tbody className="divide-y divide-white/5">
          {matches.map((m) => (
            <tr key={m.id} className="hover:bg-white/[.02]">
              <td className="td whitespace-nowrap">{formatDate(m.finishedAt || m.createdAt)}</td>
              <td className="td">{m.mode}P</td>
              <td className="td">
                <div className="flex flex-wrap gap-1.5">
                  {m.players.map((p) => (
                    <span key={p.userId} className="inline-flex items-center gap-1 rounded-full bg-white/5 px-2 py-0.5 text-xs" title={`Rank ${p.rank || '-'}`}>
                      <span className="h-2 w-2 rounded-full" style={{ background: COLOR_HEX[p.color] }} />{p.name}{p.userId === m.winnerId ? ' 🏆' : ''}
                    </span>
                  ))}
                </div>
              </td>
              <td className="td"><Badge tone={STATUS_TONE[m.status] || 'gray'}>{m.status}</Badge></td>
              {!admin && (
                <td className="td">
                  {m.youWon == null ? '—' : m.youWon ? <Badge tone="green">Won</Badge> : <Badge tone="red">Lost</Badge>}
                </td>
              )}
              <td className="td text-right">
                {m.status === 'ACTIVE' && !admin
                  ? <button className="btn-primary px-3 py-1" onClick={() => navigate(`/game/${m.id}`)}>Resume</button>
                  : onSelect && <button className="btn-ghost px-3 py-1" onClick={() => onSelect(m)}>Details</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
