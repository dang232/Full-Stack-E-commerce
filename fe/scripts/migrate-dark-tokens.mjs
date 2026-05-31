#!/usr/bin/env node
// Dark-mode token codemod: swap hard-coded light-mode utilities for theme tokens.
//
// What it touches:
//   bg-white          (NOT bg-white/X) → bg-card
//   bg-gray-50/100    → bg-muted
//   hover:bg-gray-50  → hover:bg-muted
//   hover:bg-gray-100 → hover:bg-muted
//   text-gray-700/800/900 → text-foreground
//   text-gray-400/500/600 → text-muted-foreground
//   border-gray-100/200   → border-border
//
// Skips:
//   - HomePage.tsx, Root.tsx (already migrated by hand)
//   - DesignSystemPage.tsx (token fixture — keeps explicit colors)
//   - theme.css, i18n JSON
//
// Word boundaries protect bg-white/10, bg-white/20, hover:bg-white/10, etc.

import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const ROOT = new URL("../src/app/", import.meta.url).pathname.replace(/^\//, "");
const SKIP = new Set([
  "HomePage.tsx",
  "Root.tsx",
  "DesignSystemPage.tsx",
]);

const REPLACEMENTS = [
  // bg-white not followed by / — covers bg-white but not bg-white/10
  [/\bbg-white(?!\/)/g, "bg-card"],
  // hover:bg-gray must come before bg-gray to win
  [/\bhover:bg-gray-(50|100)\b/g, "hover:bg-muted"],
  [/\bbg-gray-(50|100)\b/g, "bg-muted"],
  [/\btext-gray-(700|800|900)\b/g, "text-foreground"],
  [/\btext-gray-(400|500|600)\b/g, "text-muted-foreground"],
  [/\bborder-gray-(100|200)\b/g, "border-border"],
];

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    const st = statSync(p);
    if (st.isDirectory()) yield* walk(p);
    else if (/\.tsx$/.test(entry)) yield p;
  }
}

let touched = 0;
let totalSwaps = 0;
for (const file of walk(ROOT)) {
  const base = file.split(/[\\/]/).pop();
  if (SKIP.has(base)) continue;
  const before = readFileSync(file, "utf8");
  let after = before;
  let swapsHere = 0;
  for (const [re, to] of REPLACEMENTS) {
    after = after.replace(re, (...args) => {
      swapsHere++;
      // re may have a capture group; we use a single string replacement
      return typeof to === "string" ? to : to(...args);
    });
  }
  if (swapsHere > 0) {
    writeFileSync(file, after);
    touched++;
    totalSwaps += swapsHere;
    console.log(`${swapsHere.toString().padStart(3)} swaps  ${file.replace(ROOT, "")}`);
  }
}

console.log(`\n${touched} files, ${totalSwaps} swaps`);
