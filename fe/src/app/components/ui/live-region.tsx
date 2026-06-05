import { useEffect, useState } from "react";

interface LiveRegionProps {
  message: string;
  politeness?: "polite" | "assertive";
}

/**
 * Announces dynamic content changes to screen readers.
 * Visually hidden but accessible via aria-live.
 */
export function LiveRegion({ message, politeness = "polite" }: LiveRegionProps) {
  const [announced, setAnnounced] = useState("");

  useEffect(() => {
    if (message) {
      setAnnounced("");
      const timer = setTimeout(() => setAnnounced(message), 100);
      return () => clearTimeout(timer);
    }
  }, [message]);

  return (
    <div role="status" aria-live={politeness} aria-atomic="true" className="sr-only">
      {announced}
    </div>
  );
}
