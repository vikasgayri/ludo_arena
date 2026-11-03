import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 p-6 text-center">
      <p className="text-6xl font-extrabold text-brand-400">404</p>
      <p className="text-slate-300">That page doesn't exist.</p>
      <Link to="/" className="btn-primary">Go home</Link>
    </div>
  );
}
