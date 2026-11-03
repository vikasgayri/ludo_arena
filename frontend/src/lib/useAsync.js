import { useCallback, useEffect, useRef, useState } from 'react';
import { errorMessage } from './api';

/** Runs an async loader on mount / when deps change, with loading + error state and a reload(). */
export function useAsync(loader, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  const seq = useRef(0);

  const run = useCallback(async () => {
    const id = ++seq.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const data = await loaderRef.current();
      if (id === seq.current) setState({ data, loading: false, error: null });
      return data;
    } catch (e) {
      if (id === seq.current) setState({ data: null, loading: false, error: errorMessage(e) });
      return null;
    }
  }, []);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { run(); }, deps);
  useEffect(() => () => { seq.current += 1; }, []);

  return { ...state, reload: run };
}
