import { useEffect } from "react";

/**
 * Run `handler` whenever the user presses Escape, while `enabled` is true.
 * Intended for closing dialogs / popovers — keep it scoped to a component's
 * `open` state so we don't fight other Escape handlers further up the tree.
 */
export function useEscapeKey(enabled: boolean, handler: () => void): void {
  useEffect(() => {
    if (!enabled) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") handler();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [enabled, handler]);
}
