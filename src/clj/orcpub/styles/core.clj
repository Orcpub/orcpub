(ns orcpub.styles.core
     (:require [garden.stylesheet :refer [at-media at-keyframes]]
               [garden.units :refer [px]]
               [orcpub.constants :as const]
               [garden.selectors :as s]))

(def orange "#f0a100")
(def button-color orange)
(def red "#9a031e")
(def green "#70a800")

(def container-style
  {:display :flex
   :justify-content :center})

(def content-style
  {:max-width (px 1440)
   :width "100%"})

(def text-color
  {:color :white})

(defn px-prop [kw abbr values]
  (map
   (fn [v]
     [(keyword (str "." (name abbr) "-" v))
      {kw (str v "px !important")}])
   values))

(def margin-lefts
  (px-prop
   :margin-left
   :m-l
   (concat (range -1 10) (range 10 55 5))))

(def margin-tops
  (px-prop
   :margin-top
   :m-t
   (concat (range 0 10) [21] (range 10 30 5))))

(def widths
  (px-prop
   :width
   :w
   [12 14 15 18 20 24 32 36 40 48 50 60 70 80 85 90 100 110 120 200 220 250 300 500 1440]))

(defn handle-browsers [property value]
  {(keyword (str "-webkit-" (name property))) value
   (keyword (str "-moz-" (name property))) value
   property value})

(def font-family "Open Sans, sans-serif")

(def font-sizes
  [[:.f-s-10
    {:font-size "10px"}]
   [:.f-s-11
    {:font-size "11px"}]
   [:.f-s-12
    {:font-size "12px !important"}]
   [:.f-s-14
    {:font-size "14px !important"}]
   [:.f-s-16
    {:font-size "16px !important"}]
   [:.f-s-18
    {:font-size "18px !important"}]
   [:.f-s-20
    {:font-size "20px !important"}]
   [:.f-s-24
    {:font-size "24px !important"}]
   [:.f-s-28
    {:font-size "28px"}]
   [:.f-s-32
    {:font-size "32px !important"}]
   [:.f-s-36
    {:font-size "36px !important"}]
   [:.f-s-48
    {:font-size "48px !important"}]])

