(ns build
  "HoneySQL's build script.

  clojure -T:build ci

  clojure -T:build run-doc-tests :aliases '[:cljs]'

  Run tests:
  clojure -X:test
  clojure -X:test:master

  For more information, run:

  clojure -A:deps -T:build help/doc"
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'com.github.seancorfield/honeysql)
(defn- the-version [patch] (format "2.4.%s" patch))
(def version (the-version (b/git-count-revs nil)))
(def snapshot (the-version "999-SNAPSHOT"))

(defn eastwood "Run Eastwood." [opts]
  (-> opts (bb/run-task [:eastwood])))

(defn gen-doc-tests "Generate tests from doc code blocks." [opts]
  (-> opts (bb/run-task [:gen-doc-tests])))

(defn run-doc-tests
  "Generate and run doc tests.

  Optionally specify :aliases vector:
  [:1.9] -- test against Clojure 1.9 (the default)
  [:1.10] -- test against Clojure 1.10.3
  [:1.11] -- test against Clojure 1.11.0
  [:master] -- test against Clojure 1.12 master snapshot
  [:cljs] -- test against ClojureScript"
  [{:keys [aliases] :as opts}]
  (gen-doc-tests opts)
  (bb/run-tests (assoc opts :aliases
                       (-> [:test-doc]
                           (into aliases)
                           (into (if (some #{:cljs} aliases)
                                   [:test-doc-cljs]
                                   [:test-doc-clj])))))
  opts)

(defn test "Run basic tests." [opts]
  (-> opts
      (update :aliases (fnil conj []) :1.11)
      (bb/run-tests)))

(defn ci
  "Run the CI pipeline of tests (and build the JAR).

  Default Clojure version is 1.9.0 (:1.9) so :elide
  tests for #409 on that version."
  [opts]
  (-> opts
      (bb/clean)
      (assoc :lib lib :version (if (:snapshot opts) snapshot version))
      (as-> opts
            (reduce (fn [opts alias]
                      (run-doc-tests (assoc opts :aliases [alias])))
                    opts
                    [:cljs :elide :1.10 :1.11 :master]))
      (eastwood)
      (as-> opts
            (reduce (fn [opts alias]
                      (bb/run-tests (assoc opts :aliases [alias])))
                    opts
                    [:cljs :elide :1.10 :1.11 :master]))
      (bb/clean)
      (assoc :src-pom "template/pom.xml")
      (bb/jar)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      (assoc :lib lib :version (if (:snapshot opts) snapshot version))
      (bb/deploy)))
