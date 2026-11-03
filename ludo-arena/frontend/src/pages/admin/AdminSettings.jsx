import { useEffect, useState } from 'react';
import { AdminApi, errorMessage, fieldErrors as getFieldErrors } from '../../lib/api';
import { useAsync } from '../../lib/useAsync';
import { useAuthStore } from '../../store/authStore';
import { toast } from '../../store/toastStore';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { ErrorState, Field, PageHeader, Spinner } from '../../components/ui';

const NUMBERS = [
  ['signupBonusCoins', 'Signup bonus (coins)', 0, 100000], ['dailyBonusCoins', 'Daily bonus (coins)', 0, 100000],
  ['matchWinRewardCoins', 'Win reward (coins)', 0, 100000], ['matchParticipationRewardCoins', 'Participation reward (coins)', 0, 100000],
  ['maxConsecutiveSixes', 'Max consecutive sixes', 0, 10], ['turnTimeoutSeconds', 'Turn timeout (seconds)', 10, 300],
];
const FLAGS = [
  ['registrationOpen', 'Registration open'], ['maintenanceMode', 'Maintenance mode'],
  ['extraTurnOnSix', 'Extra turn on six'], ['extraTurnOnCapture', 'Extra turn on capture'], ['extraTurnOnHome', 'Extra turn on reaching home'],
];

export default function AdminSettings() {
  const me = useAuthStore((s) => s.user);
  const { data, loading, error, reload } = useAsync(() => AdminApi.settings(), []);
  const [f, setF] = useState(null);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const canEdit = me?.role === 'SUPER_ADMIN';
  useEffect(() => { if (data) setF(data); }, [data]);

  const save = async (ev) => {
    ev.preventDefault();
    const e = {};
    const body = {};
    NUMBERS.forEach(([k, label, min, max]) => {
      const n = parseInt(f[k], 10);
      if (!Number.isFinite(n) || n < min || n > max) e[k] = `${min}–${max}`; else body[k] = n;
    });
    FLAGS.forEach(([k]) => { body[k] = !!f[k]; });
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    try {
      setF(await AdminApi.updateSettings(body));
      toast.success('Settings saved');
    } catch (err) {
      setErrors(getFieldErrors(err));
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Platform settings" subtitle={canEdit ? 'Changes apply to new matches and rewards immediately.' : 'Only a super admin can change these values.'} />
      {loading && !f ? <LoadingSkeleton variant="page" />
        : error ? <ErrorState message={error} onRetry={reload} />
          : f && (
            <form className="card space-y-6" onSubmit={save} noValidate>
              <fieldset disabled={!canEdit || busy} className="space-y-6">
                <div className="grid gap-4 sm:grid-cols-2">
                  {NUMBERS.map(([k, label]) => <Field key={k} label={label} htmlFor={k} error={errors[k]}><input id={k} className="input" inputMode="numeric" value={f[k]} onChange={(e) => setF({ ...f, [k]: e.target.value })} /></Field>)}
                </div>
                <div className="grid gap-2 sm:grid-cols-2">
                  {FLAGS.map(([k, label]) => <label key={k} className="flex cursor-pointer items-center justify-between rounded-xl border border-white/10 p-3 text-sm">{label}<input type="checkbox" className="h-5 w-5 accent-brand-500" checked={!!f[k]} onChange={(e) => setF({ ...f, [k]: e.target.checked })} /></label>)}
                </div>
                <p className="text-xs text-slate-500">Payments are currently <b>{f.paymentsEnabled ? 'enabled' : 'disabled'}</b>. This is controlled by server configuration and cannot be changed here.</p>
              </fieldset>
              {canEdit && <button className="btn-primary" disabled={busy}>{busy && <Spinner className="h-4 w-4" />}Save settings</button>}
            </form>
          )}
    </div>
  );
}
