import {
  Loader2,
  MessageCircle,
  Search,
  Send,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";

import { useAuth } from "../hooks/use-auth";
import {
  useMarkThreadRead,
  useMessages,
  useSendMessage,
} from "../hooks/use-messages";
import { useThreads } from "../hooks/use-threads";
import { openThread } from "../lib/api/endpoints/messaging";
import type { ChatMessage, MessageThreadSummary } from "../lib/api/endpoints/messaging";
import { ApiError } from "../lib/api/envelope";

function relativeTime(iso: string | undefined): string {
  if (!iso) return "";
  const then = new Date(iso).getTime();
  const now = Date.now();
  const diff = Math.max(0, now - then);
  if (diff < 60_000) return "vừa xong";
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} phút trước`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} giờ trước`;
  return new Date(iso).toLocaleDateString("vi-VN");
}

function ThreadList({
  threads,
  selectedId,
  onSelect,
  filter,
  onFilterChange,
  isLoading,
}: {
  threads: MessageThreadSummary[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  filter: string;
  onFilterChange: (value: string) => void;
  isLoading: boolean;
}) {
  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return threads;
    return threads.filter((t) => {
      const haystack = [t.otherPartyId, t.lastMessageBody ?? "", t.productId ?? ""]
        .join(" ")
        .toLowerCase();
      return haystack.includes(q);
    });
  }, [threads, filter]);

  return (
    <aside className="flex flex-col w-full md:w-80 border-r border-gray-100 bg-white">
      <div className="p-4 border-b border-gray-100">
        <h2 className="font-bold text-base text-gray-800 flex items-center gap-2">
          <MessageCircle size={18} style={{ color: "#00BFB3" }} /> Tin nhắn
        </h2>
        <label className="mt-3 flex items-center gap-2 px-3 py-2 rounded-xl bg-gray-50 border border-gray-100">
          <Search size={14} className="text-gray-400" />
          <input
            value={filter}
            onChange={(e) => onFilterChange(e.target.value)}
            placeholder="Tìm cuộc trò chuyện"
            className="flex-1 bg-transparent text-sm outline-none"
          />
        </label>
      </div>

      <div className="flex-1 overflow-y-auto">
        {isLoading ? (
          <div className="p-6 flex items-center justify-center text-gray-400 text-sm gap-2">
            <Loader2 size={14} className="animate-spin" /> Đang tải...
          </div>
        ) : filtered.length === 0 ? (
          <p className="p-6 text-sm text-gray-400 text-center">Chưa có cuộc trò chuyện nào.</p>
        ) : (
          filtered.map((t) => {
            const active = t.id === selectedId;
            return (
              <button
                key={t.id}
                onClick={() => onSelect(t.id)}
                className={`w-full text-left px-4 py-3 border-b border-gray-100 transition-colors ${
                  active ? "bg-[rgba(0,191,179,0.08)]" : "hover:bg-gray-50"
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-semibold text-sm text-gray-800 truncate">
                    {t.otherPartyId}
                  </span>
                  <span className="text-[11px] text-gray-400 shrink-0">
                    {relativeTime(t.lastMessageAt)}
                  </span>
                </div>
                <div className="flex items-center justify-between gap-2 mt-1">
                  <span className="text-xs text-gray-500 truncate flex-1">
                    {t.lastMessageBody ?? "Bắt đầu cuộc trò chuyện"}
                  </span>
                  {t.unreadCount > 0 ? (
                    <span
                      className="ml-2 min-w-[18px] h-[18px] px-1 rounded-full text-[10px] font-bold text-white flex items-center justify-center"
                      style={{ background: "#FF6200" }}
                    >
                      {t.unreadCount}
                    </span>
                  ) : null}
                </div>
              </button>
            );
          })
        )}
      </div>
    </aside>
  );
}

function MessageBubble({ message, isMine }: { message: ChatMessage; isMine: boolean }) {
  return (
    <div className={`flex ${isMine ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[75%] px-3 py-2 rounded-2xl text-sm ${
          isMine ? "text-white" : "text-gray-800 bg-gray-100"
        }`}
        style={isMine ? { background: "#00BFB3" } : undefined}
      >
        <p className="whitespace-pre-wrap break-words">{message.body}</p>
        <p className={`text-[10px] mt-1 ${isMine ? "text-white/80" : "text-gray-400"}`}>
          {new Date(message.sentAt).toLocaleTimeString("vi-VN", {
            hour: "2-digit",
            minute: "2-digit",
          })}
        </p>
      </div>
    </div>
  );
}

function MessagePane({
  threadId,
  callerId,
}: {
  threadId: string | null;
  callerId: string | undefined;
}) {
  const messagesQuery = useMessages(threadId ?? undefined);
  const sendMessage = useSendMessage(threadId ?? undefined);
  const markRead = useMarkThreadRead();
  const [draft, setDraft] = useState("");
  const scrollRef = useRef<HTMLDivElement | null>(null);

  // Most-recent first from BE; reverse for natural chat order (oldest top, newest bottom).
  const ordered = useMemo(
    () => [...(messagesQuery.data?.content ?? [])].reverse(),
    [messagesQuery.data?.content],
  );

  // Mark thread as read whenever the user opens it (idempotent on the server).
  useEffect(() => {
    if (!threadId) return;
    markRead.mutate(threadId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [threadId]);

  // Auto-scroll to the latest message when the cache changes.
  useEffect(() => {
    if (!scrollRef.current) return;
    scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [ordered.length]);

  if (!threadId) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400 text-sm bg-gray-50">
        Chọn một cuộc trò chuyện để bắt đầu.
      </div>
    );
  }

  const submit = () => {
    const trimmed = draft.trim();
    if (!trimmed) return;
    setDraft("");
    sendMessage.mutate(
      { body: trimmed },
      {
        onError: (err) =>
          toast.error(err instanceof ApiError ? err.message : "Không gửi được tin nhắn"),
      },
    );
  };

  return (
    <section className="flex flex-col flex-1 bg-gray-50">
      <div className="px-5 py-3 bg-white border-b border-gray-100">
        <p className="text-xs text-gray-400">Đang nhắn với</p>
        <p className="font-semibold text-sm text-gray-800">{messageHeader(messagesQuery.data?.content[0])}</p>
      </div>

      <div ref={scrollRef} className="flex-1 overflow-y-auto px-5 py-4 space-y-2">
        {messagesQuery.isLoading ? (
          <p className="text-center text-sm text-gray-400">Đang tải tin nhắn...</p>
        ) : ordered.length === 0 ? (
          <p className="text-center text-sm text-gray-400">Hãy gửi tin nhắn đầu tiên.</p>
        ) : (
          ordered.map((m) => (
            <MessageBubble
              key={m.id}
              message={m}
              isMine={m.senderId === callerId || m.senderId === "__pending__"}
            />
          ))
        )}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
        className="px-4 py-3 bg-white border-t border-gray-100 flex items-end gap-2"
      >
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              submit();
            }
          }}
          rows={1}
          placeholder="Nhập tin nhắn..."
          className="flex-1 resize-none rounded-2xl border border-gray-200 px-4 py-2 text-sm focus:outline-none focus:border-[#00BFB3]"
          maxLength={4000}
        />
        <button
          type="submit"
          disabled={!draft.trim() || sendMessage.isPending}
          className="px-4 py-2 rounded-2xl text-white text-sm font-medium flex items-center gap-2 disabled:opacity-60"
          style={{ background: "#00BFB3" }}
        >
          {sendMessage.isPending ? (
            <Loader2 size={14} className="animate-spin" />
          ) : (
            <Send size={14} />
          )}
          Gửi
        </button>
      </form>
    </section>
  );
}

function messageHeader(latest: ChatMessage | undefined): string {
  return latest?.threadId ? `Cuộc trò chuyện ${latest.threadId.slice(0, 8)}` : "Cuộc trò chuyện";
}

export function MessagesPage() {
  const navigate = useNavigate();
  const { ready, authenticated, login, subject } = useAuth();
  const [params, setParams] = useSearchParams();
  const threads = useThreads();
  const [filter, setFilter] = useState("");
  const [resolving, setResolving] = useState(false);

  const requestedThreadId = params.get("thread");
  const requestedRecipient = params.get("with");
  const requestedProduct = params.get("product");

  // If the URL says ?with=<userId>[&product=<id>], find or create that thread
  // before rendering the pane.
  useEffect(() => {
    if (!ready || !authenticated || !requestedRecipient || requestedThreadId) return;
    let cancelled = false;
    setResolving(true);
    openThread({
      recipientId: requestedRecipient,
      productId: requestedProduct ?? null,
    })
      .then((thread) => {
        if (cancelled) return;
        // Strip the auto-resolve params and stash the thread id so a refresh
        // doesn't re-trigger the resolve.
        const next = new URLSearchParams(params);
        next.delete("with");
        next.delete("product");
        next.set("thread", thread.id);
        setParams(next, { replace: true });
      })
      .catch((err) => {
        toast.error(err instanceof ApiError ? err.message : "Không mở được cuộc trò chuyện");
      })
      .finally(() => {
        if (!cancelled) setResolving(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, authenticated, requestedRecipient, requestedThreadId, requestedProduct]);

  const selectedId =
    requestedThreadId ??
    threads.items.find((t) => !!t.id)?.id ??
    null;

  const selectThread = (id: string) => {
    const next = new URLSearchParams(params);
    next.set("thread", id);
    setParams(next, { replace: false });
  };

  if (ready && !authenticated) {
    return (
      <div className="max-w-md mx-auto px-4 py-24 text-center">
        <p className="text-sm text-gray-500 mb-4">Đăng nhập để xem tin nhắn của bạn.</p>
        <button
          onClick={() => login("/messages")}
          className="px-4 py-2 rounded-xl text-white text-sm font-medium"
          style={{ background: "#FF6200" }}
        >
          Đăng nhập
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-xl font-bold text-gray-800">Tin nhắn</h1>
        <button
          onClick={() => navigate(-1)}
          className="text-xs text-gray-500 hover:text-gray-700"
        >
          Quay lại
        </button>
      </div>
      <div className="bg-white rounded-2xl shadow-sm overflow-hidden border border-gray-100 flex flex-col md:flex-row min-h-[70vh]">
        <ThreadList
          threads={threads.items}
          selectedId={selectedId}
          onSelect={selectThread}
          filter={filter}
          onFilterChange={setFilter}
          isLoading={threads.isLoading || resolving}
        />
        <MessagePane threadId={selectedId} callerId={subject} />
      </div>
    </div>
  );
}
