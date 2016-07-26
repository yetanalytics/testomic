# testomic

[![Build Status](https://travis-ci.org/yetanalytics/testomic.svg?branch=master)](https://travis-ci.org/yetanalytics/testomic)
[![Clojars Project](https://img.shields.io/clojars/v/com.yetanalytics/testomic.svg)](https://clojars.org/com.yetanalytics/testomic)

A small library to ease testing with [Datomic](http://www.datomic.com/) and (optionally) [conformity](https://github.com/rkneufeld/conformity).

## Usage

``` clojure
;; If you are using datomic pro, exclude the datomic free dep!
:dependencies [[com.yetanalytics/testomic "0.1.0"
                :exclusions [com.datomic/datomic-free]]]

```

For a fresh conn that will be automatically released/deleted:

``` clojure
(require '[testomic.core :refer :all])

(with-conn foo
  (type foo)) ;; => datomic.peer.LocalConnection

```

For a conn with data transacted in, can bind values like let:

``` clojure
(with-conn foo
  (let-txs [;; first binding/tx
            [[op1 eid1 attr1 val1] :as tx1]
            [[:db/add (d/tempid :db.part/user) :person/name "Bob"]]
            ;; second binding/tx
            [[op2 eid2 attr2 val2] :as tx2]
            [[:db/add (d/tempid :db.part/user) :person/name "Alice"]]]

  ;; accepts multiple transactions
  (d/q '[:find ?name
         :where
         [_ :person/name ?name]]
       (d/db foo)) ;; => #{["Bob"] ["Alice"]}

  ;; resolves-tempids
  (:person/name (d/entity (d/db foo) eid1)) ;; => "Bob"
  (:person/name (d/entity (d/db foo) eid2)) ;; => "Alice"

  ;; will not override a conn if nested!
  (let-txs [tx3 [[:db/add (d/tempid :db.part/user) :person/name "Ethel"]]]
    (d/q '[:find (count ?person) .
           :where
           [?person :person/name]] (d/db foo)) ;; => 3
    )))

```

If fixtures are your thing, you can use `wrap-conn` and `make-wrap-txs-fixture` to reduce code.
You can use the dynamic variable `testomic.core/conn` in test functions using the fixtures.


### Schema

If you want to provide a schema to be automatically applied to the conn, you can use conformity to do so in a number of different ways:

``` clojure
(set-norms! {:norm/foo ...}) ;; statically set the norms map to be used
(set-norms-path! "test/norms.edn") ;; same, but from file

(with-norms {:norm/foo ...} ;; bind a norms map
  (with-conn ...))

(with-norms-path "test/norms.edn"
  (with-conn ...)) ;; same, from file

;; or use fixtures
(use-fixtures :once (make-wrap-norms-fixture {:norm/foo ...}))
(use-fixtures :once (make-wrap-norms-path-fixture "test/norms.edn"))

```

## License

Copyright © 2016 Yet Analytics, Inc.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
