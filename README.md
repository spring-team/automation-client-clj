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
              :parameters  [{:name "greeting" :pattern ".*" :required true}]}}

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

Command and Event handlers work together because event handlers can either run commands, or they can send messages
that embed commands.  In this way, event handlers can make suggestions, or they can directly take action.

## Bot Messages

### Simple Messages

Whenever a command handler is called, the parameter sent to the handler will have data indicating what channel the
message was sent from (here, channel can also mean direct message during a 1 on 1 conversation).  Calling the
`simple-message` function will send a message to this channel.

```
(api/simple-message o "simple message") ;; this just responds in whatever channel the message came from
```

Addressing a message to other channels is also possible:

```
(api/simple-message (api/user "user-name") "eh!") ;; send a DM to a user - could be considered rude
(api/simple-message (api/channel "repo-channel") "I have something to tell all of you") ;; send a DM to a channel
```

### Actionable Messages

Messages can also contain references to invocable things.  If you already have a command handler named
"hello-github-commit", then you can send a message giving users a button to click on.  It's like putting
a callback function into slack.

```
(api/actionable-message
  o
  {:text        "You might want to think about saying hello"
   :attachments [{:text        "here's a button you can use to say hello"
                  :actions     [{:text    "Say hello"
                                 :type    "button"
                                 :command {:rug            {:type "command_handler"
                                                            :name "hello-github-commit")}
                                           :parameters     [{:name "greeting" :value "HI!"}]}}]}]})
```

Most of the structure of this message is defined by [Slack Attachments][slack-attachments].  However, the `:command` in
the action is an Atomist-specific addition.  It allows the Slack action to reference one of your command handlers, and
it allows the message to partially apply some parameters to the command handler.  The parameter list can be empty.
An empty parameter list just means that the bot will have to ask more questions (if the reference command handlers has
required parameters).

[slack-messages]: "https://api.slack.com/docs/message-attachments"

You can create messages that have _lots_ of buttons actually.  Slack has some limitations on the number of actions per
attachment and on the number of attachments per message.

You can also add drop-down menus to your messages.  Selected values in these drop-downs can then become parameters
passed to your handler.

```
(api/actionable-message
  o
  {:text        "You might want to think about saying hello"
   :attachments [{:text        "here are some choices"
                  :actions     [{:text    "Say hello"
                                 :type    "select"
                                 :options [{:text "say Hi" :value "Hi"}
                                           {:text "say Yo" :value "Yo"}]
                                 :command {:rug            {:type "command_handler"
                                                            :name "hello-github-commit")}
                                           :parameter_name "greeting"
                                           :parameters     []}}]}]})
```



