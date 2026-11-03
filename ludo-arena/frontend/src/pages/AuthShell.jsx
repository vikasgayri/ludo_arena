import { Link } from 'react-router-dom';
import { Dices } from 'lucide-react';

export default function AuthShell({ title, subtitle, children }) {
  return (
    <div className="flex min-h-full items-center justify-center p-4">
      <div className="w-full max-w-md">
        <Link to="/" className="mb-6 flex items-center justify-center gap-2 text-xl font-extrabold">
          <Dices className="h-7 w-7 text-brand-400" aria-hidden />LUDO<span className="text-brand-400">ARENA</span>
        </Link>
        <div className="card">
          <h1 className="text-xl font-bold">{title}</h1>
          {subtitle && <p className="mb-5 mt-1 text-sm text-slate-400">{subtitle}</p>}
          {children}
        </div>
      </div>
    </div>
  );
}
