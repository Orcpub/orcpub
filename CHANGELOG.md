# Changelog

## [Summer Patch] — 2026 (character-load resilience, homebrew salvage, PDF printing)

Consolidates the June bug-patch bundle, the homebrew conflict-resolution/salvage
work, and the summer additions (PDF printing, character-load recovery, spell-key
reconciliation) into one release.

### Fixed

Character loading & display
- **"A single colon is not a valid keyword" crash + self-heal** — a custom element saved with a blank or symbols-only name derived the empty keyword `:`, an unreadable token that crashed the whole character on load; key generation now guards the blank case, and an already-corrupt save is repaired in place on load (`ba80b78d`).
- **An unreadable character recovers in place** — instead of a blank page it shows a recovery panel with a copyable diagnostic, and error messages persist until dismissed (`d50eaf87`).
- **Character sheets no longer go blank** — an unrenderable section shows a recovery message; the rest of the sheet stays usable (`565c33c0`).
- **The Features tab loads for every character**, a nameless trait shows "[Unnamed feature]" instead of crashing the name sort, and Hunter's Evasion is named (`2a6fde93`, `5c3b073f`, `dd65d66a`).
- **Homebrew class source no longer poisons spell-selection keys** — the source label was folded into the class `:name`, so saved spells/cantrips vanished when it changed; keys now come from a stable class identity, and orphaned saves repair on load (`9a709c0d`, `fe549631`, `a3e26155`).
- **Boolean toggles no longer corrupt data** and self-heal old damage (`1e9f27ec`); **non-ASCII name detection works in the browser** (`d9b23021`).

