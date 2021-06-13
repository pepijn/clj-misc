(ns nl.epij.workflow
  (:require [clojure.spec.alpha :as s]
            [liberator.representation :as lib.rep]
            [nl.epij.gcf.log :as log]
            [clojure.pprint :as pprint]
            [nl.epij.effect :as effects]
            [clojure.set :as set]))

(defn handle-patch-final
  [ring-map]
  (-> ring-map
      (select-keys [:status :headers :message :body ::domain-object ::effects])
      (lib.rep/ring-response)))

(defn validate-side-effects!
  [expected actual]
  (let [expected' (into #{} (map ::effects/name expected))
        actual'   (into #{} (map ::effects/name actual))]
    (assert (empty? (set/difference expected' actual')))
    (assert (empty? (set/difference actual' expected')))))

(defn workflow-resource
  ([config] (workflow-resource config vector))
  ([{::keys [request->domain domain-spec data->effects side-effects]} components]
   {:pre [(set? side-effects)]}
   {:allowed-methods             #{:patch}
    :initialize-context          (fn [{:keys [request]}]
                                   {::domain-object (try (request->domain request)
                                                         (catch RuntimeException e
                                                           (log/warning "Couldn't initialize domain object" (Throwable->map e))
                                                           e))})
    :available-media-types       #{"application/json"}
    :processable?                (fn [{::keys [domain-object]}]
                                   (and (s/valid? domain-spec domain-object)
                                        [true {::domain-object domain-object
                                               ::effects       (data->effects domain-object)}]))
    :handle-unprocessable-entity (fn [{::keys [domain-object] :as ring-map}]
                                   (let [explanation (s/explain-str domain-spec domain-object)]
                                     (-> ring-map
                                         (select-keys [:status :headers :message ::domain-object])
                                         (assoc :body explanation)
                                         (lib.rep/ring-response))))
    :patch-enacted?              (effects/enact? side-effects)
    :patch!                      (fn [{::keys [effects]}]
                                   (validate-side-effects! side-effects effects)
                                   (doseq [{::effects/keys [name] :as effect} effects]
                                     (log/debug (str "Running side effect: " name) effect)
                                     (let [{::effects/keys [execute!]} (effects/get-handler effect)]
                                       (execute! components))))
    :handle-accepted             handle-patch-final
    :handle-no-content           handle-patch-final
    :handle-exception            (fn [{:keys [status] :as ctx}] ;; TODO: test this! And make sure to warn when against using in production
                                   (log/error (str "Exception thrown: " status) ctx)
                                   (lib.rep/ring-response (assoc ctx :body (with-out-str (pprint/pprint ctx)))))}))
