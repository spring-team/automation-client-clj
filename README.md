## Running an Automation Client

We need a generator for this but clojure automation clients.  Latest version is:

```
[automation-api-clj "0.2.1"]     ;; https://atomist.jfrog.io/atomist/libs-release-local 
```

### Getting started

See our example project (link when it's open sourced in a minute)

### Usage

You'll need a `config.edn` file that defines at least 3 things:

```
{:team-id      {:value "your_team_id"}
 :automation-namespaces ["fingerprints"]
 :name "fingerprints-clj"}
```

* The `:team-id` is your Atomist team identifier.  (TODO:  they still get this from the bot enrollment today.  link?)
* The `:name` should be something unique to identify this 'automation client'.
* The automation namespaces refer to the symbol names of namespaces that may contain handlers.  We will scan these
  namespaces for metadata on function vars when the automation client first starts up.

You can start up the automation client using in a repl using:

```
(require '[automation.core])
(require '[mount.core :as mount]'
(mount/start)
```

## Handlers

The actual automation handler functions are defined in namespaces that you listed in the above config.edn.  A handler is any
function that has Atomist metadata.

### Commands

Command handlers are just functions but they can declare requirements on parameters, mapped parameters, and secrets.
When an automation client starts up, it uses this metadata to define the binding with external sources of command
request events.  This is perfect for chatops use cases.  For example, if your team has invited the Atomist bot
to your slack team, then a Command Handler definition automatically becomes a new command that you can send to your
bot.

```
(ns fingerprints
  (:require [automation.core :as api]))

(defn
  ^{:command {:name        "hello-clj"
              :intent      ["hello clj"]
              :description "register a command"
              :secrets     ["github://user_token?scopes=repo"]
              :parameters  [{:name "owner" :pattern ".*" :required true}
                            {:name "repo" :pattern ".*" :required true}]}}

  handler-hello-clj
  [o]
  (api/simple-message o (format "for %s/%s, ignore standard build config"
                                (api/get-parameter-value o "owner")
                                (api/get-parameter-value o "repo"))))
```

The `:intent` means that you can now say `@atomist hello clj` wherever your bot has been invited.  Your handler
function will be delivered parameters, mapped_parameters, and secrets.

### Events

These are way more important than Commands.

```
(ns fingerprints
  (:require [automation.core :as api]))

(defn
  ^{:event   {:name        "hello-github-commit"
              :description "watch for commits"
              :secrets     ["github://org_token?scopes=repo"]
              :subscription "subscription CommitHappened {Commit {sha repo {name org {owner ownerType}}}}}}

  handler-hello-clj
  [o]
  (api/simple-message o (format "I just noticed a commit %s" (-> o :data :Commit first :sha)))
```

The `subscription` is a graphql query that allows you to query for data coming from:

* GitHub
* Slack
* CI systems (like Jenkins, Travis, Circle, etc.)
* Kubernetes
* Custom events

We love graphql for this because the schema grows as your team configures more webhooks, or pushes more data to
ingestion points.  You create these queries in the Atomist dashboard for your team.

