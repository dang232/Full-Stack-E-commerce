import { useEffect, useState } from "react";

/**
 * Returns `value` after it has stayed unchanged for `delayMs`. Useful for
 * debouncing inputs that drive expensive queries.
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
