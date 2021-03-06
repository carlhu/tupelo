;   Copyright (c) Alan Thompson. All rights reserved.
;   The use and distribution terms for this software are covered by the Eclipse Public License 1.0
;   (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at
;   the root of this distribution.  By using this software in any fashion, you are agreeing to be
;   bound by the terms of this license.  You must not remove this notice, or any other, from this
;   software.
(ns tupelo.string
  "Tupelo - Making Clojure even sweeter"
  (:refer-clojure :exclude [drop take] )
  (:require
    [clojure.core :as cc]
    [clojure.string :as str]
    [schema.core :as s]
    [tupelo.char :as char]
    [tupelo.impl :as impl]
    [tupelo.schema :as tsk]))

(defn alphanumeric?       [& args] (every? char/alphanumeric?        (impl/strcat args)))
(defn whitespace-horiz?   [& args] (every? char/whitespace-horiz?    (impl/strcat args)))
(defn whitespace-eol?     [& args] (every? char/whitespace-eol?      (impl/strcat args)))
(defn whitespace?         [& args] (every? char/whitespace?          (impl/strcat args)))
(defn lowercase?          [& args] (every? char/lowercase?           (impl/strcat args)))
(defn uppercase?          [& args] (every? char/uppercase?           (impl/strcat args)))
(defn digit?              [& args] (every? char/digit?               (impl/strcat args)))
(defn hex?                [& args] (every? char/hex?                 (impl/strcat args)))
(defn alpha?              [& args] (every? char/alpha?               (impl/strcat args)))
(defn visible?            [& args] (every? char/visible?             (impl/strcat args)))
(defn text?               [& args] (every? char/text?                (impl/strcat args)))

; #todo make general version vec -> vec; str-specific version str -> str
; #todo need (substring {:start I :stop J                 } ) ; half-open (or :stop)
; #todo need (substring {:start I :stop J :inclusive true } ) ; closed interval
; #todo need (substring {:start I :count N })

; #todo need (idx "abcdef" 2) -> [ \c ]
; #todo need (indexes "abcde" [1 3 5]) -> (mapv #(idx "abcde" %) [1 3 5]) -> [ \b \d \f ]
; #todo need (idxs    "abcde" [1 3 5]) -> (mapv #(idx "abcde" %) [1 3 5])   ; like matlab

; #todo -> tupelo.string
(defn collapse-whitespace ; #todo readme & blog
  "Replaces all consecutive runs of whitespace characters (including newlines) with a single space.
   Removes any leading or trailing whitespace. Returns a string composed of all tokens
   separated by a single space."
  [arg]
  (-> arg
    str/trim
    (str/replace #"\s+" " ")))

(s/defn equals-ignore-spacing :- s/Bool  ; #todo readme & blog
  "Compares arguments for equality using tupelo.misc/collapse-whitespace.
   Equivalent to separating tokens by whitespace and comparing the resulting sequences."
  [& args :- [s/Str]]
  (let [ws-collapsed-args (mapv collapse-whitespace args)]
    (apply = ws-collapsed-args)))

; #todo need (squash)         -> (collapse-whitespace (strcat args))       ; (smash ...)         ?
; #todo need (squash-equals?) -> (apply = (mapv squash args))              ; (smash-equals? ...)  ?
;    or (equals-base) or (equals-root) or (squash-equals) or (base-equals) or (core-equals) or (equals-collapse-string...)

(s/defn quotes->single :- s/Str ; #todo readme & blog
  [arg :- s/Str]
  (str/replace arg \" \'))

(s/defn quotes->double :- s/Str ; #todo readme & blog
  [arg :- s/Str]
  (str/replace arg \' \"))

(defn ^:deprecated ^:no-doc double-quotes->single-quotes [& args] (apply quotes->single args))
(defn ^:deprecated ^:no-doc single-quotes->double-quotes [& args] (apply quotes->double args))

; #todo need tests
(defn normalize-str
  "Returns a 'normalized' version of str-in, stripped of leading/trailing
   blanks, and with all non-alphanumeric chars converted to hyphens."
  [str-in]
  (-> str-in
    str/trim
    (str/replace #"[^a-zA-Z0-9]" "-")))
; #todo replace with other lib

; %todo define current mode only for (str->kw "ab*cd #()xyz" :sloppy), else throw
(defn str->kw-normalized       ; #todo need test, README
  "Returns a keyword constructed from a normalized string"
  [arg]
  (keyword (normalize-str arg)))

; #todo throw if bad string
(defn str->kw       ; #todo need test, README
  "Returns a keyword constructed from a normalized string"
  [arg]
  (keyword arg))

(defn kw->str       ; #todo need test, README
  "Returns the string version of a keyword, stripped of the leading ':' (colon)."
  [arg]
  (str/join (cc/drop 1 (str arg))))

(defn snake->kabob
  "Converts a string from a_snake_case_value to a-kabob-case-value"
  [arg]
  (str/replace arg \_ \- ))

(defn kabob->snake
  "Converts a string from a-kabob-case-value to a_snake_case_value"
  [arg]
  (str/replace arg \- \_ ))

(defn kw-snake->kabob [kw]
  (-> kw
    (kw->str)
    (snake->kabob)
    (str->kw)))

(defn kw-kabob->snake [kw]
  (->> kw
    (kw->str)
    (kabob->snake)
    (str->kw)))

;-----------------------------------------------------------------------------

(s/defn drop :- s/Str  ; #todo add readme
  "Drops the first N chars of a string, returning a string result."
  [n    :- s/Int
   txt  :- s/Str]
  (str/join (cc/drop n txt)))

(s/defn take :- s/Str  ; #todo add readme
  "Drops the first N chars of a string, returning a string result."
  [n    :- s/Int
   txt  :- s/Str]
  (str/join (cc/take n txt)))

(s/defn indent :- s/Str  ; #todo add readme
  "Indents a string by pre-pending N spaces. Returns a string result."
  [n    :- s/Int
   txt  :- s/Str]
  (let [indent-str (str/join (repeat n \space))]
    (str indent-str txt)))

(s/defn indent-lines :- s/Str  ; #todo add readme
  "Splits out each line of txt using clojure.string/split-lines, then
  indents each line by prepending N spaces. Joins lines together into
  a single string result, with each line terminated by a single \newline."
  [n    :- s/Int
   txt  :- s/Str]
  (str/join
    (interpose \newline
      (for [line (str/split-lines txt)]
        (str (indent n line))))))

(s/defn indent-lines-with :- s/Str  ; #todo add readme ;  need test
  "Splits out each line of txt using clojure.string/split-lines, then
  indents each line by prepending it with the supplied string. Joins lines together into
  a single string result, with each line terminated by a single \newline."
  [indent-str :- s/Str
   txt  :- s/Str]
  (impl/indent-lines-with indent-str txt))

(s/defn increasing :- s/Bool
  "Returns true if a pair of strings are in increasing lexicographic order."
  [a :- s/Str
   b :- s/Str ]
  (neg? (compare a b)))

(s/defn increasing-or-equal :- s/Bool
  "Returns true if a pair of strings are in increasing lexicographic order, or equal."
  [a :- s/Str
   b :- s/Str ]
  (or (= a b)
      (increasing a b)))

(defn index-of [search-str tgt-str]
  (.indexOf search-str tgt-str))

(defn starts-with? [search-str tgt-str]
  (zero? (index-of search-str tgt-str)))

(def phonetic-alphabet
  {:a "alpha" :b "bravo" :c "charlie" :d "delta" :e "echo" :f "foxtrot" :g "golf" :h "hotel"
   :i "india" :j "juliett" :k "kilo" :l "lima" :m "mike" :n "november" :o "oscar" :p "papa"
   :q "quebec" :r "romeo " :s "sierra" :t "tango" :u "uniform" :v "victor" :w "whiskey"
   :x "x-ray" :y "yankee" :z "zulu" } )

; #todo add undent (verify only leading whitespace removed)
; #todo add undent-lines
