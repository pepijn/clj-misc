(ns nl.epij.effect)

(defmulti execute! #(::name %2))

(defmulti get-handler ::name)

(defn enact?
  [effects]
  (->> effects
       (map (fn [side-effect]
              (-> (get-handler side-effect)
                  ::enact?)))
       (every? true?)))
