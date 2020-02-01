(defproject jedi-time "0.1.4"
  :description "Datafiable/Navigable protocol extensions for the core java.time objects."
  :url "https://github.com/jimpil/jedi-time"
  :author "Dimitrios Piliouras <jimpil1985@gmail.com>"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]]
  :global-vars {*warn-on-reflection* true}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                 ; ["vcs" "tag"]
                  ["deploy" "clojars"]]
  )
