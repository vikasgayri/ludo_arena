import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';
import { useToastStore } from '../store/toastStore';

const ICONS = { success: CheckCircle2, error: AlertCircle, info: Info };
const TONES = {
  success: 'border-emerald-500/30 bg-emerald-950/90 text-emerald-100',
  error: 'border-red-500/30 bg-red-950/90 text-red-100',
  info: 'border-blue-500/30 bg-slate-900/95 text-blue-100',
};

export default function ToastContainer() {
  const toasts = useToastStore((s) => s.toasts);
  const dismiss = useToastStore((s) => s.dismiss);
  return (
    <div className="pointer-events-none fixed bottom-4 right-4 z-[60] flex w-[calc(100%-2rem)] max-w-sm flex-col gap-2" aria-live="polite">
      {toasts.map((t) => {
        const Icon = ICONS[t.type] || Info;
        return (
          <div key={t.id} role={t.type === 'error' ? 'alert' : 'status'} className={`pointer-events-auto flex animate-slideIn items-start gap-3 rounded-xl border p-3 text-sm shadow-xl ${TONES[t.type] || TONES.info}`}>
            <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            <p className="flex-1">{t.message}</p>
            <button onClick={() => dismiss(t.id)} aria-label="Dismiss notification" className="opacity-70 hover:opacity-100"><X className="h-4 w-4" /></button>
          </div>
        );
      })}
    </div>
  );
}
