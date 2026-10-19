'use strict';

// Behaviour of the payment-confirmation poller, driven with a fake clock and a scripted status
// endpoint. Run by PaymentConfirmationScriptTest with Node's built-in test runner; no packages.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const {
  createPaymentConfirmation,
  POLL_INTERVAL_MS,
  POLL_TIMEOUT_MS
} = require(path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'payment-confirmation.js'));

const REPORT_URL = '/professional-assessment/6f1e2d3c-4b5a-4968-8776-a5b4c3d2e1f0/report';

function flush() {
  return new Promise((resolve) => setImmediate(resolve));
}

/** statuses: answers returned in order; the last one repeats. */
function harness(statuses) {
  const answers = statuses.slice();
  let now = 0;
  let nextId = 1;
  const timers = [];
  const calls = { fetches: [], navigated: [], shown: [] };
  const deps = {
    fetchStatus: () => {
      calls.fetches.push(now);
      return Promise.resolve(answers.length > 1 ? answers.shift() : answers[0]);
    },
    navigate: (url) => calls.navigated.push(url),
    show: (state) => calls.shown.push(state),
    setTimer: (fn, ms) => {
      const id = nextId++;
      timers.push({ id, fn, at: now + ms });
      return id;
    },
    clearTimer: (id) => {
      const index = timers.findIndex((timer) => timer.id === id);
      if (index >= 0) timers.splice(index, 1);
    },
    reportUrl: REPORT_URL,
    intervalMs: POLL_INTERVAL_MS,
    timeoutMs: POLL_TIMEOUT_MS
  };
  async function advance(ms) {
    const end = now + ms;
    for (;;) {
      timers.sort((a, b) => a.at - b.at || a.id - b.id);
      const due = timers[0];
      if (!due || due.at > end) break;
      timers.shift();
      now = due.at;
      due.fn();
      await flush();
    }
    now = end;
  }
  return {
    calls,
    advance,
    setAnswers: (next) => answers.splice(0, answers.length, ...next),
    timersPending: () => timers.length,
    confirmation: createPaymentConfirmation(deps)
  };
}

test('polls every 1 to 2 seconds and gives up after about 30 seconds', () => {
  assert.ok(POLL_INTERVAL_MS >= 1000 && POLL_INTERVAL_MS <= 2000);
  assert.equal(POLL_TIMEOUT_MS, 30000);
});

test('ACTIVE redirects to the full report straight away', async () => {
  const h = harness(['ACTIVE']);
  await h.confirmation.start();

  assert.deepEqual(h.calls.shown, ['confirming']);
  assert.deepEqual(h.calls.navigated, [REPORT_URL]);
  assert.equal(h.timersPending(), 0);
});

test('PENDING keeps polling at the interval until ACTIVE, then redirects once', async () => {
  const h = harness(['PENDING', 'PENDING', 'ACTIVE']);
  await h.confirmation.start();
  await h.advance(POLL_INTERVAL_MS * 5);

  assert.deepEqual(h.calls.fetches, [0, POLL_INTERVAL_MS, POLL_INTERVAL_MS * 2]);
  assert.deepEqual(h.calls.navigated, [REPORT_URL]);
  assert.equal(h.timersPending(), 0);
});

test('polling stops at the timeout and shows the delayed-confirmation message', async () => {
  const h = harness(['PENDING']);
  await h.confirmation.start();
  await h.advance(POLL_TIMEOUT_MS);

  assert.equal(h.calls.shown.at(-1), 'delayed');
  assert.ok(h.calls.fetches.every((at) => at < POLL_TIMEOUT_MS));
  const fetchesAtTimeout = h.calls.fetches.length;

  await h.advance(POLL_TIMEOUT_MS * 4);
  assert.equal(h.calls.fetches.length, fetchesAtTimeout, 'no requests after the timeout');
  assert.equal(h.timersPending(), 0);
  assert.deepEqual(h.calls.navigated, []);
});

test('the manual check after a timeout reaches the report once the webhook has activated it', async () => {
  const h = harness(['PENDING']);
  await h.confirmation.start();
  await h.advance(POLL_TIMEOUT_MS);
  assert.equal(h.calls.shown.at(-1), 'delayed');

  h.setAnswers(['ACTIVE']);
  await h.confirmation.start();

  assert.equal(h.calls.shown.at(-1), 'confirming');
  assert.deepEqual(h.calls.navigated, [REPORT_URL]);
});

