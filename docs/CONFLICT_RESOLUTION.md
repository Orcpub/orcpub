# Conflict Resolution & Duplicate Key Handling

## Overview

Detects and resolves duplicate content keys during import, preventing data loss when merging homebrew content from multiple sources.

**Why this exists:** Before this feature, imports silently overwrote existing content with no warning. Characters referencing the old version would break. Users needed explicit control over conflict resolution.

**Key insight:** When renaming parent content (e.g., a class), all child references (subclasses) must be updated automatically or they become orphaned. This was the critical bug that led to the reference update feature.

## How It Works

### Duplicate Key Detection

Scans imported content for duplicate keys before import.

**Two conflict types:**

**External** - Between existing and imported content:
```
Already loaded: :artificer from "Core Rulebook"
Importing:      :artificer from "Homebrew Classes"
→ CONFLICT: Same key, different sources
```

**Internal** - Within the imported file:
```
Importing from "My Pack":
  - :artificer (Battle Smith subclass)
  - :artificer (Armorer subclass)
→ CONFLICT: Same key used twice
```

**Implementation:** `import_validation.cljs:162-280`

### Conflict Resolution Modal

Interactive modal presents three resolution options per conflict.

**1. Rename** - Give new key to imported item
```
Option: Rename imported :artificer to :artificer-2
Result: Both versions exist with different keys
Use when: You want to keep both versions
```

**2. Skip** - Don't import this item
```
Option: Skip :artificer from "Homebrew Classes"
Result: Existing version unchanged, new version discarded
Use when: You prefer the existing version
```

**3. Replace** - Overwrite existing with imported
```
Option: Replace :artificer with new version
Result: Existing version removed, new version loaded
Use when: You want to update to the new version
```

**Bulk actions:** Rename All, Skip All, Replace All (for handling 10+ conflicts efficiently)

**Design decision:** Originally considered automatic resolution strategies (always rename, always skip). User testing showed explicit per-conflict choices prevent unexpected behavior and data loss.

**Implementation:** `views/conflict_resolution.cljs`

### Key Renaming with Reference Updates

When renaming, automatically updates all internal references to maintain parent-child relationships.

```clojure
;; Original
{:key :artificer
 :name "Artificer"}

{:key :battle-smith
 :class :artificer        ; ← Reference to parent
 :name "Battle Smith"}

;; After renaming :artificer → :artificer-2
{:key :artificer-2        ; ← Renamed
 :name "Artificer"}

{:key :battle-smith
 :class :artificer-2      ; ← Auto-updated!
 :name "Battle Smith"}
```

**Why this matters:** Early implementation forgot to update references. Result: Subclasses imported successfully but became orphaned (not associated with parent class). Character builder showed them but they were unselectable.

**Reference types supported:**
- Subclass → parent class (`:class` field)
- Subrace → parent race (`:race` field)
- Items → class restrictions (`:classes` field)
- Spells → class spell lists (`:spell-lists` field)

**Implementation:** `import_validation.cljs:282-380`

### Auto-generated Keys

"Rename All" appends numeric suffix until key is unique.

```clojure
:artificer     → :artificer-2
:artificer-2   → :artificer-3
:blood-hunter  → :blood-hunter-2
```

Checks against all existing content (loaded + importing) to guarantee uniqueness.

**Alternative considered:** UUIDs (`:artificer-a3f2b1c9`). Rejected: not human-readable, harder to debug.

**Implementation:** `events.cljs:450-520`

## Common Scenarios

### Single Conflict

Importing `:artificer` when the core `:artificer` already exists:

```
┌─ Conflict Resolution ─────────────────────────┐
│ Found 1 duplicate key:                        │
│                                                │
│ Classes:                                       │
│   :artificer                                   │
│     Existing: "Core Rulebook"                  │
│     Importing: "Homebrew Classes"              │
│                                                │
│   ○ Rename to: :artificer-2                   │
│   ○ Skip (keep existing)                       │
│   ○ Replace (use imported)                     │
│                                                │
│         [Cancel]  [Apply Resolutions]         │
└────────────────────────────────────────────────┘
```

