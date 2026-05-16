import { X } from "lucide-react";
import { motion } from "motion/react";
import { useState } from "react";
import { toast } from "sonner";

import { useEscapeKey } from "../hooks/use-escape-key";

export interface FormField {
  key: string;
  label: string;
  placeholder?: string;
  type?: "text" | "number" | "textarea";
  required?: boolean;
  helper?: string;
}

interface FormDialogProps {
  open: boolean;
  title: string;
  description?: string;
  submitLabel: string;
  submitColor?: string;
  fields: FormField[];
  onClose: () => void;
  onSubmit: (values: Record<string, string>) => void;
  isSubmitting?: boolean;
}

function emptyValues(fields: FormField[]): Record<string, string> {
  const initial: Record<string, string> = {};
  for (const f of fields) initial[f.key] = "";
  return initial;
}

/**
 * Generic form dialog. Caller is expected to remount it via `key` (or by only
 * rendering it when `open === true`) when fields or initial values change —
 * the previous useEffect-based reset has been removed because parents already
 * control mount lifecycle through the modal pattern.
 */
export function FormDialog({
  open,
  title,
  description,
  submitLabel,
  submitColor = "#FF6200",
  fields,
  onClose,
  onSubmit,
  isSubmitting = false,
}: FormDialogProps) {
  const [values, setValues] = useState<Record<string, string>>(() => emptyValues(fields));

  useEscapeKey(open && !isSubmitting, onClose);

  if (!open) return null;

  const handleSubmit = () => {
    for (const field of fields) {
      const required = field.required ?? true;
      const v = (values[field.key] ?? "").trim();
      if (required && !v) {
        toast.error(`Vui lòng nhập ${field.label.toLowerCase()}`);
        return;
      }
      if (field.type === "number" && v) {
        const n = Number(v.replace(/\D/g, ""));
        if (!Number.isFinite(n) || n < 0) {
          toast.error(`${field.label} không hợp lệ`);
          return;
        }
      }
    }
    const trimmed: Record<string, string> = {};
    Object.entries(values).forEach(([k, v]) => {
      trimmed[k] = (v ?? "").trim();
    });
    onSubmit(trimmed);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ background: "rgba(0,0,0,0.5)" }}
      onClick={onClose}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className="bg-white rounded-2xl w-full max-w-md shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 className="text-lg font-bold text-gray-800">{title}</h3>
          <button
            onClick={onClose}
            disabled={isSubmitting}
            className="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 flex items-center justify-center disabled:opacity-50"
          >
            <X size={16} />
          </button>
        </div>

        <div className="p-6 space-y-4">
          {description ? <p className="text-sm text-gray-500">{description}</p> : null}
          {fields.map((field) => (
            <div key={field.key}>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                {field.label}
                {field.required === false ? (
                  <span className="text-gray-400 font-normal"> (tuỳ chọn)</span>
                ) : null}
              </label>
              {field.type === "textarea" ? (
                <textarea
                  value={values[field.key] ?? ""}
                  onChange={(e) => setValues((prev) => ({ ...prev, [field.key]: e.target.value }))}
                  rows={3}
                  placeholder={field.placeholder}
                  className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3] resize-none"
                  autoFocus={fields.indexOf(field) === 0}
                />
              ) : (
                <input
                  type={field.type === "number" ? "text" : "text"}
                  inputMode={field.type === "number" ? "numeric" : undefined}
                  value={values[field.key] ?? ""}
                  onChange={(e) => setValues((prev) => ({ ...prev, [field.key]: e.target.value }))}
                  placeholder={field.placeholder}
                  className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm outline-none focus:border-[#00BFB3]"
                  autoFocus={fields.indexOf(field) === 0}
                />
              )}
              {field.helper ? (
                <p className="text-[11px] text-gray-400 mt-1">{field.helper}</p>
              ) : null}
            </div>
          ))}
        </div>

        <div className="px-6 py-4 border-t border-gray-100 flex gap-3">
          <button
            onClick={onClose}
            disabled={isSubmitting}
            className="flex-1 py-2.5 rounded-xl border border-gray-200 text-sm font-semibold text-gray-600 disabled:opacity-50"
          >
            Huỷ
          </button>
          <button
            onClick={handleSubmit}
            disabled={isSubmitting}
            className="flex-1 py-2.5 rounded-xl text-white text-sm font-semibold disabled:opacity-50"
            style={{ background: submitColor }}
          >
            {isSubmitting ? "Đang xử lý..." : submitLabel}
          </button>
        </div>
      </motion.div>
    </div>
  );
}
