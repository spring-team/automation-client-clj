## Running an Automation Client

We need a generator for this but clojure automation clients.  Latest version is:

```
[automation-api-clj "0.1.0"]     ;; @ "https://sforzando.artifactoryonline.com/sforzando/libs-release-local
```

### Usage

You'll need a `config.edn` file that looks like the following:

```
{:domain       "prod.atomist.services."
 :team-id      {:value "T095SFFBK"}
 :automation-namespaces ["fingerprints"]
 :name "fingerprints-clj"}
```

The `:name` should be something unique to identify the entire namespace.  The automation namespaces
refer to the symbol names of any namespaces that we should check for handler metadata.

You can start up the automation client using `mount/start`

```
(require '[automation.core])
(mount/start)
```

The actual automation handler functions are defined in any namespaces that you list above.  A handler is any
function that has Atomist metadata.

```
(ns fingerprints
  (:require [automation.core :as api]))

(defn
  ^{:command {:name        (name :ignore-build-update)
              :description ""
              :secrets     ["github://user_token?scopes=repo"]
              :parameters  [{:name "owner" :pattern ".*" :required true}
                            {:name "repo" :pattern ".*" :required true}]
              }}
  handler-ignore-build-update
  ""
  [o]
  (api/simple-message o (format "for %s/%s, ignore standard build config"
                                (api/get-parameter-value o "owner")
                                (api/get-parameter-value o "repo"))))
```

