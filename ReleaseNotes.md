# Release Notes

## 0.1.2

- Add :lock-transition feature to fsm
  When the fsm :lock-transition feature is used, the transition is locked,
  so multiple threads can't update the state concurrently.

  Fixes #1, which was re-opened.

## 0.1.1

- Ensure state-fn for terminal state is called in event-machine-loop
  Fixes #2.

- Fix race condition in handling transitions from events
  Fixes #1.

## 0.1.0

Initial version.
