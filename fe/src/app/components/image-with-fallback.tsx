import { useState, type ImgHTMLAttributes } from "react";
import { ImageOff } from "lucide-react";

type ImageFallbackProps = ImgHTMLAttributes<HTMLImageElement> & {
  /** Optional alternate URL to try before showing the placeholder. */
  fallbackSrc?: string;
  /** Override the placeholder (defaults to a neutral icon on a gray tile). */
  placeholder?: React.ReactNode;
};

/**
 * Drop-in replacement for `<img>` that swaps to a placeholder when the URL fails.
 * Tries `fallbackSrc` once if provided. Defers loading via `loading="lazy"` by default.
 */
export function ImageWithFallback({
  src,
  fallbackSrc,
  placeholder,
  alt,
  className,
  loading = "lazy",
  decoding = "async",
  onError,
  ...rest
}: ImageFallbackProps) {
  const [errored, setErrored] = useState(false);
  const [usingFallback, setUsingFallback] = useState(false);

  if (!src || errored) {
    return (
      <div
        className={`flex items-center justify-center bg-gray-100 text-gray-300 ${className ?? ""}`}
        aria-label={alt || "image unavailable"}
      >
        {placeholder ?? <ImageOff size={20} />}
      </div>
    );
  }

  const currentSrc = usingFallback ? fallbackSrc : src;

  return (
    <img
      {...rest}
      src={currentSrc}
      alt={alt}
      className={className}
      loading={loading}
      decoding={decoding}
      onError={(e) => {
        if (!usingFallback && fallbackSrc) {
          setUsingFallback(true);
        } else {
          setErrored(true);
        }
        onError?.(e);
      }}
    />
  );
}