(def props
  [[:.sans
    {:font-family font-family}]
   [:.flex
    {:display :flex}]
   [:.inline-block
    {:display :inline-block}]

   [:.flex-column
    {:flex-direction :column}]

   [:.list-style-disc
    {:list-style-type :disc
     :list-style-position :inside}]

   [:.f-w-bold
    {:font-weight :bold}]

   [:.flex-grow-1
    {:flex-grow 1}]

   [:.flex-basis-50-p
    {:flex-basis "50%"}]

   [:.i
    {:font-style :italic}]

   [:.wsp-prw
    {:white-space "pre-wrap"
     :display "block"}]

   [:.f-w-n
    {:font-weight :normal}]
   [:.f-w-b
    {:font-weight :bold}]
   [:.f-w-600
    {:font-weight 600}]

   [:.l-h-19
    {:line-height "19px"}]
   [:.l-h-20
    {:line-height "20px"}]

   [:.m-r--10
    {:margin-right "-10px"}]
   [:.m-r--5
    {:margin-right "-5px"}]
   [:.m-r-2
    {:margin-right "2px"}]
   [:.m-r-5
    {:margin-right "5px"}]
   [:.m-r-10
    {:margin-right "10px"}]
   [:.m-r-18
    {:margin-right "18px"}]
   [:.m-r-20
    {:margin-right "20px"}]
   [:.m-r-30
    {:margin-right "30px"}]

   [:.m-r-80
    {:margin-right "80px"}]

   [:.m-t--10
    {:margin-top "-10px"}]
   [:.m-t--20
    {:margin-top "-20px"}]
   [:.m-t--5
    {:margin-top "-5px"}]
   [:.m-t-2
    {:margin-top "2px"}]
   [:.m-t-20
    {:margin-top "20px"}]
   [:.m-t-30
    {:margin-top "30px"}]
   [:.m-t-40
    {:margin-top "40px"}]
   [:.m-t-21
    {:margin-top "21px"}]

   [:.opacity-0
    {:opacity 0}]
   [:.opacity-1
    {:opacity "0.1"}]
   [:.opacity-2
    {:opacity "0.2"}]
   [:.opacity-5
    {:opacity "0.5"}]
   [:.opacity-6
    {:opacity "0.6"}]
   [:.opacity-7
    {:opacity "0.7"}]
   [:.opacity-9
    {:opacity "0.9"}]

   [:.m-b--2
    {:margin-bottom "-2px"}]
   [:.m-b--1
    {:margin-bottom "-1px"}]
   [:.m-b-0-last:last-child
    {:margin-bottom "0px"}]
   [:.m-b-2
    {:margin-bottom "2px"}]
   [:.m-b-5
    {:margin-bottom "5px"}]
   [:.m-b-10
    {:margin-bottom "10px"}]
   [:.m-b-16
    {:margin-bottom "16px"}]
   [:.m-b-19
    {:margin-bottom "19px"}]
   [:.m-b-20
    {:margin-bottom "20px"}]
   [:.m-b-30
    {:margin-bottom "30px"}]
   [:.m-b-40
    {:margin-bottom "40px"}]

   [:.m-l-2
    {:margin-left "2px"}]
   [:.m-l--10
    {:margin-left "-10px"}]
   [:.m-l--5
    {:margin-left "-5px"}]
   [:.m-l-30
    {:margin-left "30px"}]

   [:.m-5
    {:margin "5px"}]

   [:.text-shadow
    {:text-shadow "1px 2px 1px black"}]

   [:.white-text-shadow
    {:text-shadow "1px 2px 1px white"}]

   [:.slight-text-shadow
    {:text-shadow "1px 1px 1px rgba(0,0,0,0.8)"}]

   [:.hover-shadow:hover :.shadow
    {:box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"}]

   [:.hover-no-shadow:hover
    {:box-shadow :none}]

   [:.hover-underline:hover
    {:text-decoration :underline}]

   [:.orange-shadow
    {:box-shadow "0 1px 0 0 #f0a100"}]

   [:.t-a-c
    {:text-align :center}]
   [:.t-a-l
    {:text-align :left}]
   [:.t-a-r
    {:text-align :right}]
   [:.justify-cont-s-b
    {:justify-content :space-between}]
   [:.justify-cont-s-a
    {:justify-content :space-around}]
   [:.justify-cont-c
    {:justify-content :center}]
   [:.justify-cont-end
    {:justify-content :flex-end}]
   [:.align-items-c
    {:align-items :center}]
   [:.align-items-t
    {:align-items :flex-start}]
   [:.align-items-end
    {:align-items :flex-end}]
   [:.flex-wrap
    {:flex-wrap :wrap}]

   [:.w-auto
    {:width :auto}]
   [:.w-10-p
    {:width "10%"}]
   [:.w-20-p
    {:width "20%"}]
   [:.w-30-p
    {:width "30%"}]
   [:.w-40-p
    {:width "40%"}]
   [:.w-50-p
    {:width "50%"}]
   [:.w-60-p
    {:width "60%"}]
   [:.w-100-p
    {:width "100%"}]

   [:.h-0
    {:height "0px"}]
   [:.h-12
    {:height "12px"}]
   [:.h-14
    {:height "14px"}]
   [:.h-15
    {:height "15px"}]
   [:.h-18
    {:height "18px"}]
   [:.h-20
    {:height "20px"}]
   [:.h-24
    {:height "24px"}]
   [:.h-25
    {:height "25px"}]
   [:.h-32
    {:height "32px"}]
   [:.h-36
    {:height "36px"}]
   [:.h-40
    {:height "40px"}]
   [:.h-48
    {:height "48px"}]
   [:.h-60
    {:height "60px"}]
   [:.h-72
    {:height "72px"}]
   [:.h-120
    {:height "120px"}]
   [:.h-200
    {:height "200px"}]
   [:.h-800
    {:height "800px"}]

   [:.h-100-p
    {:height "100%"}]

   [:.overflow-auto
    {:overflow :auto}]

   [:.posn-rel
    {:position :relative}]
   [:.posn-abs
    {:position :absolute}]
   [:.posn-fixed
    {:position :fixed}]
   [:.main-text-color
    {:color :white
     :fill :white}]
   [:.stroke-color
    {:stroke :white}]
   [:.white
    {:color :white}]
   [:.black
    {:color "#191919"}]
   [:.orange
    {:color button-color}

    [:a :a:visited
     {:color button-color}]]
   [:.green
    {:color green}

    [:a :a:visited
     {:color green}]]
   [:.red
    {:color red}

    [:a :a:visited
     {:color red}]]
   [:.uppercase
    {:text-transform :uppercase}]
   [:.bg-trans
    {:background-color :transparent}]
   [:.bg-white
    {:background-color :white}]
   [:.bg-slight-white
    {:background-color "rgba(255,255,255,0.05)"}]
   [:.no-border
    {:border :none}]

   [:.underline
    {:text-decoration :underline}]
   [:.no-text-decoration
    {:text-decoration :none}]

   [:.p-t-0
    {:padding-top "0px"}]
   [:.p-t-2
    {:padding-top "2px"}]
   [:.p-t-3
    {:padding-top "3px"}]
   [:.p-t-4
    {:padding-top "4px"}]
   [:.p-t-5
    {:padding-top "5px"}]
   [:.p-t-10
    {:padding-top "10px"}]
   [:.p-t-20
    {:padding-top "20px"}]

   [:.p-b-5
    {:padding-bottom "5px"}]
   [:.p-b-10
    {:padding-bottom "10px"}]
   [:.p-b-20
    {:padding-bottom "20px"}]
   [:.p-b-40
    {:padding-bottom "40px"}]
   [:.p-0
    {:padding "0px"}]
   [:.p-1
    {:padding "1px"}]
   [:.p-2
    {:padding "2px"}]
   [:.p-5
    {:padding "5px"}]
   [:.p-10
    {:padding "10px"}]
   [:.p-20
    {:padding "20px"}]
   [:.p-30
    {:padding "30px"}]
   [:.p-5-10
    {:padding "5px 10px"}]

   [:.p-l-0
    {:padding-left "0px"}]
   [:.p-l-5
    {:padding-left "5px"}]
   [:.p-l-10
    {:padding-left "10px"}]
   [:.p-l-15
    {:padding-left "15px"}]
   [:.p-l-20
    {:padding-left "20px"}]

   [:.p-r-5
    {:padding-right "5px"}]
   [:.p-r-10
    {:padding-right "10px"}]
   [:.p-r-20
    {:padding-right "20px"}]
   [:.p-r-40
    {:padding-right "40px"}]

   [:.b-rad-50-p
    {:border-radius "50%"}]
   [:.b-rad-5
    {:border-radius "5px"}]

   [:.b-1
    {:border "1px solid"}]
   [:.b-3
    {:border "3px solid"}]

   [:.b-b-2
    {:border-bottom "2px solid"}]

   [:.b-w-3
    {:border-width "3px"}]
   [:.b-w-5
    {:border-width "5px"}]

   [:.b-color-gray
    {:border-color "rgba(255,255,255,0.2)"}]

   [:ul.list-style-disc
    {:list-style-type :disc}]

   [:.hidden
    {:display :none}]
   [:.invisible
    {:visibility :hidden}]

   [:.tooltip
    {:position "relative"
     :display "inline-block"
     :border-bottom "1px dotted black"}]

   [:.tooltip [:.tooltiptext
               {:visibility "hidden"
                :width "130px"
                :bottom "calc(100% - -5px)"
                :left "50%"
                :margin-left "-60px"
                :background-color "black"
                :font-family "Open Sans, sans-serif"
                :font-size "14px"
                :font-weight "normal"
                :color "#fff"
                :text-align "center"
                :padding "10px 10px"
                :border-radius "6px"
                :position "absolute"
                :z-index "1"}]]

   [:.tooltip:hover [:.tooltiptext
                     {:visibility "visible"}]]

   [:.image-character-thumbnail
    {:max-height "100px"
     :max-width "200px"
     :border-radius "5px"}]
   
   [:.image-faction-thumbnail
    {:max-height "100px"
     :max-width "200px"
     :border-radius "5px"}]

   (at-keyframes
    :fade-out
    [:from {:opacity 1
            :height "100%"}]
    [:50% {:opacity 0
           :height "100%"}]
    [:to {:height "0%"}])

   [:.pointer
    {:cursor :pointer}]
   [:.cursor-disabled
    {:cursor :not-allowed}]

   [:.c-f4692a
    {:color "#f4692a"}]
   [:.c-f32e50
    {:color "#f32e50"}]
   [:.c-b35c95
    {:color "#b35c95"}]
   [:.c-47eaf8
    {:color "#47eaf8"}]
   [:.c-bbe289
    {:color "#bbe289"}]
   [:.c-f9b747
    {:color "#f9b747"}]

   [:.b-orange
    {:border-color button-color}]
   [:.b-red
    {:border-color red}]
   [:.b-gray
    {:border-color "rgba(72,72,72,0.37)"}]

   [:.hover-slight-white:hover
    {:background-color "#2c3445"
     :opacity 0.2}]

   [:.hover-opacity-full:hover
    {:opacity 1.0}]

   [:.bg-light
    {:background-color "rgba(72,72,72,0.2)"}]
   [:.bg-lighter
    {:background-color "rgba(0,0,0,0.15)"}]
   [:.bg-orange
    {:background-color orange}]
   [:.bg-red
    {:background-color red}]
   [:.bg-green
    {:background-color "#70a800"}]

   [:.fade-out
    {:animation-name :fade-out
     :animation-duration :5s}]

   [:.no-appearance
    (handle-browsers :appearance :none)]])

(def xs-min "0")
(def sm-min "768px")
(def sm-max "991px")
(def md-max "1199px")

(def xs-query
  {:max-width "767px"})

(def sm-query
  {:min-width sm-min :max-width sm-max})

(def md-min "992px")

(def md-query
  {:min-width md-min :max-width md-max})

(def sm-or-md-query
  {:min-width sm-min :max-width md-max})

(def xs-or-sm-query
  {:min-width xs-min :max-width sm-max})


(def lg-min "1200px")

(def lg-query
  {:min-width lg-min})

(def not-lg-query
  {:max-width md-max})

(def not-xs-query
  {:min-width sm-min})

(def media-queries
  [[:.visible-xs,
    :.visible-sm,
    :.visible-md,
    :.visible-lg
    {:display "none !important"}]

   [:.visible-xs-block,
    :.visible-xs-inline,
    :.visible-xs-inline-block,
    :.visible-sm-block,
    :.visible-sm-inline,
    :.visible-sm-inline-block,
    :.visible-md-block,
    :.visible-md-inline,
    :.visible-md-inline-block,
    :.visible-lg-block,
    :.visible-lg-inline,
    :.visible-lg-inline-block 
    {:display "none !important"}]

   (at-media xs-query
    [:.visible-xs {:display "block !important"}]
    [:table.visible-xs {:display "table !important"}]
    [:tr.visible-xs {:display "table-row !important"}]
    [:th.visible-xs,
     :td.visible-xs {:display "table-cell !important"}])

   (at-media xs-query
    [:.visible-xs-block
     {:display "block !important"}])
   (at-media xs-query [
                                   :.visible-xs-inline {
                                                        :display "inline !important"
                                                        }
                                   ])
   (at-media xs-query [
                                   :.visible-xs-inline-block {
                                                              :display "inline-block !important"
                                                              }
                                   ])
   (at-media sm-query [
                                                          :.visible-sm {
                                                                        :display "block !important"
                                                                        }
                                                          :table.visible-sm {
                                                                             :display "table !important"
                                                                             }
                                                          :tr.visible-sm {
                                                                          :display "table-row !important"
                                                                          }
                                                          :th.visible-sm,
                                                          :td.visible-sm {
                                                                          :display "table-cell !important"
                                                                          }
                                                          ])
   (at-media sm-query [
                                                          :.visible-sm-block {
                                                                              :display "block !important"
                                                                              }
                                                          ])
   (at-media sm-query [
                                                          :.visible-sm-inline {
                                                                               :display "inline !important"
                                                                               }
                                                          ])
   (at-media sm-query [
                                                          :.visible-sm-inline-block {
                                                                                     :display "inline-block !important"
                                                                                     }
                                                          ])
   (at-media md-query [
                                                           :.visible-md {
                                                                         :display "block !important"
                                                                         }
                                                           :table.visible-md {
                                                                              :display "table !important"
                                                                              }
                                                           :tr.visible-md {
                                                                           :display "table-row !important"
                                                                           }
                                                           :th.visible-md,
                                                           :td.visible-md {
                                                                           :display "table-cell !important"
                                                                           }
                                                           ])
   (at-media md-query [
                                                           :.visible-md-block {
                                                                               :display "block !important"
                                                                               }
                                                           ])
   (at-media md-query [
                                                           :.visible-md-inline {
                                                                                :display "inline !important"
                                                                                }
                                                           ])
   (at-media md-query [
                                                           :.visible-md-inline-block {
                                                                                      :display "inline-block !important"
                                                                                      }
                                                           ])
   (at-media lg-query [
                                    :.visible-lg {
                                                  :display "block !important"
                                                  }
                                    :table.visible-lg {
                                                       :display "table !important"
                                                       }
                                    :tr.visible-lg {
                                                    :display "table-row !important"
                                                    }
                                    :th.visible-lg,
                                    :td.visible-lg {
                                                    :display "table-cell !important"
                                                    }
                                    ])
   (at-media  [
                                    :.visible-lg-block {
                                                        :display "block !important"
                                                        }
                                    ])
   (at-media lg-query [
                                    :.visible-lg-inline {
                                                         :display "inline !important"
                                                         }
                                    ])
   (at-media lg-query [
                                    :.visible-lg-inline-block {
                                                               :display "inline-block !important"
                                                               }
                                    ])
   (at-media xs-query [
                                   :.hidden-xs {
                                                :display "none !important"
                                                }
                                   ])
   (at-media sm-query [
                                                          :.hidden-sm {
                                                                       :display "none !important"
                                                                       }
                                                          ])
   (at-media md-query [
                                                           :.hidden-md {
                                                                        :display "none !important"
                                                                        }
                                                           ])
   (at-media lg-query [
                                    :.hidden-lg {
                                                 :display "none !important"
                                                 }
                                    ])
   [:.visible-print
    :display "none !important"
    ]
   (at-media
    {:print true}
    [:.visible-print
     {:display "block !important"}]
    [:th.visible-print,
     :td.visible-print
     {:display "table-cell !important"}]
    [:table.visible-print
     {:display "table !important"}]
    [:tr.visible-print
     {:display "table-row !important"}])
   [:.visible-print-block
    {:display "none !important"}]
   
   (at-media
    {:print true}
    [:.visible-print-block
     {:display "block !important"}])
   [:.visible-print-inline
    {:display "none !important"}]
   (at-media
    {:print true}
    [:.visible-print-inline
     {:display "inline !important"}
     ])
   [:.visible-print-inline-block
    {:display "none !important"}]
   (at-media
    {:print true}
    [:.visible-print-inline-block
     {:display "inline-block !important"}])
   (at-media
    {:print true}
    [:.hidden-print
     {:display "none !important"}])
   
   (at-media
    xs-query
    [:.user-icon
     {:display :none}]
    [:.character-builder-header
     #_{:margin-bottom 0}]
    [:.list-character-summary
     {:font-size "18px"}]
    [:.character-summary
     {:flex-wrap :wrap}]
    [:.app-header
     {:height :auto
      :background-image :none
      :background-color "rgba(0, 0, 0, 0.3)"
      :min-height 0}]
    [:.app-header-bar
     {:min-height (px 50)
      :backdrop-filter :none
      :-webkit-backdrop-filter :none}]
    [:.app-header-menu
     {:flex-grow 1}]
    [:.content
     {:width "100%"}]
    #_[:.options-column
       {:width "100%"}]
    [:.header-button-text :.header-links
     {:display :none}])

    #_(at-media
     xs-query
     [:.build-tab
      {:display :none}]
     [:.options-tab-active
      [:.options-column
       {:display :none}]
      [:.options-column
       {:display :block}]
      [:.personality-column
       {:display :none}]
      [:.details-column
       {:display :none}]]
     [:.personality-tab-active
      [:.options-column
       {:display :none}]
      [:.personality-column
       {:display :block}]
      [:.details-column
       {:display :none}]]
     [:.details-tab-active
      [:.options-column
       {:display :none}]
      [:.personality-column
       {:display :none}]
      [:.details-column
       {:display :block}]])

    #_(at-media
     sm-or-md-query
     [:.build-tab
      {:display :block}]
     [:.options-tab
      {:display :none}]
     [:.personality-tab
      {:display :none}]
     [:.build-tab-active
      [:.options-column
       {:display :block}]
      [:.stepper-column
       {:display :block}]
      [:.personality-column
       {:display :block}]
      [:.details-column
       {:display :none}]]
     [:.details-tab-active
      [:.options-column
       {:display :none}]
      [:.personality-column
       {:display :none}]
      [:.details-column
       {:display :block}]])
    

    (at-media
     not-xs-query
     #_[:.details-columns
      {:display :flex}]
     #_[:.details-column-2
      {:margin-left "40px"}])

    (at-media
     not-lg-query
     [:.registration-image
      {:display :none}]
     [:.registration-content
      {:width "100%"
       :height "100%"}]
     [:.registration-input
      {:width "100%"}])

    #_(at-media
     lg-query
     [:.builder-column
      {:display :block}]
     [:.details-column
      {:max-width "500px"}])])

