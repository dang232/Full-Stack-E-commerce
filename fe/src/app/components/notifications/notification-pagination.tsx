import { useSearchParams } from "react-router";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";

interface NotificationPaginationProps {
  totalPages: number;
  currentPage: number;
}

export function NotificationPagination({ totalPages, currentPage }: NotificationPaginationProps) {
  const [, setParams] = useSearchParams();

  if (totalPages <= 1) return null;

  const goTo = (page: number) => {
    setParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set("page", String(page));
      return next;
    });
  };

  return (
    <div className="flex items-center justify-center gap-3 py-4">
      <button
        onClick={() => goTo(currentPage - 1)}
        disabled={currentPage <= 0}
        className="rounded-lg p-2 hover:bg-muted disabled:opacity-30 transition-colors"
      >
        <IconChevronLeft size={16} />
      </button>
      <span className="text-sm text-muted-foreground">
        Trang {currentPage + 1} / {totalPages}
      </span>
      <button
        onClick={() => goTo(currentPage + 1)}
        disabled={currentPage >= totalPages - 1}
        className="rounded-lg p-2 hover:bg-muted disabled:opacity-30 transition-colors"
      >
        <IconChevronRight size={16} />
      </button>
    </div>
  );
}
