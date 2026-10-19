/*
 * Moves a visitor from "Confirming your payment" to their paid report once the verified Stripe
 * webhook has activated it. This script only reads the saved status back from the application: it
 * never talks to Stripe, never activates anything and never starts a checkout, so checking again
 * cannot charge anyone twice.
 *
 * Served from this application so the content security policy can stay at script-src 'self'.
 */
(function () {
  'use strict';

  var POLL_INTERVAL_MS = 1500;
  var POLL_TIMEOUT_MS = 30000;

  /**
   * The polling state machine, with its side effects passed in so it can be tested without a browser.
   *
   * fetchStatus() resolves to a status name such as "PENDING" or "ACTIVE", "NONE" when there is no
   * purchase, or null when the answer is unknown (network error, server error). Unknown answers keep
   * polling until the timeout rather than ending the wait on one dropped request.
   */
  function createPaymentConfirmation(deps) {
    var cycle = 0;
    var pollTimer = null;
    var timeoutTimer = null;
    var finished = false;

    function clearTimers() {
      if (pollTimer !== null) deps.clearTimer(pollTimer);
      if (timeoutTimer !== null) deps.clearTimer(timeoutTimer);
      pollTimer = null;
      timeoutTimer = null;
    }

    function finish(state) {
      finished = true;
      clearTimers();
      deps.show(state);
    }

    function check(ownCycle) {
      pollTimer = null;
      return deps.fetchStatus().then(function (status) {
        if (finished) return;
        // A late ACTIVE still wins, even after the wait has timed out.
        if (status === 'ACTIVE') {
          finished = true;
          clearTimers();
          deps.navigate(deps.reportUrl);
          return;
        }
        if (ownCycle !== cycle) return;
        if (status === 'PENDING' || status === null) {
          if (timeoutTimer !== null) {
            pollTimer = deps.setTimer(function () { check(ownCycle); }, deps.intervalMs);
          }
          return;
        }
        // FAILED, REFUNDED, REVOKED or no purchase at all: waiting longer cannot help.
        finish('unavailable');
      });
    }

    /** Starts (or restarts) a polling window. Used on page load and by "Check payment status". */
    function start() {
      if (finished) return Promise.resolve();
      cycle += 1;
      var ownCycle = cycle;
      clearTimers();
      deps.show('confirming');
      timeoutTimer = deps.setTimer(function () {
        if (ownCycle !== cycle || finished) return;
        clearTimers();
        deps.show('delayed');
      }, deps.timeoutMs);
      return check(ownCycle);
    }

    return { start: start };
  }

  function fetchStatus(url) {
    return fetch(url, { headers: { Accept: 'application/json' }, cache: 'no-store', credentials: 'same-origin' })
      .then(function (response) {
        if (response.status === 404) return 'NONE';
        if (!response.ok) return null;
        return response.json().then(function (body) {
          return body && typeof body.status === 'string' ? body.status : null;
        });
      })
      .catch(function () { return null; });
  }

  function init(root, win, fetchImpl) {
    var statusUrl = root.getAttribute('data-status-url');
    var reportUrl = root.getAttribute('data-report-url');
    // Both are rendered by the server as same-origin paths; refuse anything else.
    if (!/^\/[^/]/.test(statusUrl || '') || !/^\/[^/]/.test(reportUrl || '')) return null;
    var panels = root.querySelectorAll('[data-payment-state]');
    var confirmation = createPaymentConfirmation({
      fetchStatus: fetchImpl || function () { return fetchStatus(statusUrl); },
      navigate: function (url) { win.location.assign(url); },
      show: function (state) {
        for (var i = 0; i < panels.length; i += 1) {
          panels[i].hidden = panels[i].getAttribute('data-payment-state') !== state;
        }
      },
      setTimer: function (fn, ms) { return win.setTimeout(fn, ms); },
      clearTimer: function (id) { win.clearTimeout(id); },
      reportUrl: reportUrl,
      intervalMs: POLL_INTERVAL_MS,
      timeoutMs: POLL_TIMEOUT_MS
    });
    var checkLinks = root.querySelectorAll('[data-payment-check]');
    for (var i = 0; i < checkLinks.length; i += 1) {
      checkLinks[i].addEventListener('click', function (event) {
        event.preventDefault();
        confirmation.start();
      });
    }
    if (root.getAttribute('data-auto-poll') === 'true') confirmation.start();
    return confirmation;
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
      createPaymentConfirmation: createPaymentConfirmation,
      init: init,
      POLL_INTERVAL_MS: POLL_INTERVAL_MS,
      POLL_TIMEOUT_MS: POLL_TIMEOUT_MS
    };
  }
  else if (typeof document !== 'undefined') {
    var roots = document.querySelectorAll('[data-payment-confirmation]');
    for (var r = 0; r < roots.length; r += 1) init(roots[r], window);
  }
})();