test('a manual check that is still PENDING waits one more window and then stops again', async () => {
  const h = harness(['PENDING']);
  await h.confirmation.start();
  await h.advance(POLL_TIMEOUT_MS);
  const firstWindow = h.calls.fetches.length;

  await h.confirmation.start();
  await h.advance(POLL_TIMEOUT_MS);
  assert.ok(h.calls.fetches.length > firstWindow);
  assert.equal(h.calls.shown.at(-1), 'delayed');

  const afterSecondWindow = h.calls.fetches.length;
  await h.advance(POLL_TIMEOUT_MS);
  assert.equal(h.calls.fetches.length, afterSecondWindow);
});

test('a failed, refunded or missing purchase stops waiting and never redirects', async () => {
  for (const status of ['FAILED', 'REFUNDED', 'REVOKED', 'NONE']) {
    const h = harness([status]);
    await h.confirmation.start();
    await h.advance(POLL_TIMEOUT_MS);

    assert.equal(h.calls.shown.at(-1), 'unavailable', status);
    assert.equal(h.calls.fetches.length, 1, status);
    assert.deepEqual(h.calls.navigated, [], status);
  }
});

test('an unknown answer such as a network error keeps waiting rather than giving up', async () => {
  const h = harness([null, null, 'ACTIVE']);
  await h.confirmation.start();
  await h.advance(POLL_INTERVAL_MS * 2);

  assert.equal(h.calls.fetches.length, 3);
  assert.deepEqual(h.calls.navigated, [REPORT_URL]);
});

/** Just enough DOM to exercise init() against the attributes the result page renders. */
function fakePage(autoPoll, statusUrl) {
  const element = (state, extra = {}) => ({
    hidden: false,
    listeners: {},
    attributes: Object.assign({ 'data-payment-state': state }, extra),
    getAttribute(name) { return this.attributes[name] ?? null; },
    addEventListener(type, fn) { this.listeners[type] = fn; }
  });
  const panels = [element('confirming'), element('delayed'), element('unavailable')];
  const checkLink = element(null);
  const root = {
    attributes: {
      'data-status-url': statusUrl,
      'data-report-url': REPORT_URL,
      'data-auto-poll': String(autoPoll)
    },
    getAttribute(name) { return this.attributes[name] ?? null; },
    querySelectorAll(selector) {
      if (selector === '[data-payment-state]') return panels;
      if (selector === '[data-payment-check]') return [checkLink];
      return [];
    }
  };
  const assigned = [];
  const win = {
    location: { assign: (url) => assigned.push(url) },
    setTimeout: () => 0,
    clearTimeout: () => {}
  };
  const visible = () => panels.filter((panel) => !panel.hidden).map((panel) => panel.attributes['data-payment-state']);
  return { root, win, checkLink, assigned, visible };
}

test('on return from Stripe the page polls by itself and redirects when ACTIVE', async () => {
  const { init } = require(path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'payment-confirmation.js'));
  const page = fakePage(true, '/professional-assessment/abc/payment-status');
  init(page.root, page.win, () => Promise.resolve('ACTIVE'));
  await flush();

  assert.deepEqual(page.assigned, [REPORT_URL]);
});

test('the Check payment status button checks without leaving the page, then redirects when ACTIVE', async () => {
  const { init } = require(path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'payment-confirmation.js'));
  const page = fakePage(false, '/professional-assessment/abc/payment-status');
  let fetches = 0;
  init(page.root, page.win, () => { fetches += 1; return Promise.resolve('ACTIVE'); });
  await flush();
  assert.equal(fetches, 0, 'no automatic polling without a Stripe return');

  let prevented = false;
  page.checkLink.listeners.click({ preventDefault: () => { prevented = true; } });
  await flush();

  assert.ok(prevented);
  assert.equal(fetches, 1);
  assert.deepEqual(page.visible(), ['confirming']);
  assert.deepEqual(page.assigned, [REPORT_URL]);
});

test('a status URL that is not a same-origin path is refused', () => {
  const { init } = require(path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'payment-confirmation.js'));
  for (const url of ['https://evil.example/status', '//evil.example/status', '']) {
    const page = fakePage(true, url);
    assert.equal(init(page.root, page.win, () => Promise.resolve('ACTIVE')), null, url);
    assert.deepEqual(page.assigned, []);
  }
});
