import { useState } from 'react';
import { AdminApi, errorMessage } from '../../lib/api';
import { useAsync } from '../../lib/useAsync';
import { toast } from '../../store/toastStore';
import { formatDate } from '../../lib/format';
import Modal from '../../components/Modal';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { Badge, EmptyState, ErrorState, Field, PageHeader, Pagination } from '../../components/ui';

const TONE = { OPEN: 'yellow', IN_REVIEW: 'blue', RESOLVED: 'green', DISMISSED: 'gray' };

export default function AdminReports() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [sel, setSel] = useState(null);
  const { data, loading, error, reload } = useAsync(() => AdminApi.reports({ status: status || undefined, page, size: 15 }), [status, page]);
  return (
    <div>
      <PageHeader title="Reports & complaints" subtitle="Player-submitted reports awaiting review" />
      <div className="mb-4 max-w-xs"><label className="label" htmlFor="r-s">Status</label>
        <select id="r-s" className="input" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}><option value="">All</option><option>OPEN</option><option>IN_REVIEW</option><option>RESOLVED</option><option>DISMISSED</option></select></div>
      {loading && !data ? <LoadingSkeleton variant="table" rows={5} />
        : error ? <ErrorState message={error} onRetry={reload} />
          : !data?.items.length ? <EmptyState title="No reports" message="Nothing to review right now." />
            : (
              <>
                <ul className="space-y-3">
                  {data.items.map((r) => (
                    <li key={r.id} className="card flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="flex flex-wrap items-center gap-2 font-semibold">{r.type}<Badge tone={TONE[r.status]}>{r.status}</Badge></p>
                        <p className="mt-1 text-sm text-slate-300">{r.description}</p>
                        <p className="mt-1 text-xs text-slate-500">By {r.reporterName || r.reporterId} · {formatDate(r.createdAt)}{r.targetUserId ? ` · target ${r.targetUserId}` : ''}{r.matchId ? ` · match ${r.matchId}` : ''}</p>
                        {r.adminNote && <p className="mt-1 text-xs text-slate-400">Note: {r.adminNote}</p>}
                      </div>
                      <button className="btn-secondary" onClick={() => setSel(r)}>Review</button>
                    </li>
                  ))}
                </ul>
                <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
              </>
            )}
      {sel && <ReviewModal report={sel} onClose={() => setSel(null)} onSaved={() => { setSel(null); reload(); }} />}
    </div>
  );
}

function ReviewModal({ report, onClose, onSaved }) {
  const [st, setSt] = useState(report.status);
  const [note, setNote] = useState(report.adminNote || '');
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try { await AdminApi.updateReport(report.id, { status: st, adminNote: note.trim() || undefined }); toast.success('Report updated'); onSaved(); } catch (e) { toast.error(errorMessage(e)); } finally { setBusy(false); }
  };
  return (
    <Modal open title="Review report" onClose={onClose} footer={<><button className="btn-secondary" onClick={onClose}>Cancel</button><button className="btn-primary" onClick={save} disabled={busy}>{busy ? 'Saving…' : 'Save'}</button></>}>
      <p className="mb-3 text-sm text-slate-300">{report.description}</p>
      <div className="space-y-3">
        <Field label="Status" htmlFor="rv-s"><select id="rv-s" className="input" value={st} onChange={(e) => setSt(e.target.value)}><option>OPEN</option><option>IN_REVIEW</option><option>RESOLVED</option><option>DISMISSED</option></select></Field>
        <Field label="Admin note" htmlFor="rv-n"><textarea id="rv-n" className="input min-h-20" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
      </div>
    </Modal>
  );
}
