import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';

export default function Modal({ open, title, onClose, children, footer, size = 'md' }) {
  const ref = useRef(null);
  const sizes = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-2xl' };

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => { if (e.key === 'Escape') onClose?.(); };
    document.addEventListener('keydown', onKey);
    const prev = document.activeElement;
    ref.current?.focus();
    return () => { document.removeEventListener('keydown', onKey); prev?.focus?.(); };
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/70 p-4 sm:items-center" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose?.(); }}>
      <div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-label={title} className={`card w-full ${sizes[size]} animate-pop`}>
        <div className="mb-4 flex items-start justify-between gap-4">
          <h2 className="text-lg font-bold">{title}</h2>
          <button className="btn-ghost -mr-2 -mt-1 p-1.5" onClick={onClose} aria-label="Close dialog"><X className="h-5 w-5" /></button>
        </div>
        <div>{children}</div>
        {footer && <div className="mt-5 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  );
}

export function ConfirmModal({ open, title, message, confirmLabel = 'Confirm', danger, busy, onConfirm, onClose }) {
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      size="sm"
      footer={(
        <>
          <button className="btn-secondary" onClick={onClose} disabled={busy}>Cancel</button>
          <button className={danger ? 'btn-danger' : 'btn-primary'} onClick={onConfirm} disabled={busy}>{busy ? 'Working…' : confirmLabel}</button>
        </>
      )}
    >
      <p className="text-sm text-slate-300">{message}</p>
    </Modal>
  );
}
