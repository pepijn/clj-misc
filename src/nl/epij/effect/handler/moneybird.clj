(ns nl.epij.effect.handler.moneybird
  (:require [nl.epij.effect :as effects]
            [hato.client :as hc]
            [nl.epij.gcf.log :as log]
            [clojure.string :as str]))

(defn moneybird-client
  [moneybird-token administration-id]
  (fn [uri m]
    (let [uri (str "https://moneybird.com/api/v2/" administration-id uri)]
      (hc/request (merge {:url             uri
                          :accept-encoding [:json]
                          :as              :json
                          :headers         {"Authorization" (str "Bearer " (force moneybird-token))}}
                         m)))))

(defn form-params
  [{:nl.epij.moneybird.product/keys [description price tax-rate-id ledger-account-id]}]
  {"product[description]"       description
   "product[tax_rate_id]"       tax-rate-id
   "product[price]"             (str price)
   "product[ledger_account_id]" ledger-account-id})

(defmethod effects/execute! :nl.epij.effect.moneybird.product/create
  [{::keys [moneybird-request!]} product]
  (log/info "Creating product on Moneybird" product)
  (let [result (moneybird-request! "/products.json"
                                   {:method      :post
                                    :form-params (form-params product)})]
    {:nl.epij.moneybird.product/id (get-in result [:body :id])}))

(defmethod effects/execute! :nl.epij.effect.moneybird.product/update
  [{::keys [moneybird-request!]} {:nl.epij.moneybird.product/keys [id] :as product}]
  (log/info (format "Updating product ID %s on Moneybird" id) product)
  (moneybird-request! (format "/products/%s.json" id)
                      {:method      :patch
                       :form-params (form-params product)})
  nil)

(defmethod effects/execute! :nl.epij.effect.moneybird.product/list
  [{::keys [moneybird-request!]} _]
  (let [{:keys [body headers]} (moneybird-request! "/products.json" {:method :get})
        [_ next-link] (str/split (get headers "link") #"[<>]")]
    {:nl.epij.moneybird/next-page next-link
     :nl.epij.moneybird/products  (mapv (fn [{:keys [id description price]}]
                                          {:nl.epij.moneybird.product/id          id
                                           :nl.epij.moneybird.product/description description
                                           :nl.epij.moneybird.product/price       price})
                                        body)}))

(defmethod effects/execute! :nl.epij.effect.moneybird.product/delete
  [{::keys [moneybird-request!]} {:nl.epij.moneybird.product/keys [id] :as product}]
  (log/info (format "Deleting product ID %s on Moneybird" id) product)
  (moneybird-request! (format "/products/%s.json" id)
                      {:method :delete})
  nil)

(comment

 (->> (effects/execute! {::moneybird-request! (moneybird-client (delay (System/getenv "MONEYBIRD_TOKEN"))
                                                                "314978192511206690")}
                        {::effects/name :nl.epij.effect.moneybird.product/list})
      :nl.epij.moneybird/products
      (map (fn [effect] (effects/execute! {::moneybird-request! (moneybird-client (delay (System/getenv "MONEYBIRD_TOKEN"))
                                                                                  "314978192511206690")}
                                          (assoc effect ::effects/name :nl.epij.effect.moneybird.product/delete)))))

 (effects/execute! {::moneybird-request! (moneybird-client "" "314978192511206690")}
                   {:nl.epij.moneybird.product/id                "317608343636018748"
                    :nl.epij.moneybird.product/description       "Pepijn test update",
                    :nl.epij.moneybird.product/price             13.40M,
                    :nl.epij.moneybird.product/tax-rate-id       "314978194181588323",
                    :nl.epij.moneybird.product/ledger-account-id "314978192871916858"})

 )
