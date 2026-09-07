/* Coalescing key pipeline for the stateless replay client (ticket 06).

   The server derives every snapshot from the whole Session, so each movement step is a
   full POST replaying (size, seed, cumulative keys). A player holds W or mashes keys
   much faster than a round trip, so naive "one request per keydown" would either drop
   steps or let responses land out of order. This pipeline keeps at most ONE request in
   flight and never loses a press:

     - press(key) appends to `pending` and pumps.
     - While a request is in flight, further presses accumulate in `pending`.
     - When the in-flight request resolves, the pipeline commits those keys (slices
       only what it actually sent — presses that arrived during the flight survive),
       then pumps again with the newest cumulative keys.

   So requests are strictly ordered and the final committed keys always equal the full
   sequence pressed — replay(same keys) == terminal state (ticket 06 criterion 2).

   DOM-free: `send` is injected, so this is unit-testable under node. Classic script;
   exposes window.KeyPipe and module.exports under node. */
(function (root) {
  'use strict';

  /* send: (cumulativeKeys) => Promise — resolves once the snapshot reflecting those
     keys has been applied. onError optional; a rejected send keeps pending keys so the
     next press (or a later successful drain) retries them. */
  function createPipeline(send, onError) {
    var committed = ''; // keys the engine has acknowledged (basis of the current frame)
    var pending = '';   // pressed, not yet acknowledged
    var busy = false;

    function drain() {
      if (busy || pending === '') {
        return;
      }
      busy = true;
      var sentKeys = committed + pending;
      var sentLen = pending.length;
      Promise.resolve().then(function () {
        return send(sentKeys);
      }).then(function () {
        committed = sentKeys;
        pending = pending.slice(sentLen);
        busy = false;
        drain();
      }, function (err) {
        busy = false; // keep pending: a later press retries it
        if (onError) {
          onError(err);
        }
      });
    }

    function press(key) {
      pending += key;
      drain();
    }

    /* Start a fresh episode (e.g. a new game / a loaded save): drop any unacknowledged
       presses and reset the committed baseline. */
    function reset(baseKeys) {
      committed = baseKeys || '';
      pending = '';
      busy = false;
    }

    function appliedKeys() {
      return committed;
    }

    return { press: press, reset: reset, appliedKeys: appliedKeys };
  }

  var api = { createPipeline: createPipeline };
  if (typeof module === 'object' && module.exports) {
    module.exports = api;
  } else {
    root.KeyPipe = api;
  }
})(typeof self !== 'undefined' ? self : this);
