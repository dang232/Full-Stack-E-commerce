#!/usr/bin/env node
// Hydrate OneDrive "Files On-Demand" reparse-point stubs to plain on-disk files.
//
// Why: OneDrive evicts files to cloud-only stubs after idle. They look like
// regular files to most tools but break anything that walks directories
// itself — Playwright's testMatch, esbuild glob inputs, Docker build
// contexts that selectively copy via .dockerignore. The pt17 notification
// service rebuild failed mid-flight because authenticated-request.ts
// re-stubbed since it was last touched.
//
// Detection: Node's lstat sets isSymbolicLink() for reparse-points on
// Windows. OneDrive cloud stubs come through that path.
// Fix: copy -> delete -> rename forces the file out of the reparse-point
// table and into a plain file. Reading alone is not enough — OneDrive can
// re-evict on the next sync cycle.
//
// Usage:
//   node scripts/hydrate.mjs                        # default dirs
//   node scripts/hydrate.mjs services/order-service # one service
//   node scripts/hydrate.mjs services/*/src         # whatever shell expands
//
// On non-Windows this script is a no-op.

import {
  existsSync,
  copyFileSync,
  unlinkSync,
  renameSync,
  readdirSync,
  lstatSync,
} from "node:fs";
import { join, resolve } from "node:path";

const SKIP_DIRS = new Set([
  "node_modules",
  "target",
  "dist",
  ".git",
  ".claude",
  ".idea",
  ".vscode",
  "build",
  "out",
]);

const DEFAULT_ROOTS = [
  "services",
  "fe/src",
  "fe/e2e",
  "infra",
];

const repoRoot = resolve(process.cwd());

function resolveRoots() {
  const argv = process.argv.slice(2);
  const list = argv.length > 0 ? argv : DEFAULT_ROOTS;
  return list
    .map((p) => resolve(repoRoot, p))
    .filter((p) => existsSync(p));
}

let hydrated = 0;
let directoryStubs = 0;

function walk(dir) {
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch (err) {
    // Directory itself may be a reparse-point stub. We can't recurse into
    // it without OneDrive hydrating the whole subtree, which would defeat
    // the per-file approach. Surface it so the user can pin the dir.
    directoryStubs += 1;
    console.warn(`skipped (directory stub or access error): ${dir} — ${err.code ?? err.message}`);
    return;
  }
  for (const entry of entries) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full);
      continue;
    }
    let stat;
    try {
      stat = lstatSync(full);
    } catch {
      continue;
    }
    if (!stat.isSymbolicLink()) continue;
    const tmp = `${full}.hydrate.tmp`;
    try {
      copyFileSync(full, tmp);
      unlinkSync(full);
      renameSync(tmp, full);
      hydrated += 1;
    } catch (err) {
      console.warn(`failed to hydrate: ${full} — ${err.code ?? err.message}`);
    }
  }
}

if (process.platform !== "win32") {
  console.log("not windows — nothing to hydrate");
  process.exit(0);
}

const roots = resolveRoots();
if (roots.length === 0) {
  console.log("no target directories exist — nothing to hydrate");
  process.exit(0);
}

console.log(`hydrating reparse-points under: ${roots.map((r) => r.replace(repoRoot + "\\", "")).join(", ")}`);
for (const root of roots) walk(root);

console.log(`hydrated ${hydrated} file(s)${directoryStubs > 0 ? `; ${directoryStubs} directory stub(s) skipped` : ""}`);
