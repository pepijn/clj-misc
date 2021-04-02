(ns nl.epij.effect.handler.moneybird
  (:require [nl.epij.effect :as effects]
            [hato.client :as hc]
            [nl.epij.gcp.gcf.log :as log]))

(defn moneybird-client
  [moneybird-token administration-id]
  (fn [uri m]
    (let [uri (str "https://moneybird.com/api/v2/" administration-id uri)]
      (hc/request (merge {:url             uri
                          :accept-encoding [:json]
                          :as              :json
                          :headers         {"Authorization" (str "Bearer " moneybird-token)}}
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

(comment

 (effects/execute! {::moneybird-request! (moneybird-client "" "314978192511206690")}
                   {::effects/name                               :nl.epij.effect.moneybird.product/create
                    :nl.epij.moneybird.product/description       "Pepijn test",
                    :nl.epij.moneybird.product/price             3.52M,
                    :nl.epij.moneybird.product/tax-rate-id       "314978194181588323",
                    :nl.epij.moneybird.product/ledger-account-id "314978192871916858"})

 (effects/execute! {::moneybird-request! (moneybird-client "" "314978192511206690")}
                   {:nl.epij.moneybird.product/id                "317608343636018748"
                    :nl.epij.moneybird.product/description       "Pepijn test update",
                    :nl.epij.moneybird.product/price             13.40M,
                    :nl.epij.moneybird.product/tax-rate-id       "314978194181588323",
                    :nl.epij.moneybird.product/ledger-account-id "314978192871916858"})

 )
