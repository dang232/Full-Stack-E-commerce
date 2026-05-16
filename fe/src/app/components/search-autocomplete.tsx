import { Search } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";

interface SearchAutocompleteProps {
  value: string;
  suggestions: string[];
  onValueChange: (next: string) => void;
  onSubmit: (query: string) => void;
  placeholder?: string;
  className?: string;
}

/**
 * Generic autocomplete input. The data source (search suggestions, recent
 * queries, popular tags, ...) is the parent's responsibility — this component
 * only renders the input + dropdown and dispatches submissions. Keeps the UI
 * reusable across surfaces that have different suggestion sources.
 */
export function SearchAutocomplete({
  value,
  suggestions,
  onValueChange,
  onSubmit,
  placeholder = "Tìm kiếm sản phẩm...",
  className,
}: SearchAutocompleteProps) {
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const listboxId = useId();

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, [open]);

  // Reset highlight whenever the suggestion list shape changes.
  useEffect(() => {
    setActiveIndex(-1);
  }, [suggestions]);

  const submit = (q: string) => {
    if (!q.trim()) return;
    onSubmit(q.trim());
    setOpen(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (suggestions.length === 0 || !open) {
      if (e.key === "Enter") {
        e.preventDefault();
        submit(value);
      }
      return;
    }
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActiveIndex((prev) => (prev + 1) % suggestions.length);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIndex((prev) => (prev <= 0 ? suggestions.length - 1 : prev - 1));
    } else if (e.key === "Enter") {
      e.preventDefault();
      submit(activeIndex >= 0 ? suggestions[activeIndex] : value);
    } else if (e.key === "Escape") {
      setOpen(false);
    }
  };

  const showDropdown = open && suggestions.length > 0;

  return (
    <div ref={containerRef} className={`relative ${className ?? ""}`}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit(value);
        }}
        className="flex w-full rounded-xl overflow-hidden shadow-md"
      >
        <input
          type="text"
          value={value}
          onChange={(e) => {
            onValueChange(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          className="flex-1 px-4 py-2.5 text-sm bg-white text-gray-800 outline-none placeholder:text-gray-400"
          role="combobox"
          aria-expanded={showDropdown}
          aria-controls={listboxId}
          aria-autocomplete="list"
          aria-activedescendant={
            activeIndex >= 0 ? `${listboxId}-option-${activeIndex}` : undefined
          }
        />
        <button
          type="submit"
          className="px-5 py-2.5 font-semibold text-sm text-white transition-colors"
          style={{ background: "#FF6200" }}
          aria-label="Tìm kiếm"
        >
          <Search size={18} />
        </button>
      </form>

      {showDropdown ? (
        <ul
          id={listboxId}
          role="listbox"
          className="absolute left-0 right-0 top-full mt-1 max-h-80 overflow-y-auto bg-white rounded-xl shadow-lg border border-gray-100 z-50"
        >
          {suggestions.map((suggestion, idx) => (
            <li
              key={suggestion}
              id={`${listboxId}-option-${idx}`}
              role="option"
              aria-selected={idx === activeIndex}
            >
              <button
                type="button"
                onMouseDown={(e) => {
                  // mousedown beats blur, so the suggestion fires before the
                  // input loses focus and the dropdown closes.
                  e.preventDefault();
                  submit(suggestion);
                }}
                onMouseEnter={() => setActiveIndex(idx)}
                className={`w-full px-4 py-2 text-left text-sm flex items-center gap-2 ${
                  idx === activeIndex ? "bg-gray-100" : "hover:bg-gray-50"
                } text-gray-800`}
              >
                <Search size={14} className="text-gray-400" />
                <span className="truncate">{suggestion}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
