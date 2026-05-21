#!/usr/bin/env node
// One-shot codemod: lucide-react -> @tabler/icons-react
// - rewrites import statements (named imports + module specifier)
// - renames JSX usages: <Heart .../> -> <IconHeart .../>, <Heart>.. -> <IconHeart>..
// - rewrites Lucide-color "stroke" prop on icon components to Tabler "color"
// - leaves strokeWidth={n} alone (still a valid SVG attr; works in Tabler)
//
// Targets: fe/src/app/**/*.{ts,tsx}

import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const ROOT = new URL("../src/app/", import.meta.url).pathname.replace(/^\//, "");

// Lucide -> Tabler. Most icons are direct prefix `Icon`.
// Renames where Tabler uses a different name are listed explicitly.
const RENAME = {
  AlertCircle: "IconAlertCircle",
  ArrowLeft: "IconArrowLeft",
  ArrowUpRight: "IconArrowUpRight",
  BarChart3: "IconChartBar",
  Bell: "IconBell",
  Check: "IconCheck",
  CheckCircle: "IconCircleCheck",
  CheckCircle2: "IconCircleCheck",
  ChevronRight: "IconChevronRight",
  Clock: "IconClock",
  CreditCard: "IconCreditCard",
  Edit: "IconEdit",
  Eye: "IconEye",
  EyeOff: "IconEyeOff",
  Filter: "IconFilter",
  Grid3X3: "IconLayoutGrid",
  Heart: "IconHeart",
  ImageIcon: "IconPhoto",
  ImageOff: "IconPhotoOff",
  LayoutDashboard: "IconLayoutDashboard",
  LayoutList: "IconLayoutList",
  Loader2: "IconLoader2",
  LogIn: "IconLogin",
  Mail: "IconMail",
  MapPin: "IconMapPin",
  MessageCircle: "IconMessageCircle",
  MessageSquare: "IconMessage",
  Package: "IconPackage",
  Plus: "IconPlus",
  RefreshCw: "IconRefresh",
  Search: "IconSearch",
  Send: "IconSend",
  Share2: "IconShare",
  Shield: "IconShield",
  ShieldCheck: "IconShieldCheck",
  ShoppingBag: "IconShoppingBag",
  ShoppingCart: "IconShoppingCart",
  SlidersHorizontal: "IconAdjustmentsHorizontal",
  Sparkles: "IconSparkles",
  Star: "IconStar",
  Tag: "IconTag",
  Trash2: "IconTrash",
  TrendingUp: "IconTrendingUp",
  Truck: "IconTruck",
  Users: "IconUsers",
  Wallet: "IconWallet",
  X: "IconX",
  XCircle: "IconCircleX",
  Zap: "IconBolt",
  // Icons used in HomePage that we forgot earlier
  Award: "IconAward",
  BadgeCheck: "IconBadgeCheck",
  Gift: "IconGift",
  Headphones: "IconHeadphones",
  // Second pass — caught by re-run
  Minus: "IconMinus",
  Copy: "IconCopy",
  ArrowRight: "IconArrowRight",
  Info: "IconInfoCircle",
  Store: "IconBuildingStore",
  User: "IconUser",
  Home: "IconHome",
  RotateCcw: "IconRotate",
  ArrowLeftRight: "IconArrowsLeftRight",
  ChevronLeft: "IconChevronLeft",
  ThumbsUp: "IconThumbUp",
  Camera: "IconCamera",
  Edit3: "IconEdit",
  LogOut: "IconLogout",
  Save: "IconDeviceFloppy",
  Sun: "IconSun",
  Moon: "IconMoon",
  Menu: "IconMenu2",
  Settings: "IconSettings",
  ChevronDown: "IconChevronDown",
  Phone: "IconPhone",
};

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    const st = statSync(p);
    if (st.isDirectory()) yield* walk(p);
    else if (/\.(tsx?|ts)$/.test(entry)) yield p;
  }
}

const importRegex = /import\s*(type\s+)?\{([^}]+)\}\s*from\s*["']lucide-react["'];?/g;

let touched = 0;
let unmapped = new Set();

for (const file of walk(ROOT)) {
  let src = readFileSync(file, "utf8");
  if (!src.includes("lucide-react")) continue;

  // collect identifiers we need to rename for this file
  const identsInFile = new Set();

  src = src.replace(importRegex, (_match, typePrefix, names) => {
    const parts = names
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    const mapped = parts.map((p) => {
      // Handle aliases: "Foo as Bar" -> map Foo, keep alias
      const aliasMatch = p.match(/^(\w+)\s+as\s+(\w+)$/);
      if (aliasMatch) {
        const [, orig, alias] = aliasMatch;
        if (!RENAME[orig]) {
          unmapped.add(orig);
          return p;
        }
        identsInFile.add(alias);
        // If alias is the same as the Tabler name we'd map to, drop the alias
        if (alias === RENAME[orig]) return RENAME[orig];
        return `${RENAME[orig]} as ${alias}`;
      }
      if (!RENAME[p]) {
        unmapped.add(p);
        return p;
      }
      identsInFile.add(p);
      return RENAME[p];
    });
    return `import ${typePrefix ?? ""}{ ${mapped.join(", ")} } from "@tabler/icons-react";`;
  });

  // rename JSX usages of each identifier (only when not aliased — aliases keep their old name in JSX)
  for (const id of identsInFile) {
    if (!RENAME[id]) continue;
    const tabler = RENAME[id];
    // Only rewrite usages that look like JSX or constructor refs.
    // <Foo  -> <IconFoo
    // </Foo> -> </IconFoo>
    // " Foo," / " Foo " / " Foo}" / " Foo)" — bare references in object/array contexts
    // Use word boundary.
    const re = new RegExp(`\\b${id}\\b`, "g");
    src = src.replace(re, tabler);
  }

  // After renaming components, fix the Tabler color/stroke prop overload.
  // Lucide: `stroke="#XXXX"` is a color. Tabler: `stroke` is the width number.
  // Rule: when stroke= holds a string literal (looks like a color) on an Icon* element,
  // rewrite to color=. Be conservative: only the literal-string form.
  // We rewrite per-line where the line contains <Icon...
  src = src.replace(
    /(<Icon\w+[^>]*?\s)stroke=("[^"{]+")/g,
    (_m, lead, val) => `${lead}color=${val}`,
  );
  // Also handle multiline JSX where the prop sits on its own line and the opening tag is above.
  // We do a second pass: any standalone `stroke="..."` line that is between `<Icon...` and the
  // closing `>` should be rewritten. Approximated by checking if the previous non-blank token
  // is part of an IconXxx tag — too brittle. Instead, rewrite ALL lines matching `^\s*stroke="..."\s*$`
  // when the file is an icon-using file (all of these are; lucide imports were just rewritten).
  src = src.replace(/^(\s*)stroke=("[^"{]+")(\s*)$/gm, "$1color=$2$3");

  writeFileSync(file, src);
  touched++;
  console.log("wrote", file);
}

console.log(`\nrewrote ${touched} files`);
if (unmapped.size > 0) {
  console.log("\nUNMAPPED identifiers (please add to RENAME):");
  for (const u of unmapped) console.log("  -", u);
  process.exit(1);
}
