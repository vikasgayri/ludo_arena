import { useState } from 'react';
import { Flag } from 'lucide-react';
import { ReportApi, UserApi, errorMessage, fieldErrors as getFieldErrors } from '../lib/api';
import { useAsync } from '../lib/useAsync';
import { useAuthStore } from '../store/authStore';
import { toast } from '../store/toastStore';
import { Badge, EmptyState, Field, PageHeader, Spinner } from '../components/ui';
import Modal from '../components/Modal';
import { formatDate } from '../lib/format';
import { PASSWORD_HINT, PASSWORD_RE } from './Register';

const REPORT_TYPES = ['CHEATING', 'ABUSE', 'BUG', 'OTHER'];
const TONE = { OPEN: 'yellow', IN_REVIEW: 'blue', RESOLVED: 'green', DISMISSED: 'gray' };

export default function Settings() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [pwErrors, setPwErrors] = useState({});
  const [pwBusy, setPwBusy] = useState(false);
  const [prefBusy, setPrefBusy] = useState(false);
  const [reportOpen, setReportOpen] = useState(false);
  const reports = useAsync(() => ReportApi.mine({ page: 0, size: 5 }), []);

  if (!user) return null;

  const togglePref = async (key) => {
    setPrefBusy(true);
    try {
      setUser(await UserApi.update({ [key]: !user.preferences[key] }));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setPrefBusy(false);
    }
  };

  const changePassword = async (ev) => {
    ev.preventDefault();
    const e = {};
    if (!pw.currentPassword) e.currentPassword = 'Enter your current password';
    if (!PASSWORD_RE.test(pw.newPassword)) e.newPassword = `Password must be ${PASSWORD_HINT}`;
    if (pw.newPassword !== pw.confirm) e.confirm = 'Passwords do not match';
    setPwErrors(e);
    if (Object.keys(e).length) return;
    setPwBusy(true);
    try {
      await UserApi.changePassword({ currentPassword: pw.currentPassword, newPassword: pw.newPassword });
      toast.success('Password changed');
      setPw({ currentPassword: '', newPassword: '', confirm: '' });
    } catch (err) {
      setPwErrors({ ...getFieldErrors(err), currentPassword: getFieldErrors(err).currentPassword || errorMessage(err) });
    } finally {
      setPwBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader title="Settings" />
      <section className="card space-y-3" aria-label="Preferences">
        <h2 className="font-bold">Preferences</h2>
        {[['sound', 'Sound effects', 'Play sounds for dice and moves'], ['animations', 'Animations', 'Animate dice rolls and token movement']].map(([k, label, hint]) => (
          <label key={k} className="flex cursor-pointer items-center justify-between rounded-xl border border-white/10 p-3">
            <span><span className="block text-sm font-medium">{label}</span><span className="block text-xs text-slate-400">{hint}</span></span>
            <input type="checkbox" className="h-5 w-5 accent-brand-500" checked={!!user.preferences?.[k]} disabled={prefBusy} onChange={() => togglePref(k)} aria-label={label} />
          </label>
        ))}
      </section>

      <form className="card space-y-4" onSubmit={changePassword} noValidate aria-label="Change password">
        <h2 className="font-bold">Change password</h2>
        <Field label="Current password" htmlFor="cur" error={pwErrors.currentPassword}><input id="cur" type="password" autoComplete="current-password" className="input" value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} /></Field>
        <Field label="New password" htmlFor="new" error={pwErrors.newPassword}><input id="new" type="password" autoComplete="new-password" className="input" value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} /></Field>
        <Field label="Confirm new password" htmlFor="conf" error={pwErrors.confirm}><input id="conf" type="password" autoComplete="new-password" className="input" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} /></Field>
        <button className="btn-primary" disabled={pwBusy}>{pwBusy && <Spinner className="h-4 w-4" />}Update password</button>
      </form>

      <section className="card" aria-label="Support">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-bold">Report a problem</h2>
          <button className="btn-secondary" onClick={() => setReportOpen(true)}><Flag className="h-4 w-4" aria-hidden />New report</button>
        </div>
        {reports.loading ? <Spinner className="h-5 w-5" />
          : !reports.data?.items.length ? <EmptyState title="No reports" message="Found a bug or a rule-breaker? Let us know." />
            : <ul className="divide-y divide-white/5">{reports.data.items.map((r) => (
              <li key={r.id} className="flex items-center justify-between gap-3 py-2 text-sm"><span className="min-w-0 truncate"><b>{r.type}</b> · {r.description}</span><span className="flex shrink-0 items-center gap-2"><Badge tone={TONE[r.status]}>{r.status}</Badge><span className="hidden text-xs text-slate-500 sm:inline">{formatDate(r.createdAt)}</span></span></li>
            ))}</ul>}
      </section>
      <ReportModal open={reportOpen} onClose={() => setReportOpen(false)} onSent={reports.reload} />
    </div>
  );
}

function ReportModal({ open, onClose, onSent }) {
  const [form, setForm] = useState({ type: 'BUG', description: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    if (form.description.trim().length < 10) { setError('Please describe the problem (at least 10 characters)'); return; }
    setBusy(true);
    setError(null);
    try {
      await ReportApi.create({ type: form.type, description: form.description.trim() });
      toast.success('Report sent. Thank you!');
      setForm({ type: 'BUG', description: '' });
      onSent();
      onClose();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Modal open={open} title="Report a problem" onClose={onClose} footer={<><button className="btn-secondary" onClick={onClose}>Cancel</button><button className="btn-primary" onClick={submit} disabled={busy}>{busy ? 'Sending…' : 'Send report'}</button></>}>
      <div className="space-y-3">
        <Field label="Type" htmlFor="rep-type"><select id="rep-type" className="input" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>{REPORT_TYPES.map((t) => <option key={t}>{t}</option>)}</select></Field>
        <Field label="Description" htmlFor="rep-desc" error={error}><textarea id="rep-desc" className="input min-h-28" maxLength={1000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
      </div>
    </Modal>
  );
}
