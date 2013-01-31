# pallet-fsm

A finite state machine library.

## Main concepts

The library provides three state machines that cover four main concepts, or
concerns.

The `fsm` provides a simple state transition table. Transitions are verified
against a set of permitted transitions. Optionally transitions run specified
functions on entry and exit of a state. The `fsm` state is a keyword, and is
maintained by the caller of the fsm. This state machine is purely functional.
The `fsm` is just for verifying transition validity, and applying on-enter and
on-exit functions.

The `stateful-fsm` uses a `fsm` internally and provides atomic management of the
the state, and arbitrary `state-data`. Transitions are made by applying an
externally supplied function that is a applied to the current state. Optionally
transitions run specified functions on entery and exit of a state. Timeouts are
optionally supported on each state. The `stateful-fsm` records the current state
and provides atomic state updates, in addition to the functionality provided by
`fsm`

The `event-machine` uses a `stateful-fsm`, and provides a mapping from events to
state transitions. Each state is associated with an `event-fn` that is notified
of incoming events. An event is an event keyword, and arbitrary `event-data`. An
`event-fn` can make any transition permitted by the underlying `stateful-fsm`.

Finally, the `poll-event-machine-fn` and `event-machine-loop-fn` provide a
mechanism for running a per state `state-fn` against the current state of an
`event-machine`.

## Usage

The library has a DSL for producing the configuration map that is passed to
`fsm`, `stateful-fsm` or `event-machine`. The DSL is optional.

### Basic FSM
The basic `fsm` just verifies externally specified transitions.

```clj
(use 'pallet.algo.fsm.fsm 'pallet.algo.fsm.fsm-dsl)
(let [{:keys [transition valid-state? valid-transition?] :as sm}
      (fsm
        (fsm-config
          (state :locked
            (valid-transitions :locked :open))
          (state :open
            (valid-transitions :locked))))]
  (valid-state? :locked) ; => true
  (valid-state? :broken) ; => false

  (valid-transition? :locked :open) ; => true
  (valid-transition? :open :open) ; => false

  (transition :locked :open) ; => :open
  (transition :open :locked)) ; => :locked
```

`fsm` also provides on-enter and on-exit functions for states, that can be used
to manage external state. Note that on-enter and on-exit functions can not
(functionally) modify the fsm state.

```clj
(use 'pallet.algo.fsm.fsm 'pallet.algo.fsm.fsm-dsl)
(let [exit-locked (atom nil)
      enter-open (atom nil)
      {:keys [transition valid-state? valid-transition?] :as sm}
      (fsm
        (fsm-config
          (state :locked
            (valid-transitions :locked :open)
            (on-exit (fn [_] (reset! exit-locked true)))
          (state :open
            (valid-transitions :locked)
            (on-enter (fn [_] (reset! enter-open true)))))))]
  (transition :locked :open) ; => :open
  @exit-locked ; => true
  @enter-open ; => true)
```

Logging and other features can be added with `using-fsm-features`.

```clj
(use 'pallet.algo.fsm.fsm 'pallet.algo.fsm.fsm-dsl)
(let [{:keys [transition valid-state? valid-transition?] :as sm}
      (fsm
        (fsm-config
          (using-fsm-features (with-transition-logger :debug))
          (state :locked
            (valid-transitions :locked :open))
          (state :open
            (valid-transitions :locked))))]
  ...)
```

### Stateful FSM

The `stateful-fsm` maintains it's state in an atom, and provides atomic
transitions. The `initial-state` needs to be specified. An option
`initial-state-data` may also be given. The transition function takes
a function that will be used to update the state map atomically.

```clj
(use 'pallet.algo.fsm.stateful-fsm 'pallet.algo.fsm.fsm-dsl)
(let [{:keys [transition state reset valid-state? valid-transition?] :as sm}
      (stateful-fsm
        (fsm-config
          (initial-state :locked)
          (initial-state-data {:code 123})
          (state :locked
            (valid-transitions :locked :open))
          (state :open
            (valid-transitions :locked))))]
  (state) ; => {:state-kw :locked :state-data {:code "123"}}
  (transition
    #(assoc % :state-kw :locked :state-data {:so-far "1"})))
    ; => {:state-kw :locked :state-data {:so-far "1"}}
```

The `:timeout` feature allows the specification of timeouts for a state. If a
timeout expires the state will transition to the `:timed-out` state. Features
are specified with the `using-stateful-fsm-features` form.

```clj
(let [{:keys [transition state reset valid-state? valid-transition?] :as sm}
      (stateful-fsm
        (fsm-config
          (initial-state :locked)
          (initial-state-data {:code 123})
          (using-stateful-fsm-features :timeout)
          (state :locked
            (valid-transitions :locked :open))
          (state :open
            (valid-transitions :locked :timed-out))
          (state :timed-out))]
  (transition
    #(assoc % :state-kw :open :timeout {:s 1}))
    ; => {:state-kw :open}
  (Thread/sleep 2000) ; wait 2s
  (state) ; => {:state-kw :timed-out})
```

The `stateful-fsm` also provides a `:history` feature, that will record all
states as a sequence available on the state's `:history` keyword.

The `fsm` features can still be used, as `stateful-fsm` is implemented on top of
`fsm`.

## Event Machine

The `event-machine` adds the ability to respond to events, sent to the fsm via
it's `:event` function.

Events are handled by a state specific `event-handler`, which can implement an
arbitrary event to state transition mapping, and can freely update the
:state-data in the state.

```clj
(use 'pallet.algo.fsm.event-machin 'pallet.algo.fsm.fsm-dsl)
(let [locked (fn [state event event-data]
               (if (= event :open-sesame)
                 (assoc state :state-kw :open :state-data "welcome")
                 state))
      {:keys [event state] :as sm}
      (event-machine
        (event-machine-config
          (initial-state :locked)
          (state :locked
            (valid-transitions :locked :open)
            (event-handler locked)
          (state :open
            (valid-transitions :locked)))]
  (event :trying nil) ; => {:state-kw :locked}
  (event :open-sesame nil) ; => {:state-kw :open :state-data "welcome"})
```

The `fsm` and `event-machine` features can still be used, as `event-machine` is
implemented on top of `stateful-fsm`.

## API Docs

[API documentation](http://palletops.com/pallet-fsm/)

## Installation

To use pallet-fsm, add the following to your `:dependencies` in
`project.clj`:

```clj
[pallet-fsm "0.2.0"]
```

## License

Copyright © 2012 Hugo Duncan

Distributed under the Eclipse Public License.
