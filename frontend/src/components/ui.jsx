import { AlertTriangle, ChevronLeft, ChevronRight, Inbox, Loader2 } from 'lucide-react';
import { avatarStyle } from '../lib/cosmetics';

export function Avatar({ avatar, name, size = 40, className = '' }) {
  const a = avatarStyle(avatar);
  return (
    <span
      className={`inline-flex shrink-0 items-center justify-center rounded-full ring-2 ring-white/10 ${className}`}
      style={{ width: size, height: size, background: a.bg, fontSize: size * 0.5 }}
      role="img"
      aria-label={name ? `${name} avatar` : 'avatar'}
    >
      {a.emoji}
    </span>
  );
}

export function Spinner({ className = '' }) {
  return <Loader2 className={`animate-spin ${className}`} aria-label="Loading" />;
}

export function EmptyState({ title = 'Nothing here yet', message, action, icon: Icon = Inbox }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center" data-testid="empty-state">
      <Icon className="h-9 w-9 text-slate-500" aria-hidden />
      <p className="font-semibold text-slate-200">{title}</p>
      {message && <p className="max-w-sm text-sm text-slate-400">{message}</p>}
      {action}
    </div>
  );
}

export function ErrorState({ message = 'Something went wrong.', onRetry }) {
  return (
    <div role="alert" className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-red-500/20 bg-red-500/5 px-6 py-8 text-center" data-testid="error-state">
      <AlertTriangle className="h-8 w-8 text-red-400" aria-hidden />
      <p className="text-sm text-red-200">{message}</p>
      {onRetry && <button className="btn-secondary" onClick={onRetry}>Try again</button>}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-400">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function StatCard({ label, value, hint, icon: Icon, tone = 'brand' }) {
  const tones = { brand: 'text-brand-400', green: 'text-emerald-400', red: 'text-red-400', yellow: 'text-yellow-300', blue: 'text-accent-400' };
  return (
    <div className="card flex items-center gap-4">
      {Icon && <div className={`rounded-xl bg-white/5 p-3 ${tones[tone] || tones.brand}`}><Icon className="h-6 w-6" aria-hidden /></div>}
      <div className="min-w-0">
        <p className="text-xs uppercase tracking-wide text-slate-400">{label}</p>
        <p className="truncate text-2xl font-bold">{value}</p>
        {hint && <p className="text-xs text-slate-500">{hint}</p>}
      </div>
    </div>
  );
}

const BADGE_TONES = {
  green: 'bg-emerald-500/15 text-emerald-300',
  red: 'bg-red-500/15 text-red-300',
  yellow: 'bg-yellow-500/15 text-yellow-200',
  blue: 'bg-blue-500/15 text-blue-300',
  purple: 'bg-brand-500/20 text-brand-400',
  gray: 'bg-white/10 text-slate-300',
};
export function Badge({ tone = 'gray', children }) {
  return <span className={`badge ${BADGE_TONES[tone] || BADGE_TONES.gray}`}>{children}</span>;
}

export function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <nav className="mt-4 flex items-center justify-between text-sm" aria-label="Pagination">
      <button className="btn-secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        <ChevronLeft className="h-4 w-4" aria-hidden /> Prev
      </button>
      <span className="text-slate-400">Page {page + 1} of {totalPages}</span>
      <button className="btn-secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next <ChevronRight className="h-4 w-4" aria-hidden />
      </button>
    </nav>
  );
}

export function Field({ label, error, children, htmlFor }) {
  return (
    <div>
      {label && <label className="label" htmlFor={htmlFor}>{label}</label>}
      {children}
      {error && <p className="field-error" role="alert">{error}</p>}
    </div>
  );
}
