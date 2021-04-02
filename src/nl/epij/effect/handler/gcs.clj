(ns nl.epij.effect.handler.gcs
  (:require [nl.epij.effect :as effects]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [nl.epij.gcp.gcf.log :as log])
  (:import [java.io ByteArrayInputStream]
           [com.google.cloud.storage Blob$BlobSourceOption Bucket$BlobTargetOption Blob BlobId StorageOptions Storage Storage$BucketGetOption]))

(defn- ^Blob get-object
  [project-id bucket-name object-name]
  (let [x       (.build (doto (StorageOptions/newBuilder)
                          (.setProjectId project-id)))
        storage ^Storage (.getService x)
        blob-id (BlobId/of bucket-name object-name)]
    (.get storage blob-id)))

(defn- create-object
  ([project-id bytes bucket-name object-name]
   (create-object project-id bytes bucket-name object-name [(Bucket$BlobTargetOption/doesNotExist)]))
  ([project-id bytes bucket-name object-name options]
   (let [x       (.build (doto (StorageOptions/newBuilder)
                           (.setProjectId project-id)))
         storage ^Storage (.getService x)
         bucket  (.get storage bucket-name (make-array Storage$BucketGetOption 0))
         options (into-array Bucket$BlobTargetOption options)]
     (.create bucket object-name bytes "text/json" options))))

(defn blob->stream
  [blob]
  (io/reader (ByteArrayInputStream. (.getContent blob (make-array Blob$BlobSourceOption 0)))))

(defn stream->obj
  [stream json?]
  (if json?
    (json/parse-stream stream)
    (slurp stream)))

(defn uri->bucket+key
  [uri]
  (let [[_ _ bucket & key-parts] (str/split uri #"/")
        key (str/join "/" key-parts)]
    [bucket key]))

(defn- json-file?
  [location]
  (str/ends-with? location ".json"))

(defn read!
  [{:nl.epij.storage.file/keys [location] :as file}]
  (log/debug (str "Reading from GCS: " location) file)
  (let [[bucket key] (uri->bucket+key location)
        json? (json-file? location)
        blob  (get-object nil bucket key)
        data  (some-> blob
                      (blob->stream)
                      (stream->obj json?))]
    (conj {:nl.epij.storage.file/location   location
           :nl.epij.storage.file/exists?    (some? blob)
           :nl.epij.storage.file/generation (.getGeneration blob)}
          (when data {:nl.epij.storage.file/contents data}))))

(defmethod effects/execute! :nl.epij.effect.storage.file/read
  [_ file]
  (read! file))

(defn write!
  [{:nl.epij.storage.file/keys [location contents generation] :as file}]
  (log/debug (str "Writing to GCS: " location) file)
  (let [[bucket key] (uri->bucket+key location)
        json? (json-file? location)
        data  (if json?
                (json/generate-string contents {:pretty true})
                contents)]
    (if generation
      (create-object nil (.getBytes data)
                             bucket
                             key
                             [(Bucket$BlobTargetOption/generationMatch generation)])
      (create-object nil (.getBytes data) bucket key))
    ;; TODO: it should probably return its location, as long as it respects CQRS
    nil))

(defmethod effects/execute! :nl.epij.effect.storage.file/write
  [_ file]
  (write! file))

(defmethod effects/execute! :nl.epij.effect.storage.file/update
  [_ {:nl.epij.storage.file/keys [location update] :as file}]
  (log/debug (str "Updating GCS file: " location) file)
  (let [{:nl.epij.storage.file/keys [generation contents]} (read! file)
        data   (update contents)
        result (write! (assoc file :nl.epij.storage.file/contents data
                                   :nl.epij.storage.file/generation generation))]
    result))

(comment

 (effects/execute! {}
                   {::effects/name                 :nl.epij.effect.storage.file/update
                    :nl.epij.storage.file/location "gs://development-moneybird-product-transporter/testing/test3.json",
                    :nl.epij.storage.file/update   (fn [x]
                                                     (assoc x "1089" :olar))})

 (effects/execute! {}
                   {::effects/name                 :nl.epij.effect.storage.file/read
                    :nl.epij.storage.file/location "gs://development-moneybird-product-transporter/testing/test2.json"})

 (effects/execute! {}
                   {::effects/name                 :nl.epij.effect.storage.file/write
                    :nl.epij.storage.file/location "gs://development-moneybird-product-transporter/testing/test3.json",
                    :nl.epij.storage.file/contents {1089 nil}})

 )
