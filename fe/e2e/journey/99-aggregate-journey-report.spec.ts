import { test } from "@playwright/test";

import { aggregateJourneyReport } from "./_journey-evidence";

/**
 * Aggregator-only spec. Walks every chapter's report.json sidecar and writes
 * fe/e2e/evidence/JOURNEY-REPORT.md — the BA-facing top-level artifact.
 *
 * Filename starts with `99-` so Playwright runs it after every chapter
 * (file order = run order under our serial config). It deliberately runs
 * even when earlier chapters fail (no `test.describe.configure({ mode })`
 * cascade) so a failed run still produces a useful stakeholder report.
 */
test.describe("99 — aggregate journey report", () => {
  test("write JOURNEY-REPORT.md from every chapter's sidecar", async () => {
    await aggregateJourneyReport();
  });
});
