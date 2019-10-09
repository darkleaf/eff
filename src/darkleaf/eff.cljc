(ns darkleaf.eff
  (:refer-clojure :exclude [test])
  #?(:cljs (:require-macros [darkleaf.eff :refer [<<-]]))
  (:require
   [clojure.test :as t]
   [clojure.walk :as w]))

(defmacro <<- [& body]
  (when (seq body)
    `(->> ~@(reverse body))))

(defn continue-impl [effect callback]
  (if (-> effect meta ::continuation some?)
    (vary-meta effect update ::continuation
               (fn [continuation]
                 (fn [coeffect]
                   (continue-impl (continuation coeffect) callback))))
    (callback effect)))

(defmacro let!
  {:style/indent :defn}
  [bindings & body]
  {:pre [(-> bindings count even?)]}
  (if (empty? bindings)
    `(do ~@body)
    (let [[name expr & bindings] bindings
          cont                   `(fn [~name]
                                    (let! [~@bindings]
                                      ~@body))]
      (cond
        (vector? expr) `(vary-meta ~expr assoc ::continuation ~cont)
        (seq? expr)    `(continue-impl ~expr ~cont)))))

(defmacro loop!
  {:style/indent :defn}
  [bindings & body]
  {:pre [(-> bindings count even?)]}
  (let [loop-name      (gensym "loop-name")
        form (->> `(do ~@body)
                  (w/macroexpand-all)
                  (w/prewalk-replace {'recur! loop-name}))
        bindings-names (->> bindings (partition 2) (map first))
        bindings-vals  (->> bindings (partition 2) (map second))]
    `(letfn [(~loop-name [~@bindings-names] ~form)]
       (~loop-name ~@bindings-vals))))

(defmacro ! [effect]
  `(let! [value# ~effect]
     value#))

(comment
  (defn transduce))

(defn- process-first-item [{:keys [report continuation]} {:keys [args]}]
  {:report        report
   :actual-effect (apply continuation args)})

(defn- process-middle-item [{:keys [report actual-effect]} {:keys [effect coeffect]}]
  (<<-
   (if (not= :pass (:type report))
     {:report report})
   (if (not= effect actual-effect)
     {:report {:type     :fail
               :expected effect
               :actual   actual-effect}})
   (let [continuation (-> actual-effect meta ::continuation)])
   (if (nil? continuation)
     {:report {:type :fail}})
   (let [actual-effect (continuation coeffect)])
   {:report        report
    :actual-effect actual-effect}))

(defn- process-middle-items [ctx items]
  (reduce process-middle-item ctx items))

(defn- process-last-item [{:keys [report actual-effect]} {:keys [return]}]
  (<<-
   (if (not= :pass (:type report))
     {:report report})
   (if-some [continuation (-> actual-effect meta ::continuation)]
     {:report {:type     :fail
               :expected nil
               :actual   continuation}})
   (if (not= return actual-effect)
     {:report {:type     :fail
               :expected return
               :actual   actual-effect}})
   {:report report}))

(defn test [continuation script]
  {:pre [(fn? continuation)
         (<= 2 (count script))]}
  (let [first-item   (first script)
        middle-items (-> script rest butlast)
        last-item    (last script)]
    (-> {:continuation continuation, :report {:type :pass}}
        (process-first-item first-item)
        (process-middle-items middle-items)
        (process-last-item last-item)
        :report
        (t/do-report))))