Choosing "Rename" → Both versions available (`:artificer` and `:artificer-2`)

### Multiple Conflicts

Importing 5 classes where 3 conflict with existing content. Click "Rename All" → all auto-renamed (`:artificer-2`, `:blood-hunter-2`, `:mystic-2`). Faster than resolving individually.

### Subclass Reference Updates (Critical)

Importing custom fighter with subclasses, conflicts with existing `:custom-fighter`:

```
Before rename:
  Class: :custom-fighter
  Subclass: :rune-knight → parent: :custom-fighter

After renaming to :custom-fighter-2:
  Class: :custom-fighter-2
  Subclass: :rune-knight → parent: :custom-fighter-2  ← Auto-updated
```

Without auto-update, subclass becomes orphaned (shows in UI but unselectable).

## Implementation

**How the modal appears (integration flow):**

1. **Import triggered** - User imports via `::e5/import-plugin` event (`events.cljs:3314`)
2. **Validation runs** - Checks for duplicate keys (`events.cljs:3318-3329`)
3. **Conflicts found?** - If yes, dispatch `:start-conflict-resolution` (`events.cljs:3353-3360`)
4. **State updated** - Event sets `:conflict-resolution {:active? true ...}` (`events.cljs:3466-3475`)
5. **Modal subscribes** - Component subscribes to `:conflict-resolution` (`subs.cljs:1296`, `views/conflict_resolution.cljs`)
6. **Conditional render** - `(when active? ...)` shows modal (`views/conflict_resolution.cljs`)
7. **Always mounted** - Modal part of `import-log-overlay` rendered in `main-view` (`core.cljs:113`)

**Key files:**
- `import_validation.cljs` - Conflict detection logic
- `events.cljs:3314-3575` - Import event, conflict check, resolution events
- `views/conflict_resolution.cljs` - Modal component + overlay container
- `core.cljs:106-113` - App root (mounts overlay on every page)
- `subs.cljs:1296-1313` - State subscriptions
- `import_validation_test.cljs` - Tests

**Reference fields map** (for adding new content types):
```clojure
{:subclass [:class]       ; Parent class
 :subrace [:race]         ; Parent race
 :spell [:spell-lists]    ; Which classes can cast
 :item [:classes]}        ; Class restrictions
```

## Testing

**Automated:** `import_validation_test.cljs` - Covers duplicate detection, key renaming, reference updates, unique key generation

**Critical manual test:** Create class + subclass → Export → Delete → Re-import with rename → Verify subclass still references renamed parent

**Why this test matters:** This is the bug that led to the reference update feature. Must not regress.

## Extending

**Change naming pattern:** Edit `generate-unique-key` in `events.cljs` (currently appends `-2`, `-3`, etc.)

**Add reference types:** Add to `reference-fields` map in `import_validation.cljs` (shown in Implementation section above)

## Troubleshooting

**Modal doesn't appear:** Check console for conflict detection logs. Verify import actually contains duplicate keys.

**References not updated:** Reference field probably not in `reference-fields` map. Add to `import_validation.cljs` and re-run.

**Duplicate keys after "Rename All":** Bug in `generate-unique-key`. Verify `existing-keys` includes all loaded content (not just imported).

## Future Enhancements

**Preview impact:** Show what renaming will affect before applying (e.g., "Will update 3 subclasses, 12 spell references")

**Conflict history:** Remember previous decisions for same content ("Last time: Rename - apply again?")

**Diff view:** Compare conflicting versions side-by-side to make informed choice

## Related Documentation

- [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md) - Import/export validation
- [CONTENT_RECONCILIATION.md](CONTENT_RECONCILIATION.md) - Missing content detection
- [HOMEBREW_REQUIRED_FIELDS.md](HOMEBREW_REQUIRED_FIELDS.md) - Content field requirements
