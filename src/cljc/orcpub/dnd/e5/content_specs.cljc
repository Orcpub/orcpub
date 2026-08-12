(ns orcpub.dnd.e5.content-specs
  "Single source of truth tying SAVE and LOAD validation together: each homebrew
   content-type keyword → its STRICT per-type save spec, plus the LOOSE floor
   every saved item must still satisfy on load.

   Save and load used to name their specs independently, so they could DRIFT: if
   load ever grew stricter than save, already-saved content would be quarantined
   on the next boot. One generative test (`content_specs_test`) proves the
   invariant `save ⊆ load` — anything a save spec accepts, the load floor accepts.

   LOAD stays deliberately loose (`:option-pack` string + letter-leading key, see
   `::e5/homebrew-item`) for backward compat: never reject real saved content just
   because a builder's save spec later tightened."
  (:require #?(:clj [clojure.spec.alpha :as spec]
               :cljs [cljs.spec.alpha :as spec])
            [orcpub.common :as common]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.backgrounds :as backgrounds]
            [orcpub.dnd.e5.feats :as feats]
            [orcpub.dnd.e5.languages :as languages]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.encounters :as encounters]
            [orcpub.dnd.e5.selections :as selections]))

(def save-specs
  "content-type keyword → the strict spec used to validate an item on SAVE.
   The single source consumed by `reg-save-homebrew` (events.cljs) and the
   `save ⊆ load` drift test. Keep in lockstep with the save handlers: every
   content type that has a builder/save handler appears here exactly once."
  {::e5/spells       ::spells/homebrew-spell
   ::e5/monsters     ::monsters/homebrew-monster
   ::e5/encounters   ::encounters/encounter
   ::e5/backgrounds  ::backgrounds/homebrew-background
   ::e5/languages    ::languages/homebrew-language
   ::e5/invocations  ::classes/homebrew-invocation
   ::e5/boons        ::classes/homebrew-boon
   ::e5/selections   ::selections/homebrew-selection
   ::e5/feats        ::feats/homebrew-feat
   ::e5/races        ::races/homebrew-race
   ::e5/subraces     ::races/homebrew-subrace
   ::e5/subclasses   ::classes/homebrew-subclass
   ::e5/classes      ::classes/homebrew-class})

(def load-item-spec
  "The loose floor a saved item must still satisfy to be KEPT (not quarantined)
   on load. Intentionally minimal — an `:option-pack` string — so real content is
   never falsely quarantined. The drift test proves every `save-specs` value is a
   subset of this."
  ::e5/homebrew-item)

(defn save-spec-for
  "The strict save spec for a content-type keyword, or nil if the type has no
   builder (e.g. a type that only ever arrives via import)."
  [content-type]
  (get save-specs content-type))

(defn valid-for-load?
  "The load acceptance predicate — a source is KEPT iff it satisfies the loose
   `::e5/plugin` shape (every item meets `load-item-spec`, keys are letter-leading
   content keywords). This is the single predicate the resilient loader injects
   into `e5/salvage-plugins`, so 'what counts as loadable' lives in one place."
  [plugin]
  (spec/valid? ::e5/plugin plugin))

(defn valid-item-for-load?
  "The load floor for a SINGLE homebrew item: a letter-leading key and an item
   that satisfies the loose `load-item-spec` (`::e5/homebrew-item` — a map with an
   `:option-pack` string). The per-item mirror of `valid-for-load?`, injected into
   `e5/salvage-library-items` for PER-ENTRY salvage: a source keeps exactly the
   items a whole-plugin load would have kept, and only the broken ones are set
   aside — instead of one bad item quarantining the entire source."
  [_content-type item-key item]
  (and (common/keyword-starts-with-letter? item-key)
       (spec/valid? load-item-spec item)))
