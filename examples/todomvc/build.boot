(set-env!
  :source-paths #{"src" "test"}
  :resource-paths #{"resources"}
  :dependencies '[[adzerk/boot-cljs "2.1.4" :scope "test"]
                  [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
                  [adzerk/boot-reload "0.5.2" :scope "test"]
                  [pandeiro/boot-http "0.8.3" :scope "test"]
                  [crisptrutski/boot-cljs-test "0.3.5-SNAPSHOT" :scope "test"]
                  [adzerk/boot-test "1.2.0"]
                  [com.cemerick/piggieback "0.2.2" :scope "test"]
                  [org.clojure/tools.nrepl "0.2.13" :scope "test"]
                  [weasel "0.7.0" :scope "test"]
                  [org.clojure/clojurescript "1.9.946"]
                  #_[binaryage/dirac "1.2.20" :scope "test"]
                  [powerlaces/boot-cljs-devtools "0.2.0" :scope "test"]

                  [org.clojure/spec.alpha "0.1.143"]
                  [net.cgrand/xforms "0.15.0"]
                  [org.clojure/test.check "0.9.0"]
                  [rum "0.11.0"]
                  [org.clojure/core.async "0.3.465"]
                  [provisdom/maali "0.0.1-SNAPSHOT"]])

(require
  '[adzerk.boot-cljs :refer [cljs]]
  '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
  '[adzerk.boot-reload :refer [reload]]
  '[pandeiro.boot-http :refer [serve]]
  '[adzerk.boot-test            :refer :all]
  '[crisptrutski.boot-cljs-test :refer [test-cljs report-errors!] :as cljs-test]
  #_'[powerlaces.boot-cljs-devtools :refer [cljs-devtools dirac]]
  '[boot.repl]
  )

(def +version+ "0.0.1-SNAPSHOT")

(task-options!
  pom {:project     'provisdom/todo
       :version     +version+
       :description "Boot task to compile ClojureScript applications."
       :url         "https://github.com/provisdom/maali"
       :scm         {:url "https://github.com/provisdom/maali"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

#_(swap! boot.repl/*default-dependencies*
         concat '[[com.cemerick/piggieback "0.2.2"] [com.cemerick/piggieback "0.2.2"]])

#_(swap! boot.repl/*default-middleware*
         conj 'cemerick.piggieback/wrap-cljs-repl)

(deftask build
         "This task contains all the necessary steps to produce a build
          You can use 'profile-tasks' like `production` and `development`
          to change parameters (like optimizations level of the cljs compiler)"
         []
         (comp (speak)

               (cljs)))


(deftask run
         "The `run` task wraps the building of your application in some
          useful tools for local development: an http server, a file watcher
          a ClojureScript REPL and a hot reloading mechanism"
         []
         (comp (serve)
               (watch)
               (cljs-repl)

               #_(dirac)
               (reload)
               (build)))

(deftask production []
         (task-options! cljs {:optimizations :advanced})
         identity)

(deftask development []
         (task-options! cljs {:optimizations :none}
                        reload {:on-jsload 'provisdom.todo.app/reload})
         identity)

(deftask dev-cljs
         "Simple alias to run application in development mode"
         []
         (comp (development)
               (run)))


