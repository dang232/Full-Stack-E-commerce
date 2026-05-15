import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useCountdown } from "./use-countdown";

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-05-16T12:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useCountdown", () => {
  it("formats remaining time as zero-padded hours / minutes / seconds", () => {
    const target = Date.now() + 2 * 3600_000 + 5 * 60_000 + 7 * 1000;
    const { result } = renderHook(() => useCountdown(target));
    expect(result.current.h).toBe("02");
    expect(result.current.m).toBe("05");
    expect(result.current.s).toBe("07");
    expect(result.current.isExpired).toBe(false);
  });

  it("ticks down once per second", () => {
    const target = Date.now() + 5 * 1000;
    const { result } = renderHook(() => useCountdown(target));
    expect(result.current.s).toBe("05");

    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(result.current.s).toBe("03");

    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(result.current.s).toBe("00");
    expect(result.current.isExpired).toBe(true);
  });

  it("clamps at zero when the target is in the past", () => {
    const target = Date.now() - 60_000;
    const { result } = renderHook(() => useCountdown(target));
    expect(result.current.h).toBe("00");
    expect(result.current.m).toBe("00");
    expect(result.current.s).toBe("00");
    expect(result.current.isExpired).toBe(true);
  });

  it("clears the interval on unmount", () => {
    const clearSpy = vi.spyOn(global, "clearInterval");
    const target = Date.now() + 60_000;
    const { unmount } = renderHook(() => useCountdown(target));
    unmount();
    expect(clearSpy).toHaveBeenCalled();
  });
});
