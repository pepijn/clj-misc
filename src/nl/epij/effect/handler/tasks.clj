(ns nl.epij.effect.handler.tasks
  (:require [nl.epij.effect :as effects]
            [clojure.string :as str]
            [cheshire.core :as json]
            [nl.epij.gcp.gcf.log :as log])
  (:import [com.google.cloud.tasks.v2 CloudTasksClient QueueName Task HttpRequest HttpMethod Task$Builder]
           [com.google.protobuf ByteString]))

(defmethod effects/execute! :nl.epij.effect.task/create
  [_ {:nl.epij.command/keys [name action contents] :as command}]
  (let [[_ project-id _ location-id _ queue-id _ task-name] (str/split name #"/")
        client       ^CloudTasksClient (CloudTasksClient/create)
        queue-path   ^String (.toString (QueueName/of project-id location-id queue-id))
        message      (json/generate-string contents {:pretty true})
        http-request (.build (doto (HttpRequest/newBuilder)
                               (.setBody (ByteString/copyFrom message "UTF-8"))
                               (.setUrl action)
                               (.setHttpMethod (HttpMethod/PATCH))
                               (.putHeaders "Content-Type" "application/json")))
        task-builder ^Task$Builder (doto (Task/newBuilder)
                                     (.setHttpRequest http-request)
                                     (.setName name))
        task         (.createTask client queue-path (.build task-builder))]
    (log/info (str "Google Cloud Task created: " task-name) command)
    task))
