import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { FacetList } from "./facet-list";

const entries = [
  { key: "fashion", count: 12 },
  { key: "electronics", count: 7 },
];

describe("FacetList", () => {
  it("renders nothing when entries is empty", () => {
    const { container } = render(
      <FacetList title="Categories" entries={[]} selected="" onToggle={vi.fn()} />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders the title and one row per entry with its count", () => {
    render(<FacetList title="Categories" entries={entries} selected="" onToggle={vi.fn()} />);
    expect(screen.getByText("Categories")).toBeInTheDocument();
    expect(screen.getByText("fashion")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("electronics")).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
  });

  it("calls onToggle with the entry key when a row is clicked", () => {
    const onToggle = vi.fn();
    render(<FacetList title="Categories" entries={entries} selected="" onToggle={onToggle} />);
    fireEvent.click(screen.getByText("electronics"));
    expect(onToggle).toHaveBeenCalledWith("electronics");
  });

  it("uses formatLabel to render display text but still keys/toggles by raw value", () => {
    const onToggle = vi.fn();
    render(
      <FacetList
        title="Categories"
        entries={entries}
        selected=""
        onToggle={onToggle}
        formatLabel={(k) => `Cat:${k}`}
      />,
    );
    expect(screen.getByText("Cat:fashion")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Cat:fashion"));
    expect(onToggle).toHaveBeenCalledWith("fashion");
  });

  it("respects maxVisible by truncating the long tail", () => {
    const long = Array.from({ length: 20 }, (_, i) => ({ key: `k${i}`, count: 20 - i }));
    render(
      <FacetList title="Categories" entries={long} selected="" onToggle={vi.fn()} maxVisible={5} />,
    );
    expect(screen.getByText("k0")).toBeInTheDocument();
    expect(screen.getByText("k4")).toBeInTheDocument();
    expect(screen.queryByText("k5")).toBeNull();
  });
});
