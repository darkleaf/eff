(ns darkleaf.eff-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [darkleaf.eff-test]))

(doo-tests 'darkleaf.eff-test)
