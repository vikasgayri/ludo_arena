import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { AuthApi, errorMessage } from '../lib/api';
import { toast } from '../store/toastStore';
import { Field, Spinner } from '../components/ui';
import AuthShell from './AuthShell';
import { PASSWORD_HINT, PASSWORD_RE } from './Register';

export default function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const navigate = useNavigate();
  const [pw, setPw] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (!token) {
    return (
      <AuthShell title="Reset link invalid">
        <p className="text-sm text-slate-300">This reset link is missing its token. Request a new one.</p>
        <Link to="/forgot-password" className="btn-primary mt-4 w-full">Request new link</Link>
      </AuthShell>
    );
  }

  const submit = async (ev) => {
    ev.preventDefault();
    const e = {};
    if (!PASSWORD_RE.test(pw)) e.password = `Password must be ${PASSWORD_HINT}`;
    if (pw !== confirm) e.confirm = 'Passwords do not match';
    setErrors(e);
    setFormError(null);
    if (Object.keys(e).length) return;
    setBusy(true);
    try {
      await AuthApi.reset(token, pw);
      toast.success('Password updated. Please sign in.');
      navigate('/login', { replace: true });
    } catch (err) {
      setFormError(errorMessage(err, 'Could not reset the password. The link may have expired.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell title="Choose a new password">
      <form onSubmit={submit} className="space-y-4" noValidate>
        {formError && <p role="alert" className="rounded-xl bg-red-500/10 px-3 py-2 text-sm text-red-300">{formError}</p>}
        <Field label="New password" htmlFor="password" error={errors.password}><input id="password" type="password" className="input" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} /></Field>
        <Field label="Confirm password" htmlFor="confirm" error={errors.confirm}><input id="confirm" type="password" className="input" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} /></Field>
        <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner className="h-4 w-4" />}Update password</button>
      </form>
    </AuthShell>
  );
}
