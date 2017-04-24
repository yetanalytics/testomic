(ns testomic.core-test
  (:require [clojure.test :refer :all]
            [testomic.core :refer :all]
            [datomic.api :as d]))

(deftest set-norms!-test
  (is (= {} *norms-map*))
  (set-norms! {:foo "bar"})
  (is (= {:foo "bar"} *norms-map*))
  (set-norms! {})
  (is (= {} *norms-map*)))

(deftest set-norms-path!-test
  (set-norms-path! "test/norms.edn")
  (is (:person/name
       *norms-map*))
  ;; cleanup
  (set-norms! {}))

(deftest tempid?-test
  (is (tempid? (d/tempid :db.part/db))))

(deftest with-norms-test
  (with-norms {:person/name
               {:txes [[{:db/id #db/id[:db.part/db]
                         :db/ident :person/name
                         :db/valueType :db.type/string
                         :db/cardinality :db.cardinality/one
                         :db/doc "A person's name"
                         :db.install/_attribute :db.part/db}]]}}
    (with-conn foo
      (is (d/attribute (d/db foo) :person/name)))))

(deftest with-norms-path-test
  (with-norms-path "test/norms.edn"
    (with-conn foo
      (is (d/attribute (d/db foo) :person/name)))))

(deftest with-conn-test
  (testing "binds a fresh conn to run body"
    (is (not=
         ;; first conn
         (with-conn foo
           foo)
         ;; second conn
         (with-conn bar
           bar))))
  (testing "binds a db of the new conn for use further down."
    (with-conn foo
      (is (instance? datomic.db.Db *db*))
      (is (= (d/db foo) *db*)))))

(deftest let-txs-test
  (with-norms-path "test/norms.edn"
    (with-conn foo
      (let-txs
        [[[op1 eid1 attr1 val1] :as tx1] [[:db/add (d/tempid :db.part/user) :person/name "Bob"]]
         [[op2 eid2 attr2 val2] :as tx2] [[:db/add (d/tempid :db.part/user) :person/name "Alice"]]]
        (testing "binds like let"
          (is (= val1 "Bob"))
          (is (= val2 "Alice")))
        (testing "transacts data and resolves tempids"
          (is (= #{[eid1 "Bob"] [eid2 "Alice"]}
                 (d/q '[:find ?eid ?name
                        :where
                        [?eid :person/name ?name]]
                      (d/db foo)))))
        (testing "binds a db"
          (is (instance? datomic.db.Db *db*))
          (is (= (d/db foo) *db*)))
        (testing "Can be nested to add additional transactions"
          (let-txs [tx3 [[:db/add (d/tempid :db.part/user) :person/name "Ethel"]]]
            (is (= 3 (d/q '[:find (count ?person) .
                            :where
                            [?person :person/name]] (d/db foo))))
            ))))))

(deftest let-db-with-txs-test
  (with-norms-path "test/norms.edn"
    (with-conn foo
      (let-db-with-txs
          db ;; binding symbol
          [[[op1 eid1 attr1 val1] :as tx1] [[:db/add (d/tempid :db.part/user) :person/name "Bob"]]
           [[op2 eid2 attr2 val2] :as tx2] [[:db/add (d/tempid :db.part/user) :person/name "Alice"]]]

          (is (= 2
                 (d/q '[:find (count-distinct ?p) .
                        :where
                        [?p :person/name]] db)))
        (testing "binds like let"
          (is (= val1 "Bob"))
          (is (= val2 "Alice")))
        (testing "transacts data and resolves tempids"
          (is (= #{[eid1 "Bob"] [eid2 "Alice"]}
                 (d/q '[:find ?eid ?name
                        :where
                        [?eid :person/name ?name]]
                      db))))
        (testing "binds a db"
          (is (instance? datomic.db.Db *db*))
          (is (= db *db*)))
        (testing "Can be nested to add additional transactions"
          (let-db-with-txs db2 [[[op3 eid3 attr3 val3] :as tx3] [[:db/add (d/tempid :db.part/user) :person/name "Ethel"]]]
            (is (= 3 (d/q '[:find (count ?person) .
                            :where
                            [?person :person/name]] db2)))
            (is (= #{[eid1 "Bob"] [eid2 "Alice"] [eid3 "Ethel"]}
                   (d/q '[:find ?eid ?name
                          :where
                          [?eid :person/name ?name]]
                        db2)))
            (testing "but conn will be nil to prevent confusion"
              (is (nil? *conn*))
              (testing "though you can still call it up by local name"
                (is (instance? datomic.peer.LocalConnection foo))))))))))
