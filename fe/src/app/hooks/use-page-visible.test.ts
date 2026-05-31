import { renderHook, act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { usePageVisible } from "./use-page-visible";

function setHidden(value: boolean) {
  Object.defineProperty(document, "hidden", { value, configurable: true });
}

beforeEach(() => {
  setHidden(false);
});

afterEach(() => {
  setHidden(false);
  vi.restoreAllMocks();
});

describe("usePageVisible", () => {
  it("returns true initially when document.hidden is false", () => {
    setHidden(false);
    const { result } = renderHook(() => usePageVisible());
    expect(result.current).toBe(true);
  });

  it("flips to false when visibilitychange fires with document.hidden = true", () => {
    setHidden(false);
    const { result } = renderHook(() => usePageVisible());
    expect(result.current).toBe(true);

    act(() => {
      setHidden(true);
      document.dispatchEvent(new Event("visibilitychange"));
    });

    expect(result.current).toBe(false);
  });

  it("removes the visibilitychange listener on unmount", () => {
    const addSpy = vi.spyOn(document, "addEventListener");
    const removeSpy = vi.spyOn(document, "removeEventListener");

    const { unmount } = renderHook(() => usePageVisible());

    const addedCalls = addSpy.mock.calls.filter(([type]) => type === "visibilitychange");
    expect(addedCalls).toHaveLength(1);

    unmount();

    const removedCalls = removeSpy.mock.calls.filter(([type]) => type === "visibilitychange");
    expect(removedCalls).toHaveLength(1);
    // The same handler reference must be passed to both calls
    expect(removedCalls[0][1]).toBe(addedCalls[0][1]);
  });
});
