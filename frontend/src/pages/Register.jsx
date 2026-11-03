import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { errorMessage, fieldErrors as getFieldErrors } from '../lib/api';
import { toast } from '../store/toastStore';
import { Field, Spinner } from '../components/ui';
import AuthShell from './AuthShell';

export const PASSWORD_RE = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;
export const PASSWORD_HINT = '8-72 characters with at least one letter and one digit';

export default function Register() {
  const register = useAuthStore((s) => s.register);
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [busy, setBusy] = useState(false);

  const validate = () => {
    const e = {};
    if (form.name.trim().length < 2 || form.name.trim().length > 30) e.name = 'Name must be 2-30 characters';
    if (!/^\S+@\S+\.\S+$/.test(form.email)) e.email = 'Enter a valid email address';
    if (!PASSWORD_RE.test(form.password)) e.password = `Password must be ${PASSWORD_HINT}`;
    if (form.confirm !== form.password) e.confirm = 'Passwords do not match';
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
      await register(form.name.trim(), form.email.trim(), form.password);
      toast.success('Account created — welcome to Ludo Arena!');
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setErrors(getFieldErrors(err));
      setFormError(errorMessage(err, 'Could not create the account.'));
    } finally {
      setBusy(false);
    }
  };

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  return (
    <AuthShell title="Create your account" subtitle="Free to play. Virtual coins only.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        {formError && <p role="alert" className="rounded-xl bg-red-500/10 px-3 py-2 text-sm text-red-300">{formError}</p>}
        <Field label="Display name" htmlFor="name" error={errors.name}><input id="name" className="input" autoComplete="nickname" value={form.name} onChange={set('name')} /></Field>
        <Field label="Email" htmlFor="email" error={errors.email}><input id="email" type="email" className="input" autoComplete="email" value={form.email} onChange={set('email')} /></Field>
        <Field label="Password" htmlFor="password" error={errors.password}>
          <input id="password" type="password" className="input" autoComplete="new-password" value={form.password} onChange={set('password')} />
          <p className="mt-1 text-xs text-slate-500">{PASSWORD_HINT}</p>
        </Field>
        <Field label="Confirm password" htmlFor="confirm" error={errors.confirm}><input id="confirm" type="password" className="input" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} /></Field>
        <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner className="h-4 w-4" />}Create account</button>
      </form>
      <p className="mt-5 text-center text-sm text-slate-400">Already registered? <Link to="/login" className="text-brand-400 hover:underline">Sign in</Link></p>
    </AuthShell>
  );
}
