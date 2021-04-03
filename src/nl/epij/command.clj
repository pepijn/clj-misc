(ns nl.epij.command
  (:require [clojure.spec.alpha :as s]))

(s/def ::uri string?)
(s/def :nl.epij.command.type/uri (s/keys :req [::uri]))
(s/def :nl.epij/command (s/or :uri-command :nl.epij.command.type/uri))
