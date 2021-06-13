(ns nl.epij.effect.handler.tasks
  (:require [nl.epij.effect :as effects]
            [clojure.string :as str]
            [cheshire.core :as json]
            [nl.epij.gcf.log :as log])
  (:import [com.google.cloud.tasks.v2 CloudTasksClient QueueName Task HttpRequest HttpMethod Task$Builder]
           [com.google.protobuf ByteString]))

(defn create-client
  []
  (CloudTasksClient/create))

(defn create-task!
  [{:nl.epij.command/keys [name action contents] :as command} ^CloudTasksClient client]
  (let [[_ project-id _ location-id _ queue-id _ task-name] (str/split name #"/")
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

(defmethod effects/get-handler :nl.epij.effect.task/create
  [command]
  {::effects/enact?   false
   ::effects/execute! (fn [{::keys [client]}]
                        (create-task! command (force client)))})

(comment

 )
