;   Copyright (c) Alan Thompson. All rights reserved.
;   The use and distribution terms for this software are covered by the Eclipse Public License 1.0
;   (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at
;   the root of this distribution.  By using this software in any fashion, you are agreeing to be
;   bound by the terms of this license.  You must not remove this notice, or any other, from this
;   software.
(ns tupelo.core
  "Tupelo - Making Clojure even sweeter"
  (:require 
    [clojure.core.async :as ca]
    [clojure.core.match :as ccm]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.set :as set]
    [clojure.test]
    [cheshire.core :as cc]
    [potemkin.namespaces :as pns]
    [schema.core :as s]
    [tupelo.impl :as impl]
    [tupelo.string :as tstr]
    [tupelo.types :as types]
    [tupelo.schema :as ts]
  )
  (:refer-clojure :exclude [map seqable?] )
  (:import [java.io BufferedReader StringReader]))

; #todo need (defkw :fred) and (kw :fred) to catch errors like
; (when (= person :frid)  ; (kw :frid) -> throws
;    (println "Hi Barney!"))

(pns/import-fn impl/nl )
(pns/import-fn impl/xfirst )
(pns/import-fn impl/xrest )
(pns/import-fn impl/vec->list )

(pns/import-fn impl/kw->sym )
(pns/import-fn impl/kw->str )
(pns/import-fn impl/str->kw )
(pns/import-fn impl/str->sym )
(pns/import-fn impl/sym->kw )
(pns/import-fn impl/sym->str )


(defn print-versions []
  (let [version-str   (format "   Clojure %s    Java %s"
                        (clojure-version) (System/getProperty "java.version")) ]
    (nl)
    (println "-------------------------------------")
    (println version-str)
    (println "-------------------------------------")))

; #todo add schema; result = ts/List[ ts/Pair ]
; #todo add :trunc & assert;  add :lazy
(defn zip [& args]
  "Zips together vectors like zipmap (like Python zip):
   
     (zip [:a :b :c] [1 2 3]) ->  [ [:a 1] [:b 2] [:c 3] ]     

   Use (zip ... :trunc) if you want to truncate all inputs to the lenght of the shortest.
   Use (zip ... :lazy)  if you want it to be lazy.  "
  (apply mapv vector args))

(pns/import-macro impl/forv)

; #todo maybe just make tupelo.vec/for  etc   (tv/for ...) -> (vec (for ...))
; #todo replace clojure.core/map : not lazy; can add one of :trunc or :lazy modifiers
; (map + (range 5))
; (map + 0 (range 5))
; (map + (range 5) :lazy)
; (map vector [:a :b :c] (range 9) :trunc)  ; error w/o :trunc
(defn mapper [& args]   ; alts:  mapr  onto  morph  vmap
  "An eager version of clojure.core/map
   Use (zip ... :trunc) if you want to truncate all inputs to the lenght of the shortest.
   Use (zip ... :lazy)  if you want it to be lazy.  "
  (apply clojure.core/map args))
; #todo (map-indexed ... :lazy)   vmap-indexed
; #todo (mapcat ... :lazy)    vmapcat
; #todo (for ... :lazy)       vfor
; #todo (concat ... :lazy)    vconcat

(pns/import-fn impl/map-keys->vals )
(pns/import-fn impl/fetch-in)
(pns/import-fn impl/grab)
(pns/import-fn impl/submap-by-keys )
(pns/import-fn impl/submap-by-vals )

