# Keyword-trap name repair (number→word chain)

How the app recovers a homebrew item whose NAME derives an invalid KEY — the
"keyword trap." A key must start with a letter (`common/keyword-starts-with-letter?`),
so names that lead with a number ("9 Lives") or a symbol ("@@@") derive an
invalid key (`:9-lives`) and get quarantined. This doc is the paper trail for how
we repair them without silently discarding the user's intent.

## Principle (why this exists)
Quarantine's job is to **surface a problem, isolate it so it can't infect data,
and let it be fixed — manually OR automatically.** Auto-heal is a *choice offered*
for fields we can safely heal, **not a silent default.** The earlier
`bdf24138` made Fix & Restore auto-coerce every invalid name to "Unnamed <Type>"
with no choice — laundering broken entries past the one gate meant to make the
user resolve them, and throwing away good names ("9 Lives" → "Unnamed Class").
This work replaces that with a least-destructive, opt-in repair.

## The repair chain (least-destructive first)
`common/repair-name-lead` (in `common.cljc`, CLJC so it's JVM- and CLJS-testable):

1. **Already valid** (letter-leading) → unchanged.
2. **Leading number → word** — `lead-number->words`: "9 Lives" → "Nine Lives",
   "2nd Wind" → "Second Wind", "100 Hands" → "One Hundred Hands". Preserves intent.
3. **Leading symbols → strip** — "@@@Bob" → "Bob".
4. **Nothing usable** (all symbols "@@@", or an out-of-range number with no letters
   "2020") → returns `nil`; the caller falls back to a placeholder ("Unnamed <Type>").

The result is a **suggestion**: the repair panel pre-fills it, the user accepts or
edits, and the caller still checks the derived key for **collisions** with other
brew (a colliding suggestion also falls back to the placeholder).

## Number→word translator (bounded on purpose)
`common/cardinal->words`, `ordinal->words`, `lead-number->words`:
- **Cardinals + ordinals, 0..`max-number-word`** (default **999**; a named const,
  so moving the ceiling — e.g. to 9999 for "1000 Cuts" — is one edit).
- Above the cap a leading number reads as **data** (a year/stat/code, "2020
  Vision"), not a name-word, so the translator **declines** (nil) and the chain
  falls through. Depth is cheap to extend; the cap is a *quality* knob.
- **Bails on glued tokens** — "3d6" (dice), "5e" (edition): digits followed by a
  non-ordinal letter are left alone rather than mangled ("Threed6"). Only digits
  followed by an ordinal suffix (st/nd/rd/th) or a word boundary translate.
- Pinned by `common_test.cljc` (`cardinal->words-*`, `ordinal->words-*`,
  `lead-number->words-*`, `repair-name-lead-chain`). Runs under `lein fig:test`
  (the `.cljc` common-test is shadowed under `lein test` — see
  `common_test.clj`'s header).

## UI wiring — Manual vs Auto (DESIGN — not yet built)
The repair panel splits the ambiguous "Fix + Restore" button into two clearly
labelled actions, so auto-heal is an explicit choice:
- **Restore** (manual): enabled once the user typed a valid name → restores with
  THEIR name.
- **Auto-name & Restore**: runs `repair-name-lead` and shows exactly what it will
  produce ("restore as 'Nine Lives'"); only when the chain can't repair does it
  fall to "Unnamed <Type>". When the auto-suggestion is a clean, non-colliding
  fix, both buttons converge on the same result.
- **Do neither → stays quarantined.** The default `repair-quarantined-source`
  path must NOT auto-coerce (that's what `events_test`'s
  `repair-quarantined-source-rejects-still-invalid` checks). Coercion moves to the
  explicit Auto action.
- Applies to **every content type**, not just classes.
