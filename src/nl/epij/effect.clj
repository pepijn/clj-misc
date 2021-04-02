(ns nl.epij.effect)

(defmulti execute! #(::name %2))
