import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { errorMessage, fieldErrors as getFieldErrors } from '../lib/api';
import { toast } from '../store/toastStore';
import { Field, Spinner } from '../components/ui';
import AuthShell from './AuthShell';

export default function Login() {
  const login = useAuthStore((s) => s.login);
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [busy, setBusy] = useState(false);

  const validate = () => {
    const e = {};
    if (!/^\S+@\S+\.\S+$/.test(form.email)) e.email = 'Enter a valid email address';
    if (!form.password) e.password = 'Password is required';
    return e;
  };

  const submit = async (ev) => {
    ev.preventDefault();
    const e = validate();
    setErrors(e);
    setFormError(null);
    if (Object.keys(e).length) return;
    setBusy(true);
    try {
      await login(form.email.trim(), form.password);
      toast.success('Welcome back!');
      navigate(location.state?.from || '/dashboard', { replace: true });
    } catch (err) {
      setErrors(getFieldErrors(err));
      setFormError(errorMessage(err, 'Could not sign in.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell title="Welcome back" subtitle="Sign in to continue playing">
      <form onSubmit={submit} className="space-y-4" noValidate>
        {formError && <p role="alert" className="rounded-xl bg-red-500/10 px-3 py-2 text-sm text-red-300">{formError}</p>}
        <Field label="Email" htmlFor="email" error={errors.email}>
          <input id="email" type="email" autoComplete="email" className="input" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </Field>
        <Field label="Password" htmlFor="password" error={errors.password}>
          <input id="password" type="password" autoComplete="current-password" className="input" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </Field>
        <div className="text-right text-sm"><Link to="/forgot-password" className="text-brand-400 hover:underline">Forgot password?</Link></div>
        <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner className="h-4 w-4" />}Sign in</button>
      </form>
      <p className="mt-5 text-center text-sm text-slate-400">New here? <Link to="/register" className="text-brand-400 hover:underline">Create an account</Link></p>
    </AuthShell>
  );
}
