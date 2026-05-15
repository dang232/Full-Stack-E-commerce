import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { Bell, Check } from "lucide-react";
import { motion, AnimatePresence } from "motion/react";
import { useAuth } from "../hooks/use-auth";
import { useNotifications } from "../hooks/use-notifications";
import type { Notification } from "../types/api";

function relativeTime(iso?: string | null): string {
  if (!iso) return "";
  const then = Date.parse(iso);
  if (Number.isNaN(then)) return "";
  const diffMs = Date.now() - then;
  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 1) return "vừa xong";
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} ngày trước`;
  return new Date(then).toLocaleDateString("vi-VN");
}

export function NotificationBell() {
  const navigate = useNavigate();
  const { authenticated } = useAuth();
  const { items, unreadCount, isLoading, markRead } = useNotifications();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const handleSelect = (n: Notification) => {
    if (n.read === false) markRead(n.id);
    setOpen(false);
    if (n.deepLink) {
      try {
        const url = new URL(n.deepLink, window.location.origin);
        if (url.origin === window.location.origin) {
          navigate(url.pathname + url.search + url.hash);
          return;
        }
        window.location.href = n.deepLink;
      } catch {
        navigate(n.deepLink);
      }
    }
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        onClick={() => {
          if (!authenticated) {
            navigate("/login?next=%2Fprofile");
            return;
          }
          setOpen((v) => !v);
        }}
        className="relative p-2 text-white rounded-lg hover:bg-white/10 transition-colors"
        title="Thông báo"
        aria-label="Thông báo"
        aria-expanded={open}
        aria-haspopup="menu"
      >
        <Bell size={22} />
        {unreadCount > 0 && (
          <span
            className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-0.5 rounded-full text-white text-[10px] font-bold flex items-center justify-center"
            style={{ background: "#EF4444" }}
          >
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      <AnimatePresence>
        {open && authenticated && (
          <motion.div
            role="menu"
            initial={{ opacity: 0, y: -6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.98 }}
            transition={{ duration: 0.15 }}
            className="absolute right-0 mt-2 w-80 sm:w-96 bg-white rounded-2xl shadow-xl border border-gray-100 overflow-hidden z-50"
          >
            <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
              <h3 className="font-semibold text-sm text-gray-800">Thông báo</h3>
              {unreadCount > 0 && (
                <span className="text-[11px] text-gray-500">{unreadCount} chưa đọc</span>
              )}
            </div>

            <div className="max-h-96 overflow-y-auto">
              {isLoading && (
                <p className="px-4 py-6 text-sm text-gray-400 text-center">Đang tải...</p>
              )}
              {!isLoading && items.length === 0 && (
                <div className="px-4 py-10 text-center">
                  <Bell size={32} className="mx-auto mb-3 text-gray-200" />
                  <p className="text-sm text-gray-400">Chưa có thông báo nào</p>
                </div>
              )}
              {!isLoading && items.length > 0 && (
                <ul className="divide-y divide-gray-50">
                  {items.map((n) => (
                    <li key={n.id}>
                      <button
                        onClick={() => handleSelect(n)}
                        className="w-full px-4 py-3 flex gap-3 text-left hover:bg-gray-50 transition-colors"
                      >
                        <div
                          className="w-8 h-8 rounded-full flex items-center justify-center shrink-0 mt-0.5"
                          style={{
                            background: n.read === false ? "rgba(0,191,179,0.12)" : "#f3f4f6",
                            color: n.read === false ? "#00BFB3" : "#9ca3af",
                          }}
                        >
                          {n.read === false ? <Bell size={14} /> : <Check size={14} />}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-start justify-between gap-2">
                            <p
                              className="text-sm font-medium text-gray-800 line-clamp-1"
                              style={{ fontWeight: n.read === false ? 600 : 500 }}
                            >
                              {n.title ?? n.type ?? "Thông báo"}
                            </p>
                            {n.read === false && (
                              <span
                                className="w-2 h-2 rounded-full shrink-0 mt-1.5"
                                style={{ background: "#FF6200" }}
                              />
                            )}
                          </div>
                          {n.body && (
                            <p className="text-xs text-gray-500 line-clamp-2 mt-0.5">{n.body}</p>
                          )}
                          {n.createdAt && (
                            <p className="text-[11px] text-gray-400 mt-1">
                              {relativeTime(n.createdAt)}
                            </p>
                          )}
                        </div>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="px-4 py-2.5 border-t border-gray-100 text-center">
              <button
                onClick={() => {
                  setOpen(false);
                  navigate("/profile");
                }}
                className="text-xs font-medium"
                style={{ color: "#00BFB3" }}
              >
                Xem tất cả thông báo
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
