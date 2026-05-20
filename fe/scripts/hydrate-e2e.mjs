#!/usr/bin/env node
// Hydrate any OneDrive "Files On-Demand" reparse-points in fe/e2e/.
//
// Why: OneDrive's cloud-only stubs report `IO_REPARSE_TAG_CLOUD` and
// Playwright's file walker on Windows silently excludes them from
// `testMatch`. Symptoms: `npx playwright test --list` shows N-2 of N
// specs and targeting a missing spec by exact path returns
// "Total: 0 tests in 0 files".
//
// Fix: copy → delete → rename forces the file out of the reparse-point
// table and into a plain on-disk file. Reading alone is not enough —
// OneDrive can re-evict after a sync cycle.
//
// On non-Windows this script is a no-op.

import { existsSync, copyFileSync, unlinkSync, renameSync, readdirSync, lstatSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const E2E_DIR = resolve(dirname(fileURLToPath(import.meta.url)), "..", "e2e");

if (process.platform !== "win32") process.exit(0);
if (!existsSync(E2E_DIR)) process.exit(0);

const REPARSE_POINT = 0x400; // IO_REPARSE_TAG_* set on file attributes via fs

let hydrated = 0;
for (const name of readdirSync(E2E_DIR)) {
  if (!name.endsWith(".spec.ts")) continue;
  const full = join(E2E_DIR, name);
  const stat = lstatSync(full);
  // Node's lstat sets isSymbolicLink() for reparse-points on Windows;
  // OneDrive cloud-only stubs come through here.
  if (!stat.isSymbolicLink()) continue;
  const tmp = `${full}.hydrate.tmp`;
  copyFileSync(full, tmp);
  unlinkSync(full);
  renameSync(tmp, full);
  hydrated += 1;
}

if (hydrated > 0) {
  console.log(`hydrated ${hydrated} OneDrive reparse-point spec file(s) in fe/e2e/`);
}
