import { useEffect, useRef, useState } from 'react';
import { MessageCircle, Send } from 'lucide-react';
import { COLOR_HEX } from '../lib/format';

export const QUICK_MESSAGES = ['Good luck! 🍀', 'Nice move! 👏', 'Oops 😅', 'Hurry up ⏱️', 'GG 🤝', '😂'];
const MAX = 200;

function time(at) {
  try { return new Date(at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }); } catch { return ''; }
}

/** In-match chat. Text is rendered as plain text (React escapes it); the server validates and rate limits. */
export default function ChatPanel({ messages = [], myId, onSend, disabled = false, className = '', bare = false }) {
  const [text, setText] = useState('');
  const listRef = useRef(null);

  useEffect(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [messages.length]);

  const send = (value) => {
    const body = String(value ?? text).trim();
    if (!body || disabled) return;
    if (onSend?.(body) !== false && value === undefined) setText('');
  };

  return (
    <section className={`${bare ? '' : 'card p-4'} flex flex-col ${className}`} aria-label="Chat" data-testid="chat-panel">
      {!bare && <h3 className="mb-2 flex items-center gap-2 text-sm font-bold"><MessageCircle className="h-4 w-4" aria-hidden />Chat</h3>}
      <ol ref={listRef} className="mb-3 max-h-56 min-h-[6rem] space-y-2 overflow-y-auto pr-1 text-sm" aria-live="polite" data-testid="chat-list">
        {messages.length === 0 && <li className="text-slate-500">Say hi to your opponents 👋</li>}
        {messages.map((m) => {
          const mine = m.userId === myId;
          return (
            <li key={m.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[85%] rounded-2xl px-3 py-1.5 ${mine ? 'rounded-br-sm bg-brand-500/25 text-white' : 'rounded-bl-sm bg-white/5 text-slate-100'}`}>
                {!mine && (
                  <p className="flex items-center gap-1 text-[11px] font-semibold text-slate-300">
                    <span className="h-2 w-2 rounded-full" style={{ background: COLOR_HEX[m.color] || '#94a3b8' }} aria-hidden />{m.name}
                  </p>
                )}
                <p className="whitespace-pre-wrap break-words">{m.text}</p>
                <p className="text-right text-[10px] text-slate-400">{time(m.at)}</p>
              </div>
            </li>
          );
        })}
      </ol>
      <div className="mb-2 flex flex-wrap gap-1.5">
        {QUICK_MESSAGES.map((q) => (
          <button key={q} type="button" className="rounded-full bg-white/5 px-2.5 py-1 text-xs text-slate-200 hover:bg-white/10 disabled:opacity-50" onClick={() => send(q)} disabled={disabled}>{q}</button>
        ))}
      </div>
      <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); send(); }}>
        <label htmlFor="chat-input" className="sr-only">Message</label>
        <input
          id="chat-input"
          className="input flex-1"
          value={text}
          onChange={(e) => setText(e.target.value.slice(0, MAX))}
          placeholder={disabled ? 'Chat unavailable' : 'Type a message…'}
          maxLength={MAX}
          disabled={disabled}
          autoComplete="off"
          enterKeyHint="send"
        />
        <button type="submit" className="btn-primary px-3" disabled={disabled || !text.trim()} aria-label="Send message"><Send className="h-4 w-4" aria-hidden /></button>
      </form>
    </section>
  );
}