(s/defn increasing? :- s/Bool
  "Returns true iff the vectors are in (strictly) lexicographically increasing order
    [1 2]  [1]        -> false
    [1 2]  [1 1]      -> false
    [1 2]  [1 2]      -> false
    [1 2]  [1 2 nil]  -> true
    [1 2]  [1 2 3]    -> true
    [1 2]  [1 3]      -> true
    [1 2]  [2 1]      -> true
    [1 2]  [2]        -> true
  "
  [a :- ts/List
   b :- ts/List]
  (let [len-a        (count a)
        len-b        (count b)
        cmpr         (fn [x y] (cond
                                 (= x y) :eq
                                 (< x y) :incr
                                 (> x y) :decr
                                 :else (throw (IllegalStateException. "should never get here"))))
        cmpr-res     (mapv cmpr a b)
        first-change (first (drop-while #{:eq} cmpr-res)) ; nil if all :eq
        ]
    (cond
      (= a b)                       false
      (= first-change :decr)        false
      (= first-change :incr)        true
      (nil? first-change)           (< len-a len-b))))

(s/defn increasing-or-equal? :- s/Bool
  "Returns true iff the vectors are in (strictly) lexicographically increasing order
    [1 2]  [1]        -> false
    [1 2]  [1 1]      -> false
    [1 2]  [1 2]      -> true
    [1 2]  [1 2 nil]  -> true
    [1 2]  [1 2 3]    -> true
    [1 2]  [1 3]      -> true
    [1 2]  [2 1]      -> true
    [1 2]  [2]        -> true
  "
  [a :- ts/List
   b :- ts/List]
  (or (= a b)
    (increasing? a b)))

;-----------------------------------------------------------------------------
; Java version stuff

(s/defn java-version :- s/Str
  []
  (System/getProperty "java.version"))

(s/defn java-version-matches? :- s/Bool
  "Returns true if Java version exactly matches supplied string."
  [version-str :- s/Str]
  (tstr/starts-with? (java-version) version-str))

(s/defn java-version-min? :- s/Bool
  "Returns true if Java version is at least as great as supplied string.
  Sort is by lexicographic (alphabetic) order."
  [version-str :- s/Str]
  (tstr/increasing-or-equal version-str (java-version)))

(defn is-java-1-7? [] (java-version-matches? "1.7"))
(defn is-java-1-8? [] (java-version-matches? "1.8"))

(defn is-java-1-7-plus? [] (java-version-min? "1.7"))
(defn is-java-1-8-plus? [] (java-version-min? "1.8"))

(defmacro if-java-1-7-plus
  "If JVM is Java 1.7 or higher, evaluates if-form into code. Otherwise, evaluates else-form."
  [if-form else-form]
  (if (is-java-1-7-plus?)
    `(do ~if-form)
    `(do ~else-form)))

(defmacro if-java-1-8-plus
  "If JVM is Java 1.8 or higher, evaluates if-form into code. Otherwise, evaluates else-form."
  [if-form else-form]
  (if (is-java-1-8-plus?)
      `(do ~if-form)
      `(do ~else-form)))

; #todo need min-java-1-8  ???

;-----------------------------------------------------------------------------
; Clojure version stuff

(defn is-clojure-1-7-plus? []
  (increasing-or-equal? [1 7] (map-keys->vals *clojure-version* [:major :minor ])))

(defn is-clojure-1-8-plus? []
  (increasing-or-equal? [1 8] (map-keys->vals *clojure-version* [:major :minor ])))

(defn is-clojure-1-9-plus? []
  (increasing-or-equal? [1 9] (map-keys->vals *clojure-version* [:major :minor ])))

(defn is-pre-clojure-1-8? [] (not (is-clojure-1-8-plus?)))
(defn is-pre-clojure-1-9? [] (not (is-clojure-1-9-plus?)))


; #todo add is-clojure-1-8-max?
; #todo need clojure-1-8-plus-or-throw  ??

(defmacro when-clojure-1-8-plus
  "Wraps code that should only be included for Clojure 1.8 or higher.  Otherwise, code is supressed."
  [& forms]
  (if (is-clojure-1-8-plus?)
    `(do ~@forms)))

(defmacro when-clojure-1-9-plus
  "Wraps code that should only be included for Clojure 1.9 or higher.  Otherwise, code is supressed."
  [& forms]
  (if (is-clojure-1-9-plus?)
    `(do ~@forms)))

(defmacro when-not-clojure-1-9-plus
  "Wraps code that should only be included for Clojure versions prior to 1.9.  Otherwise, code is supressed."
  [& forms]
  (if (is-pre-clojure-1-9?)
    `(do ~@forms)))

(pns/import-def impl/spy-indent-level)
(pns/import-fn impl/spy-indent-spaces)
(pns/import-fn impl/spy-indent-reset)
(pns/import-fn impl/spy-indent-inc)
(pns/import-fn impl/spy-indent-dec)

(pns/import-macro impl/with-spy-indent )
(pns/import-macro impl/with-spy-enabled )
(pns/import-macro impl/check-spy-enabled )

(pns/import-fn impl/spy)
(pns/import-macro impl/spyx)
(pns/import-macro impl/spyx-pretty)
(pns/import-macro impl/spyxx )
(pns/import-macro impl/spy-let )
(pns/import-macro impl/spy-let-pretty )

; #todo need (dbg :awt122 (some-fn 1 2 3)) -> (spy :msg :awt122 (some-fn 1 2 3))


; original
#_(s/defn truthy? :- s/Bool
    "Returns true if arg is logical true (neither nil nor false); otherwise returns false."
    [arg :- s/Any]
    (if arg true false))

(pns/import-fn impl/truthy? )
(pns/import-fn impl/falsey? )
(pns/import-fn impl/validate )

(pns/import-fn impl/has-some? )
(pns/import-fn impl/has-none? )
(pns/import-fn impl/contains-elem? )
(pns/import-fn impl/contains-key? )
(pns/import-fn impl/contains-val? )

(pns/import-fn impl/not-nil?)
(pns/import-fn impl/not-empty?)
(pns/import-fn impl/keyvals )


(defn flat-vec
  "Accepts any number of nested args and returns the flattened result as a vector."
  [& args]
  (vec (flatten args)))

(pns/import-fn impl/glue)
(pns/import-fn impl/append)
(pns/import-fn impl/prepend)
(pns/import-fn impl/macro?)

(pns/import-macro impl/vals->map)
(pns/import-macro impl/with-map-vals)

; #todo rename -> drop-idx
; #todo force to vector result
(s/defn drop-at :- ts/List
  "Removes an element from a collection at the specified index."
  [coll :- ts/List
   index :- s/Int]
  (when (neg? index)
    (throw (IllegalArgumentException. (str "Index cannot be negative: " index))))
  (when (<= (count coll) index)
    (throw (IllegalArgumentException. (str "Index cannot exceed collection length: "
                                        " (count coll)=" (count coll) " index=" index))))
  (glue (take index coll)
    (drop (inc index) coll)))

; #todo rename -> insert-idx
; #todo force to vector result
(s/defn insert-at :- ts/List
  "Inserts an element into a collection at the specified index."
  [coll :- ts/List
   index :- s/Int
   elem :- s/Any]
  (when (neg? index)
    (throw (IllegalArgumentException. (str "Index cannot be negative: " index))))
  (when (< (count coll) index)
    (throw (IllegalArgumentException. (str "Index cannot exceed collection length: "
                                        " (count coll)=" (count coll) " index=" index))))
  (glue (take index coll) [elem]
    (drop index coll)))

; #todo rename -> replace-idx
; #todo force to vector result
; #todo if was vector, could just use (assoc the-vec idx new-val)
(s/defn replace-at :- ts/List
  "Replaces an element in a collection at the specified index."
  [coll :- ts/List
   index :- s/Int
   elem :- s/Any]
  (when (neg? index)
    (throw (IllegalArgumentException. (str "Index cannot be negative: " index))))
  (when (<= (count coll) index)
    (throw (IllegalArgumentException. (str "Index cannot exceed collection length: "
                                        " (count coll)=" (count coll) " index=" index))))
  (glue (take index coll) 
        [elem]
        (drop (inc index) coll)))

; #todo use (idx    coll int-or-kw) as `get` replacement?
; #todo use (idx-in coll [kw's]) as `fetch-in` replacement?


(s/defn dissoc-in :- s/Any
  "A sane version of dissoc-in that will not delete intermediate keys.
   When invoked as (dissoc-in the-map [:k1 :k2 :k3... :kZ]), acts like
   (clojure.core/update-in the-map [:k1 :k2 :k3...] dissoc :kZ). That is, only
   the map entry containing the last key :kZ is removed, and all map entries
   higher than kZ in the hierarchy are unaffected."
  [the-map :- ts/KeyMap
   keys-vec :- [s/Keyword]]
  (let [num-keys     (count keys-vec)
        key-to-clear (last keys-vec)
        parent-keys  (butlast keys-vec)]
    (cond
      (zero? num-keys) the-map
      (= 1 num-keys) (dissoc the-map key-to-clear)
      :else (update-in the-map parent-keys dissoc key-to-clear))))

; #todo:  add in clear-nil-entries to recursively delete all k-v pairs where val is nil or empty?

; #todo:  create safe-map ns with non-nil/non-dup versions of assoc-in, update-in, dissoc-in (&
; singles). Basically like compiler-like guarentees against misspellings, duplicate entries, missing
; entries.

(pns/import-fn impl/only )

; #todo (first [] ) should throw instead of -> nil, etc.
; #todo (second [1] ) should throw instead of -> nil, etc.
; #todo should throw if not 3 items in seq
(s/defn third :- s/Any
  "Returns the third item in a collection, or nil if fewer than three items are present. "
  [seqable-arg :- ts/List]
  (first (next (next seqable-arg))))

; #awt #todo: Test failure of (safe-> 3 (* 2) (+ 1))
; #awt #todo: add tests
; #awt #todo: finish safe->>
(defmacro safe->
  "When expr is not nil, threads it into the first form (via ->), and when that result is not nil,
   through the next etc.  If result is nil, throw IllegalArgumentException"
  [expr & forms]
  (let [g     (gensym)
        pstep (fn [step] `(if (nil? ~g)
                            (throw (IllegalArgumentException. (str "Nil value passed to form '" ~step \')))
                            ; #awt #todo: remove unneeded test above ^^^
                            #_(do (println "g=" ~g) (spyxx (-> ~g ~step)))
                            ;; (def mm {:a {:b 2}})
                            ;; user=> (safe-> mm (:aa) (:b))
                            ;; g= {:a {:b 2}}
                            ;; NullPointerException   user/eval1850 (form-init5653535826905962071.clj:1)
                            ;; user=> (safe-> mm :aa :b)   ; works
                            (let [result# (-> ~g ~step)]
                              (if (nil? result#)
                                (throw (IllegalArgumentException. (str "Nil value returned from form '" ~step \')))
                                result#))))
        ]
    `(let [~g ~expr
           ~@(interleave (repeat g) (clojure.core/map pstep forms))]
       ~g)))

; #todo make an it?-> (fff ppp it? qqq) to halt thread if encounter nil result (then returns nil)
; #todo make an it!-> (fff ppp it! qqq) to throw if encounter nil (replace safe->) (maybe val->)

(pns/import-macro impl/it-> )
(pns/import-macro impl/with-exception-default )


; (defn round [dblVal :incr   (/ 1 3)]            ; #todo add
;   (let [factor (Math/pow 10 *digits*)]
;     (it-> dblVal
;           (* it factor)
;           (Math/round it)
;           (/ it factor))))
; (defn round [dblVal :exp -2]
;   (round dblVal :incr (Math/pow 10 *digits*)))
; (defn round [dblVal :digits 2]
;   (round dblVal :exp (- *digits*)))

(defn rel=
  "Returns true if 2 double-precision numbers are relatively equal, else false.  Relative equality
   is specified as either (1) the N most significant digits are equal, or (2) the absolute
   difference is less than a tolerance value.  Input values are coerced to double before comparison.
   Example:

     (rel= 123450000 123456789   :digits 4   )  ; true
     (rel= 1         1.001       :tol    0.01)  ; true
   "
  [val1 val2 & {:as opts}]
  {:pre  [(number? val1) (number? val2)]
   :post [(contains? #{true false} %)]}
  (let [{:keys [digits tol]} opts]
    (when-not (or digits tol)
      (throw (IllegalArgumentException.
               (str "Must specify either :digits or :tol" \newline
                 "opts: " opts))))
    (when tol
      (when-not (number? tol)
        (throw (IllegalArgumentException.
                 (str ":tol must be a number" \newline
                   "opts: " opts))))
      (when-not (pos? tol)
        (throw (IllegalArgumentException.
                 (str ":tol must be positive" \newline
                   "opts: " opts)))))
    (when digits
      (when-not (integer? digits)
        (throw (IllegalArgumentException.
                 (str ":digits must be an integer" \newline
                   "opts: " opts))))
      (when-not (pos? digits)
        (throw (IllegalArgumentException.
                 (str ":digits must positive" \newline
                   "opts: " opts)))))
    ; At this point, there were no invalid args and at least one of
    ; either :tol and/or :digits was specified.  So, return the answer.
    (let [val1      (double val1)
          val2      (double val2)
          delta-abs (Math/abs (- val1 val2))
          or-result (truthy?
                      (or (zero? delta-abs)
                        (and tol
                          (let [tol-result (< delta-abs tol)]
                            tol-result))
                        (and digits
                          (let [abs1          (Math/abs val1)
                                abs2          (Math/abs val2)
                                max-abs       (Math/max abs1 abs2)
                                delta-rel-abs (/ delta-abs max-abs)
                                rel-tol       (Math/pow 10 (- digits))
                                dig-result    (< delta-rel-abs rel-tol)]
                            dig-result))))
          ]
      or-result)))

(pns/import-fn impl/range-vec)
(pns/import-fn impl/thru)
(pns/import-fn impl/keep-if)
(pns/import-fn impl/drop-if)
(pns/import-fn impl/unnest )

(defmacro lazy-cons
  "The simple way to create a lazy sequence:
      (defn lazy-next-int [n]
        (t/lazy-cons n (lazy-next-int (inc n))))
      (def all-ints (lazy-next-int 0)) "
  [curr-val next-form]
  `(lazy-seq (cons ~curr-val ~next-form)))

; #todo document use via binding
(def ^:dynamic *lazy-gen-buffer-size*
  "Specifies the output channel default buffer size for `lazy-gen` forms"
  32)

; #todo add to README
; #todo fix SO posting:  defgen -> lazy-gen
; #todo make null case return [] instead of nil
; #todo make eager version?  gen-vec, gen-seq, ...
(defmacro lazy-gen [& forms]
  "Creates a 'generator function' that returns a lazy seq of results
  via `yield` (a la Python)."
  `(let [~'lazy-gen-output-buffer    (ca/chan *lazy-gen-buffer-size*)
         lazy-reader-fn#             (fn lazy-reader-fn# []
                                       (let [curr-item# (ca/<!! ~'lazy-gen-output-buffer)] ; #todo ta/take-now!
                                         (when (not-nil? curr-item#)
                                           (lazy-cons curr-item# (lazy-reader-fn#))))) ]
     (ca/go
       ~@forms
       (ca/close! ~'lazy-gen-output-buffer))
     (lazy-reader-fn#)))

(defmacro yield ; #todo put-now/put-later & dynamic
  "Within a 'generator function' created by `lazy-gen`, populates the
  result lazy seq with the supplied value (a la Python). Returns the value."
  [value]
  `(do
     (ca/>! ~'lazy-gen-output-buffer ~value)
     ~value))

(defmacro yield-all
  "Within a 'generator function' created by `lazy-gen`, populates the
  result lazy seq with each item from the supplied collection. Returns the collection."
  [values]
  `(do
     (doseq [value# ~values]
       (yield value#))
     (vec ~values)))

(defn fibonacci-seq
  "A lazy seq of Fibonacci numbers (memoized)."
  []
  (let [fibo-step (fn fibo-step [[val1 val2]]
                    (let [next-val (+ val1 val2)]
                      (lazy-cons next-val (fibo-step [val2 next-val] )))) ]
    (cons 0 (cons 1 (fibo-step [0N 1N])))))

(defn fibo-thru
  "Returns a vector of Fibonacci numbers up to limit (inclusive). Note that a
  2^62  corresponds to 91'st Fibonacci number."
  [limit]
  (vec (take-while #(<= % limit) (fibonacci-seq))))

(defn fibo-nth
  "Returns the N'th Fibonacci number (zero-based). Note that
  N=91 corresponds to approx 2^62"
  [N]
  (first (drop N (fibonacci-seq))))

; #todo remove? (in impl)
;(defn char-seq
;  "Given two characters (or numerical equivalents), returns a seq of characters
;  (inclusive) from the first to the second.  Characters must be in ascending order."
;  [start-char stop-char]
;  {:pre [ (char start-char) (char stop-char) ] }
;  ; These "dummy" casts are to ensure that any input integer values are within the valid
;  ; range for Unicode characters
;  (let [start-val   (int start-char)
;        stop-val    (int stop-char)]
;    (when-not (<= start-val stop-val)
;      (throw (IllegalArgumentException.
;               (str "char-seq: start-char must come before stop-char."
;                 "  start-val=" start-val "  stop-val=" stop-val))))
;    (mapv char (thru start-val stop-val))))

(defn seq->str
  "Convert a seq into a string (using pr) with a space preceding each value"
  [seq-in]
  (with-out-str
    (doseq [it (seq seq-in)]
      (print \space)
      (pr it))))

(pns/import-fn impl/strcat)
(pns/import-fn impl/char-seq)
(pns/import-fn impl/pretty-str)
(pns/import-fn impl/pretty)

; #todo add test & README
; #todo rename json->edn  ???
(defn json->clj [arg]                                       ; #todo experimental
  "Shortcut to cheshire.core/parse-string"
  (cc/parse-string arg true))                               ; true => keywordize-keys

(defn int->kw [arg]
  (keyword (str arg)))

(defn kw->int [arg]
  (Integer/parseInt (kw->str arg)))

; #todo add test & README
(defn clj->json [arg]                                       ; #todo experimental
  "Shortcut to cheshire.core/generate-string"
  (cc/generate-string arg))

;                                               "1234.4567.89ab.cdef"  also valid for read
; #todo need conversion from Long -> hex string="1234-4567-89ab-cdef" (& inverse)
; #todo need rand-id/randid/rid/rid-str (rand id) -> 64 bit hex string="1234-4567-89ab-cdef"
; i[12] = Random.nextInt(); bytes += i[12].toHexString()

(pns/import-fn impl/clip-str )
(pns/import-fn impl/wild-match-ctx? )
(pns/import-fn impl/wild-match? )
(pns/import-fn impl/submap? )
(pns/import-fn impl/sub-match? )
(pns/import-fn impl/wild-item? )
(pns/import-macro impl/matches? )

; #todo: add (throwed? ...) for testing exceptions

; #todo readme
(s/defn starts-with? :- s/Bool
  "Returns true when the initial elements of coll match those of tgt"
  [coll tgt-in]     ; #todo schema
  (let [tgt-vec (vec tgt-in)
        tgt-len (count tgt-vec) ]
    (if (< (count coll) tgt-len)
      false
      (let [coll-vals (take tgt-len coll)]
        (= coll-vals tgt-vec)))))

; #todo readme
(defn index-using
  "Finds the first index N where (< N (count coll)) such that (pred (drop N coll)) is truthy.
  Returns `nil` if no match found."
  [pred coll]
  (let [all-vals (vec coll)
        num-vals (count all-vals)]
    (loop [i 0]
      (if (<= num-vals i)
        nil         ; did not find match
        (let [curr-vals (subvec all-vals i)]
          (if (pred curr-vals)
            i
            (recur (inc i))))))))

; #todo readme
(defn split-using    ; #todo schema
  "Splits a collection based on a predicate with a collection argument.
  Finds the first index N such that (pred (drop N coll)) is true. Returns a length-2 vector
  of [ (take N coll) (drop N coll) ]. If pred is never satisified, [ coll [] ] is returned."
  [pred coll]
  (let [N (index-using pred (vec coll))]
    (if (nil? N)
      [coll []]
      [(take N coll) (drop N coll)])))

; #todo readme
(defn split-match    ; #todo schema
  "Splits a collection src by matching with a sub-sequence tgt of length L.
  Finds the first index N such that (= tgt (->> coll (drop N) (take L))) is true.
  Returns a length-2 vector of [ (take N coll) (drop N coll) ].
  If no match is found, [ coll [] ] is returned."
  [coll tgt]
  (split-using
    (fn [partial-coll] (starts-with? partial-coll (vec tgt)))
    (vec coll)))

; #todo readme
(s/defn partition-using
  "Partitions a collection into vector of segments based on a predicate with a collection argument.
  The first segment is initialized by removing the first element from `values`, with subsequent
  elements similarly transferred as long as `(pred remaining-values)` is falsey. When
  `(pred remaining-values)` becomes truthy, the algorithm begins building the next segment.
  Thus, the first partition finds the smallest N (< 0 N) such that (pred (drop N values))
  is true, and constructs the segment as (take N values). If pred is never satisified,
  [values] is returned."
  [pred   :- s/Any    ; a predicate function  taking a list arg
   values :- ts/List ]
  (loop [vals   (vec values)
         result []]
    (if (empty? vals)
      result
      (let [
        out-first  (take 1 vals)
        [out-rest unprocessed] (split-using pred (rest vals))
        out-vals   (glue out-first out-rest)
        new-result (append result out-vals)
      ]
        (recur unprocessed new-result)))))

(defn refer-tupelo  ; #todo document in readme
  "Refer a number of commonly used tupelo.core functions into the current namespace so they can
   be used without namespace qualification."
  []
  (refer 'tupelo.core :only
   '[spy spy-let spy-let-pretty spyx spyx-pretty spyxx
     with-spy-indent with-spy-enabled check-spy-enabled
     truthy? falsey? not-nil? not-empty? has-some? has-none?
     contains-key? contains-val? contains-elem?
     forv conjv glue vals->map with-map-vals macro? char-seq
     append prepend grab dissoc-in fetch-in
     submap? submap-by-keys submap-by-vals map-keys->vals keyvals
     validate only third it-> safe-> keep-if drop-if zip flat-vec
     strcat nl pretty pretty-str json->clj clj->json clip-str range-vec thru rel=
     drop-at insert-at replace-at starts-with? int->kw kw->int vec->list
     xfirst xrest kw->sym kw->str str->sym str->kw sym->kw sym->str
     split-using split-match partition-using wild-match? sub-match? wild-match-ctx? wild-item?
     increasing? increasing-or-equal?
     fibonacci-seq fibo-thru fibo-nth unnest
     with-exception-default lazy-cons lazy-gen yield yield-all
    ] ))

; #todo verify spy-let works after (t/refer-tupelo)

;---------------------------------------------------------------------------------------------------
; DEPRECATED functions

; As of Clojure 1.9.0-alpha5, seqable? is native to clojure
(when-not-clojure-1-9-plus
  (defn ^{:deprecated "1.9.0-alpha5"} seqable?  ; from clojure.contrib.core/seqable
    "Returns true if (seq x) will succeed, false otherwise."
    [x]
    (or (seq? x)
      (instance? clojure.lang.Seqable x)
      (nil? x)
      (instance? Iterable x)
      (-> x .getClass .isArray)
      (string? x)
      (instance? java.util.Map x))))

; duplicate of str/split-lines
(defn ^:deprecated ^:no-doc str->lines
  "***** DEPRECATED:  duplicate of str/split-lines *****

  Returns a lazy seq of lines from a string"
  [string-arg]
  (line-seq (BufferedReader. (StringReader. string-arg))))

;---------------------------------------------------------------------------------------------------
; Another benefit of test-all:  don't need "-test" suffix like in lein test:
; ~/tupelo > lein test :only tupelo.core
; lein test user
; Ran 0 tests containing 0 assertions.     ***** Nearly silent failure *****
; 0 failures, 0 errors.
;
; ~/tupelo > lein test :only tst.tupelo.core
; lein test tst.tupelo.core
; Ran 8 tests containing 44 assertions.     ***** Runs correctly *****
; 0 failures, 0 errors.
;
; ~/tupelo > lein test :only tst.tupelo.core/convj-test
; lein test tst.tupelo.core
; Ran 1 tests containing 3 assertions.
; 0 failures, 0 errors.
;
; #todo:  add run-tests with syntax like lein test :only
;   (run-tests 'tst.tupelo.core)              ; whole namespace
;   (run-tests 'tst.tupelo.core/convj-test)   ; one function only
;
; #todo make it handle either tst.orig.namespace or orig.namespace-test
; #todo make it a macro to accept unquoted namespace values

; #todo delete this
(defn ^:deprecated ^:no-doc test-all
  "Convenience fn to reload a namespace & the corresponding test namespace from disk and
  execute tests in the REPL.  Assumes canonical project test file organization with
  parallel src/... & test/tst/... directories, where a 'tst.' prefix is added to all src
  namespaces to generate the cooresponding test namespace.  Example:

    (test-all 'tupelo.core 'tupelo.csv)

  This will reload tupelo.core, tst.tupelo.core, tupelo.csv, tst.tupelo.csv and
  then execute clojure.test/run-tests on both of the test namespaces."
  [& ns-list]
  (let [test-ns-list (for [curr-ns ns-list]
                       (let [curr-ns-test (symbol (str "tst." curr-ns))]
                         (println (str "testing " curr-ns " & " curr-ns-test))
                         (require curr-ns curr-ns-test :reload)
                         curr-ns-test))
        ]
    (println "-----------------------------------------------------------------------------")
    (apply clojure.test/run-tests test-ns-list)
    (println "-----------------------------------------------------------------------------")
    (newline)
    ))

(s/defn ^:deprecated conjv :- [s/Any] ; #todo remove
  "***** DEPRECATED:  replaced by tupelo.core/append *****

   Given base-coll and and one or more values, converts base-coll to a vector and then appends the values.
   The result is always returned as a vector. Note that `(conjv nil 5)` -> `[5]`"
  ; From Stuart Sierra post 2014-2-10
  ([base-coll :- [s/Any]
    value :- s/Any]
    (conj (vec base-coll) value))
  ([base-coll :- [s/Any]
    value :- s/Any
    & values :- [s/Any]]
    (apply conj (vec base-coll) value values)))

(defn -main [& args]
  (println "main - enter")
  (spyx (s/fn-validation?)))
