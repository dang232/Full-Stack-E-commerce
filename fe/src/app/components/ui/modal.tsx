import { type ReactNode, type MouseEvent } from "react";
import { motion, AnimatePresence } from "motion/react";
import { X } from "lucide-react";
import { useEscapeKey } from "../../hooks/use-escape-key";
import { modalBackdropBg } from "../../lib/ui/theme";

interface ModalProps {
  open: boolean;
  onClose: () => void;
  /** Disable the escape-key + backdrop click handlers (e.g. while submitting). */
  dismissDisabled?: boolean;
  /** Optional title rendered in the modal header; pass `null` to omit the header entirely. */
  title?: ReactNode;
  /** Optional subtitle / metadata under the title. */
  subtitle?: ReactNode;
  /** When true the close icon is hidden (useful when only programmatic dismiss is allowed). */
  hideCloseButton?: boolean;
  /** Width preset; defaults to "md". */
  size?: "sm" | "md" | "lg";
  /** Footer content (typically Cancel/Confirm buttons). */
  footer?: ReactNode;
  children?: ReactNode;
  /** Backdrop click handler override; defaults to `onClose`. */
  onBackdropClick?: (e: MouseEvent<HTMLDivElement>) => void;
}

const SIZE_CLASS: Record<NonNullable<ModalProps["size"]>, string> = {
  sm: "max-w-sm",
  md: "max-w-md",
  lg: "max-w-2xl",
};

/**
 * Single modal/dialog primitive used by every confirmation, form, and detail dialog.
 *
 * Replaces five hand-rolled implementations (TrackingModal, ReturnModal, ShipDialog,
 * CouponDialog, the inline modal in seller-product-modal). Handles backdrop, escape
 * key, click-outside, motion, and the close button so individual dialogs only own
 * their content.
 */
export function Modal({
  open,
  onClose,
  dismissDisabled = false,
  title,
  subtitle,
  hideCloseButton = false,
  size = "md",
  footer,
  children,
  onBackdropClick,
}: ModalProps) {
  useEscapeKey(open && !dismissDisabled, onClose);

  const handleBackdrop = (e: MouseEvent<HTMLDivElement>) => {
    if (dismissDisabled) return;
    if (onBackdropClick) onBackdropClick(e);
    else onClose();
  };

  return (
    <AnimatePresence>
      {open && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          style={{ background: modalBackdropBg }}
          onClick={handleBackdrop}
          role="presentation"
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            transition={{ duration: 0.18 }}
            className={`bg-white rounded-2xl w-full shadow-2xl ${SIZE_CLASS[size]}`}
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            {(title || !hideCloseButton) && (
              <div className="flex items-start justify-between gap-3 px-6 py-4 border-b border-gray-100">
                <div className="flex-1 min-w-0">
                  {title && <h3 className="text-lg font-bold text-gray-800">{title}</h3>}
                  {subtitle && <div className="text-xs text-gray-500 mt-0.5">{subtitle}</div>}
                </div>
                {!hideCloseButton && (
                  <button
                    type="button"
                    onClick={onClose}
                    disabled={dismissDisabled}
                    className="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center disabled:opacity-50 shrink-0"
                    aria-label="Đóng"
                  >
                    <X size={16} />
                  </button>
                )}
              </div>
            )}

            <div className="p-6">{children}</div>

            {footer && (
              <div className="px-6 py-4 border-t border-gray-100 flex gap-3">{footer}</div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
