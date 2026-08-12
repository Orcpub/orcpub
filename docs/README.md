# OrcPub Documentation

Guides for developers and power users working with OrcPub's homebrew content system.

## Quick Navigation

**For Users:**
- [📥 Import/Export Validation](ORCBREW_FILE_VALIDATION.md) - Safely import/export `.orcbrew` files
- [⚔️ Conflict Resolution](CONFLICT_RESOLUTION.md) - Handle duplicate keys during import
- [🔍 Missing Content Detection](CONTENT_RECONCILIATION.md) - Find/fix missing content references
- [📋 Required Fields Guide](HOMEBREW_REQUIRED_FIELDS.md) - Required fields per content type

**For Developers:**
- [ClojureScript Type Tolerance](clojurescript-type-tolerance.md) - What cljs silently tolerates vs. what crashes (and why); the string-op crash pattern behind render black screens
- [🚨 Error Handling](ERROR_HANDLING.md) - Error handling utilities
- [🗡️ Language Selection Fix](LANGUAGE_SELECTION_FIX.md) - Ranger favored enemy language corruption (#296)
- [🐳 Docker User Management](docker-user-management.md) - Verified user setup for Docker deployments

## Key Design Decisions

### Why Progressive Import?

**Problem:** Users had partially corrupted `.orcbrew` files. Previous all-or-nothing approach: one bad item blocks entire import.

**Decision:** Import valid items, skip invalid, show detailed error report.

**Rationale:** Partial data recovery better than total failure. Users can fix issues incrementally.

→ [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md)

### Why Interactive Conflict Resolution?

**Problem:** Silent overwrites caused data loss. Users wouldn't notice until characters broke.

**Decision:** Detect conflicts pre-import, show modal with resolution options (rename/skip/replace).

**Critical insight:** When renaming parent content (e.g., class), all child references (subclasses) must auto-update or they become orphaned. Early implementation forgot this → orphaned subclasses appeared in UI but were unselectable.

→ [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md)

### Why Fuzzy Matching for Missing Content?

**Problem:** Content keys change between versions (`:blood-hunter` → `:blood-hunter-v2`). Users see "(not loaded)" with no help.

**Decision:** Multiple fuzzy matching strategies (Levenshtein, prefix, name similarity) to catch typos and versioning.

→ [CONTENT_RECONCILIATION.md](CONTENT_RECONCILIATION.md)

### Why a Fallback Chain for Language Selection?

**Problem:** Ranger favored enemy types reference 24 exotic language keys (`:aquan`, `:gith`, `:bullywug`, etc.) that aren't in the base 16 languages. `language-selection` returned nil for these, corrupting character data.

**Decision:** Three-layer fallback: language-map → corrections shim → generated entry from key. Never returns nil.

**Critical insight:** Can't remove or remap exotic keys because homebrew plugins legitimately define them. The fallback generates a valid entry when the plugin isn't loaded and uses the plugin's definition when it is.

**Gotcha:** Two different "key" concepts exist: language `:key` (data keyword like `:aquan`) and option `::entity/key` (derived from display name via `name-to-kw`). The fallback must produce names that round-trip correctly through `key-to-name` / `name-to-kw`.

> [LANGUAGE_SELECTION_FIX.md](LANGUAGE_SELECTION_FIX.md)

**Problem:** Inconsistent error handling across codebase. Some code logged, some didn't. User messages inconsistent.

**Decision:** Centralize in macros (`with-db-error-handling`, `with-email-error-handling`, `with-validation`).

**Rationale:** Consistency in logging, user messages, error data structure. Easier to add monitoring later.

→ [ERROR_HANDLING.md](ERROR_HANDLING.md)

## Common Workflows

**Creating homebrew:** Create in UI → Export → Check console warnings → Fix required fields → Re-export

**Importing content:** Import file → Resolve conflicts (if any) → Check for missing content warnings

**Debugging imports:** Console (F12) → Check validation errors → Use progressive import to recover partial data

**Fixing characters:** Check missing content warnings → Import plugin or use suggested alternative

## Known Limitations

**Field requirements:** Not all required fields are enforced. Some will silently break features (see HOMEBREW_REQUIRED_FIELDS.md).

**Batch operations:** Can only import one file at a time. Multi-file import with cross-reference resolution would be valuable.

## Implementation Files

**Import/Export:** `import_validation.cljs`, `events.cljs`
**Import UI:** `views/import_log.cljs` (log panel with grouped collapsible sections), `views/conflict_resolution.cljs` (conflict modal, export warning)
**Content Reconciliation:** `content_reconciliation.cljs`, `subs.cljs`, `character_builder.cljs` (warning UI)
**Error Handling:** `errors.cljc` (DRY macros)
**Tests:** `import_validation_test.cljs`, `errors_test.clj`, `favored_enemy_language_test.cljc`

All in `src/cljs/orcpub/dnd/e5/` unless noted.

## Debugging Tips

**Import failures:** Check console (F12) → Use progressive import to recover partial data

**Character broken:** Look for "(not loaded)" warnings → Import missing plugin or use suggested alternative

**Conflicts on import:** Modal should appear automatically → Choose rename/skip/replace per item

---
