(ns nl.epij.workflow-test
  (:require [clojure.test :refer [deftest is]])
  (:require [nl.epij.workflow :as wf]
            [clojure.spec.alpha :as s]
            [matcher-combinators.test]
            [liberator.core :as lib]
            [liberator.dev :as lib.dev]
            [matcher-combinators.matchers :as m]))

(s/def ::name string?)
(s/def ::test-domain-model (s/keys :req [::name]))

(def workflow
  {::wf/request->domain (fn [{:keys [body]}]
                          (let [{:strs [person-name] :as m} body]
                            (when-not (map? m) (throw (ex-info "Not a map" {})))
                            {::name person-name}))
   ::wf/domain-spec     ::test-domain-model
   ::wf/side-effects    #{{:nl.epij.effect/name :nl.epij.effect/noop}}
   ::wf/data->effects   (fn [{::keys [name]}]
                          [{:nl.epij.effect/name :nl.epij.effect/noop
                            :say-hello-world (format "Hello %s" name)}])})

(deftest workflows
  (let [resource (wf/workflow-resource workflow)
        handler  (lib/resource resource)]
    (is (match? {:status  422
                 :body    "nil - failed: string? in: [:nl.epij.workflow-test/name] at: [:nl.epij.workflow-test/name] spec: :nl.epij.workflow-test/name\n"
                 :headers {"X-Liberator-Trace" (m/embeds [":decision (:processable? false)"])}}
                ((lib.dev/wrap-trace handler :header)
                 {:request-method :patch
                  :body           {"invalid value" "Olar"}})))
    (is (match? {:status            422
                 ::wf/domain-object (m/pred #(instance? Exception %))}
                (handler {:request-method :patch
                          :body           "olar"})))
    (is (match? {:status            204
                 ::wf/domain-object {::name "Hank"}
                 ::wf/effects       [{:say-hello-world "Hello Hank"}]}
                (handler {:request-method :patch
                          :body           {"person-name" "Hank"}})))))