(def app
  (concat
   [[:.character-builder-header
     {:margin-bottom "19px"}]

    [:.senses
     {:width "450px"}]

    [:.notes
     {:width "350px"}]

    [:.registration-content
     {:width "785px"
      :min-height "600px"}]

    [:.registration-input
     {:min-width "438px"}]

    [:p
     {:margin "10px 0"}]

    #_["input::-webkit-outer-spin-button"
       "input::-webkit-inner-spin-button"
       {:-webkit-appearance :none
        :margin 0}]

    #_["input[type=number]"
       {:-moz-appearance :textfield}]

    [:a :a:visited
     {:color orange}]

    [:select
     {:font-family font-family
      :color "white"
      :background-color :transparent}]

    [:*:focus
     {:outline 0}]

    [:.sticky-header
     {:top 0
      :box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"
      :z-index 100
      :display :none
      :background-color "#313A4D"}]

    [:.container
     container-style]

    [:.content
     (merge
      content-style)]

    [:.app-header
     {:background-color :black
      :background-image "url(/../../image/header-background.jpg)"
      :background-position "right center"
      :background-size "cover"
      :height (px const/header-height)}]

    [:.header-tab
     {:background-color "rgba(0, 0, 0, 0.5)"
      :-webkit-backdrop-filter "blur(3px)"
      :backdrop-filter "blur(3px)"
      :border-radius "5px"
      }]

    [:.header-tab.mobile
     [:.title
      {:display :none}]
     [:img
      {:height "24px"
       :width "24px"}]
     {:width "30px"}]

    [:.item-list
     {:border-top "1px solid rgba(255,255,255,0.5)"}]

    [:.item-list-item
     {:border-bottom "1px solid rgba(255,255,255,0.5)"}]

    #_[:.header-tab:hover
       [(garden.selectors/& (garden.selectors/not :.disabled))
        {:background-color orange}]]

    [:.app-header-bar
     {:min-height (px 81)
      ;;:-webkit-backdrop-filter "blur(5px)"
      ;;:backdrop-filter "blur(5px)"
      :background-color "rgba(0, 0, 0, 0.25)"}]

    #_[:.options-column
       {:width "300px"}]

    [:.builder-column
     {:display :none
      :margin "0 5px"}]

    [:.stepper-column
     {:margin-right "-10px"}]

    [:table.striped
     [:tr
      [(s/& (s/nth-child :even))
       {:background-color "rgba(255, 255, 255, 0.1)"}]]]

    [:.builder-option
     {:border-width (px 1)
      :border-style :solid
      :border-color "rgba(255, 255, 255, 0.5)"
      :border-radius (px 5)
      :padding (px 10)
      :margin-top (px 5)
      :font-weight :normal}]

    [:.builder-tabs
     {:display :flex
      :padding "10px"
      :text-transform :uppercase
      :font-weight 600}]

    [:.builder-tab
     {:flex-grow 1
      :padding-bottom "13px"
      :text-align :center
      :cursor :pointer
      :border-bottom "5px solid rgba(72,72,72,0.37)"}
     [:.builder-tab-text
      {:opacity 0.2}]]

    [:.selected-builder-tab
     {:border-bottom-color "#f1a20f"}
     [:.builder-tab-text
      {:opacity 1}]]

    [:.collapsed-list-builder-option
     {:padding "1px"}]

    [:.disabled-builder-option
     {:color "rgba(255, 255, 255, 0.5)"
      :border-color "rgba(255, 255, 255, 0.25)"
      :cursor :auto}]

    [:.selectable-builder-option:hover
     {:border-color "#f1a20f"
      :box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"
      :cursor :pointer}]

    [:.builder-selector
     {:padding (px 5)
      :font-size (px 14)
      :margin-top (px 10)}]

    [:.builder-selector-header
     {:font-size (px 18)
      :font-weight :normal}]

    [:.builder-option-dropdown
     (merge
      {:background-color :transparent
       :width "100%"
       :cursor :pointer
       :border "1px solid white"}
      text-color
      (handle-browsers :appearance :menulist))

     [:&:active :&:focus
      {:outline :none}]]

    [:.builder-dropdown-item
     {:-webkit-appearance :none
      :-moz-appearance :none
      :appearance :none
      :background-color :black}]

    [:.selected-builder-option
     {:border-width (px 3)
      :border-color :white
      :font-weight :bold}]

    [:.remove-item-button
     {:color button-color
      :font-size "16px"
      :margin-left "5px"
      :cursor :pointer}]

    [:.add-item-button
     {:margin-top "19px"
      :color button-color
      :font-weight 600
      :text-decoration :underline
      :cursor :pointer}]

    [:.list-selector-option
     {:display :flex
      :align-items :center}]

    [:.expand-collapse-button
     {:font-size "12px"
      :max-width "100px"
      :margin-left "10px"
      :color "#f0a100"
      :text-decoration :underline
      :cursor :pointer
      :text-align :right}]

    [:.fa-caret-square-o-down
     {:color button-color}]

    [:.expand-collapse-button:hover
     {:color button-color}]

    [:.abilities-polygon
     {:transition "points 2s"
      :-webkit-transition "points 2s"}]

    [:.display-section-qualifier-text
     {:font-size "12px"
      :margin-left "5px"}]

    [:.form-button
     {:color :white
      :font-weight 600
      :font-size "12px"
      :border :none
      :border-radius "5px"
      :text-transform :uppercase
      :padding "10px 15px"
      :cursor :pointer
      :background-image "linear-gradient(to bottom, #f1a20f, #dbab50)"}]

    [:.roll-button
     {:color :white
      :min-width "68px"
      :font-weight 600
      :font-size "14px"
      :border :none
      :border-radius "2px"
      :padding "6px 6px"
      :margin-right "2px"
      :margin-left "2px"
      :margin-bottom "2px"
      :margin-top "2px"
      :cursor :pointer
      :background-image "linear-gradient(to bottom, #f1a20f, #dbab50)"}]

    [:.form-button:hover
     {:box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"}]

    [:.form-button.disabled
     {:opacity 0.5
      :cursor :not-allowed
      :pointer-events "none"}]

    [:.form-button.disabled:hover
     {:box-shadow :none}]

    [:.link-button
     {:color button-color
      :border :none
      :background-color :transparent
      :text-transform :uppercase
      :cursor :pointer
      :font-size "12px"
      :border-radius "5px"
      :padding "10px 15px"
      :text-decoration :underline}]

    [:.link-button.disabled
     {:opacity 0.5
      :cursor :not-allowed}]

    [:.field
     {:margin-top "30px"}]

    [:.field-label
     {:font-size "14px"}]

    [:.personality-label
     {:font-size "18px"}]

    [:.input
     {:background-color :transparent
      :color :white
      :border "1px solid white"
      :border-radius "5px"
      :margin-top "5px"
      :display :block
      :padding "10px"
      :width "100%"
      :box-sizing :border-box
      :font-size "14px"}]

    [:.checkbox-parent
     {:display :flex
      :padding "11px 0"
      :align-items :center}]

    [:.checkbox
     {:width "16px"
      :height "16px"
      :box-shadow "0 1px 0 0 #f0a100"
      :background-color :white
      :cursor :pointer}

     [:.fa-check
      {:font-size "14px"
       :margin "1px"}]]

    [:.checkbox.checked.disabled
     {:background-color "rgba(255, 255, 255, 0.37)"
      :cursor :not-allowed}]

    [:.checkbox-text
     {:margin-left "5px"}]

    [:#selection-stepper
     {:transition "top 2s ease-in-out"
      :width "240px"
      :position :relative
      :top 0}]

    [:.selection-stepper-inner
     {:position :absolute}]

    [:.selection-stepper-main
     {:width "200px"
      :border "1px solid white"
      :border-radius "5px"
      :padding "10px"
      :background-color "#1a1e28"
      :box-shadow "0 2px 6px 0 rgba(0, 0, 0, 0.5)"}]

    [:.selection-stepper-title
     {:font-size "18px"
      :color "#f0a100"}]

    [:.selection-stepper-help
     {:font-size "14px"
      :font-weight 100}]

    [:.selection-stepper-footer
     {:justify-content :flex-end}]

    [:.option-header
     {:display :flex
      :justify-content :space-between
      :align-items :center}]

    [:.app.light-theme
     {:background-image "linear-gradient(182deg, #FFFFFF, #DDDDDD)"}

     [:select
      {:font-family font-family
       :color "black";
       :background-color :transparent}]

     [:.item-list
      {:border-top "1px solid rgba(0,0,0,0.5)"}]

     [:.link-button
      {:color "#363636"}]

     [:.item-list-item
      {:border-bottom "1px solid rgba(0,0,0,0.5)"}]

     [:.main-text-color
      {:color "#363636"
       :fill "#363636"}]
     [:.stroke-color
      {:stroke "#363636"}]

     [:.input
      {:background-color :transparent
       :color :black
       :border "1px solid #282828"
       :border-radius "5px"
       :margin-top "5px"
       :display :block
       :padding "10px"
       :width "100%"
       :box-sizing :border-box
       :font-size "14px"}]

     [:.form-button
      {:background-image "linear-gradient(to bottom, #33658A, #33658A)"}]

     [:.orange
      {:color "rgba(0,0,0,0.8)"}]

     [:.b-orange
      {:border-color "rgba(0,0,0,0.6)"}]

     [:.text-shadow
      {:text-shadow :none}]

     [:.bg-light
      {:background-color "rgba(0,0,0,0.4)"}]
     [:.bg-lighter
      {:background-color "rgba(0,0,0,0.15)"}]

     [:.b-color-gray
      {:border-color "rgba(0,0,0,0.3)"}]

     [:.builder-option-dropdown
      (merge
       {:border "1px solid #282828"
        :color "#282828"})

      [:&:active :&:focus
       {:outline :none}]]

     [:.builder-dropdown-item
      {:background-color :white
       :color "#282828"}]

     [:.sticky-header
      {:background-color :white}]

     [:table.striped
      [:tr
       [(s/& (s/nth-child :even))
        {:background-color "rgba(0, 0, 0, 0.1)"}]]]]]
   margin-lefts
   margin-tops
   widths
   font-sizes
   props
   media-queries))
