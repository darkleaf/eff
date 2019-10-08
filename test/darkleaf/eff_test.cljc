(ns darkleaf.eff-test
  (:require
   [darkleaf.eff :as eff]
   [clojure.test :as t]))

(t/deftest <<-
  (t/is (= 3 (eff/<<-
              (let [x 1])
              (let [y 2])
              (+ x y)))))

(t/deftest simple-continutaion
  (let [cont   (fn [a b]
                 (+ a b))
        script [{:args [1 2]}
                {:return 3}]]
    (eff/test cont script)))

(t/deftest continuation-with-effects
  (let [cont   (fn [a b]
                 (eff/let! [_ [:prn a]
                            _ [:prn b]
                            c [:read]
                            _ [:prn c]]
                   :ok))
        script [{:args [:a :b]}
                {:effect   [:prn :a]
                 :coeffect nil}
                {:effect   [:prn :b]
                 :coeffect nil}
                {:effect   [:read]
                 :coeffect :c}
                {:effect   [:prn :c]
                 :coeffect nil}
                {:return :ok}]]
    (eff/test cont script)))

(t/deftest continuation-composition
  (let [ask-name (fn []
                   (eff/let! [_    [:prn "What is your name?"]
                              name [:read]]
                     name))
        cont     (fn [greeting]
                   (eff/let! [name (ask-name)
                              msg  (str greeting " " name)
                              _    [:prn msg]]
                     nil))
        script   [{:args ["Hi!"]}
                  {:effect   [:prn "What is your name?"]
                   :coeffect nil}
                  {:effect   [:read]
                   :coeffect "John"}
                  {:effect   [:prn "Hi! John"]
                   :coeffect nil}
                  {:return nil}]]
    (eff/test cont script)))

(t/deftest regular-functions
  (let [cont   (fn [xs]
                 (eff/let! [xs' (map inc xs)]
                   xs'))
        script [{:args [[1 2 3]]}
                {:return [2 3 4]}]]
    (eff/test cont script)))

(t/deftest test-fail
  (let [cont   (fn []
                 :ok)
        script [{:args []}
                {:return :fail}]
        report (with-redefs [t/do-report identity]
                 (eff/test cont script))]
    (t/is (= :fail (:type report)))))
