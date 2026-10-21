### Ad blocking and privacy
* New cosmetic overlay zapper: transparent click traps, hidden off-screen
  popunder links and ad boxes in shadow roots are removed at runtime, on top of
  the network blocker.
* Cross-site navigations without a user gesture (script redirects) are blocked
  when ad blocking is enabled, while real link taps always go through.
