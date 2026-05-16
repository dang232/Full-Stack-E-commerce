import { useRef, useState, type Dispatch, type SetStateAction } from "react";

/**
 * Like `useState`, but resets to `initial` whenever `signal` changes.
 *
 * Use this for purely visual derived state (pagination cursors, "show more"
 * counters, transient draft values) that needs to reset when filters or
 * route params change. Replaces the common useEffect anti-pattern:
 *
 *   useEffect(() => setPageSize(20), [query, sort, ...filters]);
 *
 * The reset happens during render — there is no extra commit and no flash.
 */
export function useResettableState<T>(
  initial: T,
  signal: string | number,
): [T, Dispatch<SetStateAction<T>>] {
  const lastSignal = useRef(signal);
  const [value, setValue] = useState<T>(initial);

  if (lastSignal.current !== signal) {
    lastSignal.current = signal;
    if (value !== initial) setValue(initial);
  }

  return [value, setValue];
}
