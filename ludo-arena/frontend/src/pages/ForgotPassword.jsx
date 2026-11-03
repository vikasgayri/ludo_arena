import { useState } from 'react';
import { Link } from 'react-router-dom';
import { MailCheck } from 'lucide-react';
import { AuthApi, errorMessage } from '../lib/api';
import { Field, Spinner } from '../components/ui';
import AuthShell from './AuthShell';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (ev) => {
    ev.preventDefault();
    setError(null);
    if (!/^\S+@\S+\.\S+$/.test(email)) { setError('Enter a valid email address'); return; }
    setBusy(true);
    try {
      await AuthApi.forgot(email.trim());
      setDone(true);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell title="Forgot your password?" subtitle="We'll email you a link to reset it.">
      {done ? (
        <div className="space-y-3 text-center" role="status">
          <MailCheck className="mx-auto h-10 w-10 text-emerald-400" aria-hidden />
          <p className="text-sm text-slate-300">If that email is registered, a reset link is on its way. The link expires in 30 minutes.</p>
          <Link to="/login" className="btn-secondary">Back to sign in</Link>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4" noValidate>
          <Field label="Email" htmlFor="email" error={error}><input id="email" type="email" className="input" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
          <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner className="h-4 w-4" />}Send reset link</button>
          <p className="text-center text-sm"><Link to="/login" className="text-brand-400 hover:underline">Back to sign in</Link></p>
        </form>
      )}
    </AuthShell>
  );
}
