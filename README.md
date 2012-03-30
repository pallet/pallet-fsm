# pallet-fsm

A finite state machine library.

## Main concepts

The library provides three state machines that cover four main concepts, or
concerns.

The `fsm` provides a simple state transition table. Transitions are verified
against a set of permitted transitions. Optionally transitions run specified
functions on entery and exit of a state. The `fsm` state is a keyword, and is
maintained by the caller of the fsm. This state machine is purely functional.
The `fsm` is just for verifying transition validity, and applying on-enter and
on-exit functions.

The `stateful-fsm` uses a `fsm` internally and provides atomic management of the
the state, and arbitrary `state-data`. Transitions are made by applying an
externally supplied function that is a applied to the current state. Optionally
transitions run specified functions on entery and exit of a state. Additionally,
timeouts are optionally supported on each state. The `stateful-fsm` records the
current state and provides atomic state updates, in addition to the
functionality provided by `fsm`

The `event-machine` uses a `stateful-fsm`, and provides a mapping from events to
state transitions. Each state is associated with an `event-fn` that is notified
of incoming events. An event is an event keyword, and arbitrary `event-data`. An
`event-fn` can make any transition permitted by the underlying `stateful-fsm`.

Finally, the `poll-event-machine-fn` and `event-machine-loop-fn` provide a
mechanism for running a per state `state-fn` against the current state of an
`event-machine`.

## Usage

See tests for now.

## License

Copyright © 2012 Hugo Duncan

Distributed under the Eclipse Public License.
