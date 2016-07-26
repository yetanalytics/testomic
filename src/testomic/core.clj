(ns testomic.core
  (:require [datomic.api :as d]
            [io.rkn.conformity :as c]
            [crypto.random :refer [url-part]]))

(def ^:dynamic *norms-map*
  {})

(defn rand-db-uri ^String []
  (str "datomic:mem://" (url-part 40)))

(defn new-conn
  "Creates a new conn at the specified uri.
   Only use with in-memory conns."
  ^datomic.peer.LocalConnection [^String uri]
  (d/create-database uri)
  (let [c (d/connect uri)]
    (c/ensure-conforms c *norms-map*)
    c))

(defn reduce-tx-tempids
  "Reduces over multiple transactions, collecting tempids
   for resolution"
  [^datomic.peer.LocalConnection conn
   ^clojure.lang.PersistentVector txs]
  (reduce
   (fn [tx-tids tx]
     (merge tx-tids (:tempids @(d/transact conn tx))))
   {}
   txs))

;; This can be used to override the random db uri
(def ^:dynamic *db-uri* nil)

;; map for tempid resolution
(def ^:dynamic *tempid-map* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API

;; Use this in tests wrapped with macros/fixtures below
(def ^:dynamic conn nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Util

(defn set-norms!
  "Set the norms that will be conformed to on each conn."
  [norms-map]
  (alter-var-root #'testomic.core/*norms-map*
                  (constantly norms-map)))

(defn set-norms-path!
  "Use the norms at the given resource path."
  [^String path]
  (set-norms! (c/read-resource path)))

(defn tempid?
  "Is x a datomic tempid?"
  ^java.lang.Boolean [x]
  (instance? datomic.db.DbId x))

(defn resolve-tempid
  "Manually resolve a tempid (such as one that was not bound in let-txs) using
   the bound conn and tempid-map."
  ^java.lang.Long [^datomic.db.DbId tid]
  (d/resolve-tempid (d/db conn) *tempid-map* tid))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Macros

(defmacro with-norms
  "Binds the given norms map and runs body"
  [norms-map & body]
  `(binding [testomic.core/*norms-map* ~norms-map]
     ~@body))

(defmacro with-norms-path
  "Binds the norms map at the given resource path and runs body"
  [norms-path & body]
  `(with-norms (c/read-resource ~norms-path)
     ~@body))

(defmacro with-conn
  "Binds conn to run body, then releases it."
  [conn-sym & body]
  `(binding [testomic.core/*db-uri* (rand-db-uri)]
     (binding [testomic.core/conn (new-conn testomic.core/*db-uri*)]
       (let [~conn-sym testomic.core/conn]
         (try ~@body
              (finally
                (d/release testomic.core/conn)))))))

(defn- wrap-tempid
  "Wrap a symbol in a Cons that will resolve it if it is bound to a tempid.
   Used internally by let-txs"
  ^clojure.lang.Cons [^clojure.lang.Symbol maybe-tid-sym]
  `(if (tempid? ~maybe-tid-sym)
     (datomic.api/resolve-tempid (d/db testomic.core/conn) testomic.core/*tempid-map* ~maybe-tid-sym)
     ~maybe-tid-sym))

(defmacro let-txs
  [tx-bindings & body]
  "Like let, but each binding represents a transaction into conn.
   Bound tempids will be resolved to eids. If there is no conn, one will be
   created."
  (assert (vector? tx-bindings) "a vector for its binding")
  (assert (even? (count tx-bindings)) "an even number of forms in binding vector")
  (let [destructured (destructure tx-bindings)
        rights (into #{} (take-nth 2 (drop 1 tx-bindings)))
        tx-symbols (into []
                         (for [[left right] (partition 2 destructured)
                               :when (rights right)]
                           left))
        ;; Wrap all bindings to resolve if they are a tempid
        destructured-wrapped (into []
                                   (interleave
                                    (take-nth 2 destructured)
                                    (map
                                     wrap-tempid
                                     (take-nth 2 destructured))))]
    `(let* ~destructured
       (assert testomic.core/conn "No conn present!")
      (binding [testomic.core/*tempid-map* (reduce-tx-tempids testomic.core/conn ~tx-symbols)]
        (let* ~destructured-wrapped
          ~@body)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test Fixtures/Fixture Factories

(defn make-wrap-norms-fixture
  "Like with-norms, but returns a fn suitable for use as a clojure.test fixture."
  [norms-map]
  (fn wrap-norms [f]
    (binding [*norms-map* norms-map]
      (f))))

(defn make-wrap-norms-path-fixture
  "Like with-norms-path, but returns a fn suitable for use as a clojure.test fixture."
  [norms-path]
  (fn wrap-norms-path [f]
    (binding [*norms-map* (c/read-resource norms-path)]
      (f))))

(defn wrap-conn
  "Like with-conn, but suitable for use as a clojure.test fixture."
  [f]
  (if conn
    (f)
    (binding [*db-uri* (rand-db-uri)]
      (binding [conn (new-conn *db-uri*)]
        (try (f)
             (finally
               (d/release conn)))))))

(defn make-wrap-txs-fixture
  "Given a vector of transactions, it returns a function suitable for use as a
   clojure test fixture that wraps a new conn (if needed), transacts the data,
   and binds a map of tempids for resolution."
  [txs]
  (fn wrap-txs [f]
    (if conn
      (binding [*tempid-map* (reduce-tx-tempids conn txs)]
        (f))
      (wrap-conn #(wrap-txs f)))))
