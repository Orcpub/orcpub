# ClojureScript's quiet type tolerance — what it hides, and what it crashes on

If you come to this codebase from JVM Clojure (or from a strongly-typed language),
ClojureScript will surprise you in one consistent way: it is **far more forgiving
of bad or missing data than you expect, right up until the moment it isn't.** Most
of the time a `nil` where you expected a number, or a number where you expected a
string, flows through quietly and produces a wrong-but-not-crashing result. Then,
in a few specific situations, the exact same kind of bad data throws a hard runtime
error and (without an error boundary) blanks the page.

Knowing which situations are which saves a lot of debugging time. It also helps you
notice the *more dangerous* case — the silent one — because a crash at least tells
you something is wrong, whereas silent tolerance can let a bug live in production
for a long time.

Everything below was checked by running it in the live app, not assumed.

## The short version

- **Arithmetic, comparisons, and collection access usually do NOT throw on `nil` or
  wrong types.** They coerce or treat `nil` as "empty/zero/false" and keep going.
- **String operations DO throw on non-strings** (including `nil`). This is the most
  common real crash, and the character-sheet black screen was an instance of it.
- A couple of other operations (`name`, out-of-range `nth`) also throw.

## What it tolerates — and therefore can hide

These all return a value instead of throwing. That is convenient, but it means a
`nil` that should never have been there can travel a long way through your code,
quietly turning into `0`, `""`, or "nothing rendered", before anyone notices.

| Expression | Result | Why |
|---|---|---|
| `(+ nil 1)` | `1` | JavaScript coerces `null` to `0`, so the math just works |
| `(* nil 3)` | `0` | same coercion |
| `(inc nil)` | `1` | same |
| `(< nil 5)` | `true` | `nil` compares as `0` |
| `(pos? nil)` | `false` | treated as not-positive rather than an error |
| `(count nil)` | `0` | `nil` is treated as an empty collection |
| `(get nil :k)` / `(:k nil)` | `nil` | reading a key out of `nil` is allowed |
| `(first nil)` / `(nth nil 2)` | `nil` | sequence functions treat `nil` as empty |
| `(str nil)` | `""` | `str` is defined to turn `nil` into an empty string |
| `(blank? nil)` / `(blank? 5)` | `true` / `false` | `clojure.string/blank?` guards its input |
| `(keyword 5)` | `nil` | returns `nil` rather than complaining |
| `(/ 1 0)` | `##Inf` | JavaScript division gives Infinity, not an error |

**Two reasons this happens.** First, ClojureScript's arithmetic and comparison
compile down to JavaScript operators, and JavaScript quietly coerces `null` to `0`
and never throws on divide-by-zero. Second, many Clojure core functions are written
to "nil-pun" — to treat `nil` as an empty collection — so `count`, `seq`, `get`,
`first`, and friends all have a sensible answer for `nil` instead of an error.

The practical warning: **"it didn't crash" does not mean "the data was correct."** A
section that renders a `0`, a blank, or an empty list may be hiding a `nil` that
should have been caught upstream.

## What it chokes on — and why

These throw a real runtime error. In a React tree with no error boundary above
them, that error unmounts the component and you get a blank (black) screen.

| Expression | Throws | Why |
|---|---|---|
| `(lower-case nil)` | `Cannot read properties of null` | calls `null.toLowerCase()` |
| `(lower-case 5)` | `s.toLowerCase is not a function` | numbers have no `.toLowerCase` |
| `(subs nil 0 1)` | `Cannot read properties of null` | calls `null.substring(...)` |
| `(name nil)` / `(name 5)` | `Doesn't support name` | ClojureScript explicitly rejects these |
| `(nth [1] 9)` | `No item 9 in vector of length 1` | out-of-range index on a real vector |

**The common thread is JavaScript interop.** Functions in `clojure.string`
(`lower-case`, `upper-case`, `capitalize`, `subs`, `split`, …) are thin wrappers
that call a JavaScript *string method* — `.toLowerCase()`, `.substring()`, and so on
— on their argument. Those methods only exist on real strings. Hand them a `nil` or
a number and JavaScript throws `"... is not a function"` or
`"Cannot read properties of null"`. The value didn't have the method, so the call
fails immediately.

`name` is a little different: ClojureScript has an explicit check and deliberately
throws `"Doesn't support name"` for anything that isn't a keyword, symbol, or
string. And `nth` on a genuine collection (not `nil`) throws when the index is out
of range — though note two escape hatches: `nth` on `nil` returns `nil`, and
`(nth coll i default)` returns the default instead of throwing.

## Why this matters here specifically

The character-sheet "black screen" bug was exactly this pattern. A feature whose
`:name` was `nil` reached a sort (`clojure.string/lower-case` is used as the sort
key), `lower-case` was handed a `nil`, it threw, and — with no error boundary — the
whole view unmounted. Every other part of the render had tolerated the missing data
silently; the string operation was the one place it turned into a crash.

So when you are hunting a render crash in this codebase, **suspect a string
operation receiving a non-string first** — most often a sort or display helper in
`clojure.string`, fed a value that was supposed to be a name or label but came
through `nil`. And when you are hunting a *wrong-but-not-crashing* display, suspect
the opposite: a `nil` that got silently turned into `0`/`""`/nothing somewhere
upstream and was never noticed.

## What to do about it

Two small habits cover almost all of this.

**For string ops on data that might not be a string — coerce in one shared place.**
Don't sprinkle `(str x)` at every call. We have `common/lower-case`, a thin wrapper
that does `(clojure.string/lower-case (str x))`, so a nil or number folds to `""`
instead of crashing. Use it (or `aloof-sort-by`, which is built on it) for sort and
compare keys. Don't redefine the core `lower-case` itself — overriding a core
function globally is surprising and hard to trace; a wrapper with its own name is
clearer.

**For a missing value that gets shown — use an obvious placeholder, not a blank.**
Silent tolerance is the real trap: a `nil` that quietly becomes `""` hides the
problem. When the value is displayed, render something visible instead — that's why
`common/feature-name` turns a missing name into `[Unnamed feature]`. The user sees
the broken item in place, and you didn't have to crash to surface it. For a value
that's the *wrong type* (a number where a name belongs), that's a real bug, so
`feature-name` throws in dev (you catch it immediately) and coerces in prod (users
keep working).

## How to check a specific case yourself

Don't guess — the rules above are easy to confirm. In the dev build the
ClojureScript runtime is reachable from the browser console, so you can try the
exact expression:

```js
// in the page console (dev build)
try { clojure.string.lower_case.call(null, null); console.log('ok'); }
catch (e) { console.log('throws:', e.message); }
```

That is how the table above was produced — by running each expression against the
real compiled runtime rather than reasoning about what *should* happen.