Homebrew import / export / salvage
- **"Rename all" resolves duplicate keys in one pass** instead of the 20 → 3 → 1 → 0 re-import crawl (`c037de78`).
- **Multi-source paks survive an imperfect sub-source** — detection is structural (shape), not spec-validity, so one flawed sub-source no longer quarantines the whole pak (`c037de78`).
- **Conflict resolution no longer nils out an item** — a redundant double-rename is now a no-op (`9df1b4ae`).
- **Import can't report success it won't keep** — problems surface at import time against the loader's floor, and success fires only after the write persists (`c037de78`, `7782e831`).
- **Dangling spell references render** with a key-derived name + edit link instead of a blank card, reported once per class (`a4dbfe19`, `73e75c9d`).
- **Old-name spells in imported paks resolve** — 17 pre-2024 wizard-possessive keys (Leomund's, Tasha's, Bigby's…) map to their current SRD keys; loaded homebrew is never overridden (`cf1f4f1c`).
- **Keyword-trap imports are caught and routed to repair** instead of silently vanishing (`d9b23021`); **unreadable storage is preserved** for recovery, not deleted (`eedffc08`); **quota-failed saves warn and offer a backup**.
- **Readable import/export errors** — plain-English console messages, dedup shown as a log line (`eba28a9c`, `e512dc45`); **post-save export** and **autosave-on-empty-template** crashes fixed (`e3c9a9ee`).

### Added

Homebrew resilience & repair
- **Per-entry salvage** — one bad entry no longer quarantines its whole source; valid items stay, broken ones are set aside for repair (`957e09ab`, `7782e831`, `c037de78`).
- **Entry-level repair panel** in My Content — editable Name + Option-source per set-aside entry, Fix & Restore, and Discard (`d34007ff`, `c037de78`).
- **Export runs the same checks as import** — duplicates/cleanups caught on the way out; raw/pretty export stays an unchecked escape hatch (`9df1b4ae`, `c037de78`).
- **Resilient homebrew loading** with a My Content repair panel (`eedffc08`); **builder escape hatches** — draft export, refresh-safe WIP restore, "Save anyway" with placeholders, emergency raw export, Export & Auto-Fix (`eac350d0`, `e3c9a9ee`); **"Show homebrew source on class names" toggle** (`8f94a94c`); **fill-in dialog on export** with live field guidance (`1547cd69`, `22172adb`).

PDF & printing
- **Card-back logo** — "Print logo on card backs" under a new Appearance section; the mark redrawn to print legibly (solid black, filled letters), with a faded-color option for color printers (`e8e560a3`, `99e20389`, `d0f2bfd8`).
- **Printer-friendly (black & white) spell cards** — the baked-red casting/range/component/duration/recharge icons render solid black with white-halo labels; a nested "faded grayscale icons" option offers a softer look (`cb51a4fa`, `dcc8d551`).

Support
- **Report a character that won't load** — from the recovery panel, an auth-gated one-click report (or copyable text) emails the support address, falling back to the existing error-notification inbox so no new config is needed; header-injection-safe, raw capped (`c2bc7d03`, `4fb40a20`, `b88d1413`).

### Changed
- **Import and export share one correction gate**, and the exported library is canonicalized so import → export → re-import is idempotent (`9df1b4ae`, `c037de78`).
- **Quarantine granularity is per-entry**, backward-compatible with whole-source entries, with precise per-entry diagnostics (`957e09ab`, `7782e831`, `c037de78`).
- **Source-less imported content lands in the real "Default Option Source"** instead of a phantom placeholder (`54f4e87d`).
- **Save validation covers every required field** — dropdowns and multi-selects too (`e512dc45`); **save and load share one spec registry** so they can't drift (`ca977e0a`); normal exports strip meaningless blank flags; invalid-key errors are element-specific.

## [breaking/2026-stack-modernization]

### Infrastructure

- **2026 full-stack modernization** (`22823da`)
  Java 8 → 21, Datomic Free → Pro, Pedestal 0.5 → 0.7.0, React 15 → 18,
  Reagent 0.6 → 2.0, re-frame 0.x → 1.4.4, PDFBox 2 → 3, clj-time → java-time,
  figwheel-main, lambdaisland/garden, Jackson/Guava pinning.

- **Consolidate dev tooling** (`6249565`)
  Unified `user.clj` with lazy figwheel, nREPL helpers, lein aliases
  (`fig:dev`, `fig:watch`, `fig:build`, `fig:test`), operational scripts
  (`start.sh`, `stop.sh`, `menu`), `:dev`/`:uberjar`/`:lint`/`:init-db` profiles.

- **Merge develop** (`1d50782`)
  Integrate character folders, weapon builder (special/loading properties),
  docker-compose updates from `origin/develop` (24 commits).

### Bug Fixes

- **`:class-name` → `:class`** (`263f290`)
  Reagent 2.x overwrites hiccup tag classes with `:class-name`. Converted all
  UI uses to `:class`; 18 remaining `:class-name` are D&D data keys (correct).

- **Subscribe-outside-reactive-context — phase 1** (`c2290ca`)
  42 fixes across events.cljs, options.cljc, classes.cljc, core.cljs.
  Patterns: direct db read, plugin-data map, track! template cache, SSOT pure fns.

- **Subscribe-outside-reactive-context — phase 2** (`09d7e4c`)
  14 fixes across options.cljc, pdf_spec.cljc, equipment_subs.cljs, views.cljs.
  Patterns: plugin-data threading, reg-sub-raw, move to render scope.

- **Prereq subscribes → pure character fns** (`9cbc25a`)
  22 prereq-fn lambdas in options.cljc converted from `@(subscribe)` to pure
  `(fn [character] ...)` functions.

- **Multiclass/wizard prereqs** (`3249f88`)
  7 multiclass and spell-mastery prereqs in classes.cljc converted to pure fns.

- **`def` + `partial` → `defn`** (`f578cdb`)
  `option-language-proficiency-choice` captured subscribe at load time via
  `partial`. Converted to `defn` for proper reactive context.

### Cleanup

- **Remove 11 orphaned subscriptions** (`bb2400d`)
  4 static map wrappers deleted (superseded by homebrew-aware versions).
  7 unused subs reader-discarded (`#_`) with comments: `all-melee-weapons`,
  `item`, `base-spells-map`, `spell-option`, `spell-options`,
  `filtered-monster-names`, `has-prof?`. Pre-existing tech debt, not caused
  by subscribe refactor.

- **Fix 591 missing-else-branch lint warnings** (`29c9f28`, via `fix/lint-missing-else`)
  Mechanical `if→when`, `if-let→when-let`, `if-not→when-not` across 33 files.
  Scripted fix (`scripts/fix-missing-else.py`) with column-precise substitution.
  Also fixed 2 pre-existing bugs: `when` used instead of `if` for two-branch
  conditionals in classes.cljc:1808 and options.cljc:463.

- **Fix forward-reference lint error** (`792fe3c`)
  `show-generic-error` used before its `def` alias in events.cljs. Changed to
  fully-qualified `event-utils/show-generic-error`.

- **Consolidate lint config** (`7476f10`)
  All linter settings moved from project.clj `:lint` profile to
  `.clj-kondo/config.edn` (single source of truth for IDE + CLI). Lint scope
  expanded to cover `native/`, `test/`, `web/`. clj-kondo bumped to 2026.01.19.
  LSP false-positive suppression via `:exclude-when-defined-by` for re-frame.

- **Dead code cleanup — ~92 vars** (`6bbcd9a`, `b68b917`)
  `#_` reader-discard on dead defs across 10 source files: deprecated ua/scag
  refs, superseded template UI (ability roller, amazon frames), 17 never-dispatched
  event handlers, dead style defs, duplicate constants. Includes cascade cleanup
  (helpers that lost all callers). Each `#_` has a comment explaining why.

- **Redundant expression fixes** (in `6bbcd9a`, `b68b917`, `429152e`)
  Remove nested `(str (str ...))`, flatten `(and (and ...))`, remove duplicate
  destructuring param, remove unused refers, narrow test `:refer` lists,
  fix unreachable code in registration.cljc.

### Enhancements

- **Input debounce** (`d108134`)
  Moved debounce from component-level `input-field` to `debounced-build-sub`
  in subs.cljs (leading+trailing edge, 500ms). Eliminates per-keystroke
  entity/build recomputation.

- **Folder hardening** (`f28f58f`)
  `on-folder-failure` event re-fetches server state on HTTP error. Client +
  server blank-name validation. `check-folder-owner` wrapped with
  `interceptor/interceptor`, returns 404 for missing folders. Named tempid
  `"new-folder"` + `d/resolve-tempid`. `case` default clause in folders sub.
  CSS class fix (`builder-dropdown` → `builder-option-dropdown`).

- **UI polish** (`d163ca9`)
  Zero-warning dev/prod builds, dev-mode CSP nonce, favicon, custom
  `externs.js` for React 18 advanced compilation.

### Tests

- **CLJS test infrastructure** (`b96b1b6`)
  figwheel-main test build, `test_runner.cljs`, pure function tests for
  compute, entity, character accessors.

- **JVM tests for new code** (`6124d9f`)
  `compute-all-weapons-map`, feat-prereqs, pdf_spec pure functions, folder
  routes (CRUD + blank rejection + trimming).

- **Folder validation tests** (in `f28f58f`)
  Blank name → 400, whitespace trimming, nil defaults to "New Folder",
  name unchanged after rejected renames.

### Documentation

- **Migration docs** (`026b031`)
  MIGRATION-INDEX.md, JAVA-COMPATIBILITY.md, datomic-pro.md, pedestal-0.7.md,
  frontend-stack.md, library-upgrades.md, dev-tooling.md, ENVIRONMENT.md,
  testing.md.

- **STACK.md** (in `f28f58f`)
  Library/dependency onboarding guide: architecture diagram, all frameworks,
  build system, profiles, dependency pinning rationale.

### Current Status

- **174 JVM tests**, 444 assertions, 0 failures
- **0 CLJS errors**, 0 warnings (dev + advanced)
- **0 subscribe warnings** in browser console
- **0 linter errors**, 0 warnings

---

## [feature/error-handling-import-validation] (merged)

### New Features

#### Import Validation (`import_validation.cljs` -- new file)
- **Unicode normalization**: Converts smart quotes, em-dashes, non-breaking spaces, and 40+ other problematic Unicode characters to ASCII equivalents on import and homebrew save. Prevents copy-paste corruption from Word/Google Docs.
- **Required field detection & auto-fill**: On import, missing required fields (`:name`, `:hit-die`, `:speed`, etc.) are auto-filled with placeholder values like `[Missing Name]`. Content types covered: classes, subclasses, races, subraces, backgrounds, feats, spells, monsters, invocations, languages, encounters.
- **Trait validation**: Nested `:traits` arrays are checked for missing `:name` fields and auto-filled.
- **Option validation**: Empty options (`{}`) created by the UI are detected and auto-filled with unique default names ("Option 1", "Option 2", etc.).
- **Multi-plugin format detection**: Distinguishes single-plugin from multi-source orcbrew files for correct processing.

#### Export Validation
- **Pre-export warning modal**: Before exporting homebrew, all content is validated for missing required fields. If issues are found, a modal lists them with an "Export Anyway" option.
- **Specific save error messages**: `reg-save-homebrew` now extracts field names from spec failures and shows targeted messages instead of generic "You must specify a name" errors.

#### Content Reconciliation (`content_reconciliation.cljs` -- new file)
- **Missing content detection**: When a character references homebrew content that isn't loaded (e.g., deleted plugin), the system detects missing races, classes, and subclasses.
- **Fuzzy key matching**: Uses prefix matching and base-keyword similarity to suggest available content that resembles missing keys (top 5 matches with similarity scores).
- **Source inference**: Guesses which plugin pack a missing key likely came from based on key structure.

#### Missing Content Warning UI (`character_builder.cljs`)
- **Warning banner**: Orange expandable banner appears in character builder when content is missing, showing count and details.
- **Detail panel**: Lists each missing item with its content type, key, inferred source, and suggestions for similar available content.
- **DOM IDs for testability**: `#missing-content-warning`, `#missing-content-details`, `.missing-content-item` with `data-key` and `data-type` attributes.

#### Conflict Resolution Modal (`views/conflict_resolution.cljs`, `events.cljs`)
- **Duplicate key detection**: On import, detects keys that conflict with already-loaded homebrew (both internal duplicates within a file and external conflicts with existing content).
- **Resolution UI**: Modal presents each conflict with rename options. Key renaming updates internal references (subclass -> parent class mappings, etc.).
- **Color-coded radio options**: Rename (cyan), Keep (orange), Skip (purple) with left-border + tinted background. All styles in Garden CSS.

#### Import Log Panel (`views/import_log.cljs`)
- **Grouped collapsible sections**: Changes grouped into Key Renames, Field Fixes, Data Cleanup, and Advanced Details (collapsed by default). Empty sections hidden automatically.
- **Detailed field fix reporting**: Field Fixes section shows per-item breakdown — which item, content type, which fields were filled, how many traits/options were fixed.
- **Collapsible section component**: Reusable `collapsible-section` with configurable icon, colors, and default-expanded state.

#### OrcBrew CLI Debug Tool (`tools/orcbrew.clj` -- new file)
- `lein prettify-orcbrew <file>` -- Pretty-prints orcbrew EDN for readability.
- `lein prettify-orcbrew <file> --analyze` -- Reports potential issues: nil-nil patterns, problematic Unicode, disabled entries, missing trait names, file structure summary.

### Bug Fixes

#### nil nil Corruption (`events.cljs`)
- **Root cause fix**: `set-class-path-prop` was calling `assoc-in` with a nil path, producing `{nil nil}` entries in character data. Now guards against nil path before the second `assoc-in`.

#### Nil Character ID Crash (`views.cljs`)
- Character list page crashed with "Cannot form URI without a value given for :id parameter" when characters had nil `:db/id`. Added `(when id ...)` guard to skip rendering those entries.

#### Subclass Key Preservation (`options.cljc`, `spell_subs.cljs`)
- Subclass processing now uses explicit `:key` field if present (for renamed plugins), falling back to name-generated key. Prevents renamed keys from reverting.
- `plugin-subclasses` subscription preserves map keys and sets `:key` on subclass data correctly.

#### Plugin Data Robustness (`spell_subs.cljs`)
- `plugin-vals` subscription wrapped in try-catch to skip malformed plugin data instead of crashing.
- `level-modifier` handles unknown modifier types gracefully (logs warning, returns nil instead of throwing).
- `make-levels` filters out nil modifiers with `keep`.

#### Unhandled HTTP Status Crash (`subs.cljs`, `equipment_subs.cljs`)
- All 7 API-calling subscriptions used bare `case` on HTTP status with no default clause. Any unexpected status (e.g., 400) threw `No matching clause`. Replaced with `handle-api-response` HOF that logs unhandled statuses to console.

#### Import Log "Renamed key nil -> nil" (`events.cljs`, `import_validation.cljs`)
- Key rename change entries used `:old-key`/`:new-key` fields but display code expected `:from`/`:to`. Unified on `:from`/`:to` across creation, application, and display.

### Error Handling (Backend)

#### Database (`datomic.clj`)
- Startup wrapped in try-catch with structured errors: `:missing-db-uri`, `:db-connection-failed`, `:schema-initialization-failed`.

#### Email (`email.clj`)
- Email config parsing catches `NumberFormatException` for invalid port (`:invalid-port`).
- `send-verification-email` and `send-reset-email` check postal response and raise on failure (`:verification-email-failed`).

#### PDF Generation (`pdf.clj`, `pdf_spec.cljc`)
- Network timeouts (10s connect, 10s read) for image loading. Specific handling for `SocketTimeoutException` and `UnknownHostException`.
- Nil guards throughout `pdf_spec.cljc`: `total-length`, `trait-string`, `resistance-strings`, `profs-paragraph`, `keyword-vec-trait`, `damage-str`, spell name lookup. All use fallback strings like "(unknown)", "(Unknown Spell)", "(Unnamed Trait)".

#### Routes (`routes.clj`, `routes/party.clj`)
- All mutation endpoints wrapped with error handling: verification, password reset, entity CRUD, party operations. Each uses structured error codes (`:verification-failed`, `:entity-creation-failed`, `:party-creation-failed`, etc.).

#### System (`system.clj`)
- PORT environment variable parsing validates numeric input (`:invalid-port`).

#### Error Infrastructure (`errors.cljc` -- expanded)
- New error code constants for auth flows.
- `log-error`, `create-error` utility functions.
- `with-db-error-handling`, `with-email-error-handling`, `with-validation` macros for consistent patterns.

### Supporting Changes

#### Common Utilities (`common.cljc`)
- `kw-base`: Extracts keyword base before first dash (e.g., `:artificer-kibbles` -> `"artificer"`).
- `traverse-nested`: Higher-order function for recursively walking nested option structures.

#### Styles (`styles/core.clj`)
- `.bg-warning`, `.bg-warning-item` CSS classes for warning banner UI.
- `.conflict-*` Garden CSS classes for conflict resolution modal (backdrop, modal, header, footer, body, radio options with color-coded variants: cyan/rename, orange/keep, purple/skip).
- `.export-issue-*` Garden CSS classes for export warning modal.

#### App State (`db.cljs`)
- Added `import-log` and `conflict-resolution` state maps to re-frame db.

#### Subscriptions (`subs.cljs`, `equipment_subs.cljs`)
- Import log, conflict resolution, export warning, missing content report subscriptions.
- `handle-api-response` HOF (`event_utils.cljc`) — centralizes HTTP status dispatch with sensible defaults (401 → login, 500 → generic error) and catch-all logging for unhandled statuses. Replaces bare `case` statements across 7 API-calling subscriptions.

#### Entry Point (`core.cljs`)
- Dev version logging on startup.
- Import log overlay component mounted in main view wrapper.

#### Linter Configuration
- `.clj-kondo/config.edn`: Exclusions for `with-db` macro and user namespace functions.
- `.lsp/config.edn` (new): Explicit source-paths to prevent clojure-lsp from scanning compiled CLJS output in `resources/public/js/compiled/out/`.

### Design Principles

- **Import = permissive** (auto-fix and continue), **Export = strict** (warn user, let them decide)
- **Placeholder text convention**: `[Missing Name]` format (square brackets indicate auto-filled)
- **Modal pattern**: db state -> re-frame subscription -> event handlers -> component in `import-log-overlay`
