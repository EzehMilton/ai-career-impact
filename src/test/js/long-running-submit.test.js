'use strict';

// Behaviour of the long-running submission state, driven with fake DOM objects and a fake clock. Run by
// LongRunningSubmitScriptTest with Node's built-in test runner; no packages.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const { createLongRunningSubmit, init, ROTATE_MS } =
  require(path.join(__dirname, '..', '..', 'main', 'resources', 'static', 'js', 'long-running-submit.js'));

const REPORT_MESSAGES = ['Reviewing your assessment…', 'Preparing your detailed analysis…',
  'Writing your personalised guidance…', 'Checking your report…', 'Almost there…'];
const BUTTONS = 'button[type="submit"], button:not([type]), input[type="submit"]';

/** A minimal element: attributes, hidden, text, children, and querySelectorAll answers given per selector. */
function element(tagName, options = {}) {
  const el = {
    tagName,
    hidden: !!options.hidden,
    textContent: options.text || '',
    value: options.value || '',
    disabled: false,
    attrs: Object.assign({}, options.attrs),
    children: options.children || [],
    selectors: options.selectors || {},
    listeners: {},
    focused: false,
    setAttribute(name, value) { this.attrs[name] = String(value); },
    getAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attrs, name) ? this.attrs[name] : null; },
    removeAttribute(name) { delete this.attrs[name]; },
    contains(other) { return other === this || this.children.some((child) => child.contains(other)); },
    querySelectorAll(selector) { return this.selectors[selector] || []; },
    querySelector(selector) { return (this.selectors[selector] || [])[0] || null; },
    addEventListener(type, fn) { this.listeners[type] = fn; },
    focus() { this.focused = true; }
  };
  if (options.checkValidity) el.checkValidity = options.checkValidity;
  return el;
}

function clock() {
  let now = 0;
  let nextId = 1;
  let timers = [];
  return {
    setTimer(fn, ms) { const id = nextId++; timers.push({ id, fn, at: now + ms }); return id; },
    clearTimer(id) { timers = timers.filter((timer) => timer.id !== id); },
    advance(ms) {
      const until = now + ms;
      for (;;) {
        const due = timers.filter((timer) => timer.at <= until).sort((a, b) => a.at - b.at)[0];
        if (!due) break;
        timers = timers.filter((timer) => timer !== due);
        now = due.at;
        due.fn();
      }
      now = until;
    },
    pending() { return timers.length; }
  };
}

/** A page: an intro and a form card to hide, a form with one submit button, and the report panel. */
function page(options = {}) {
  const line = element('P', { text: REPORT_MESSAGES[0] });
  const heading = element('H2');
  const items = REPORT_MESSAGES.map((text) => element('LI', { text }));
  const panel = element('SECTION', {
    hidden: true,
    attrs: { 'data-loading-label': 'Preparing…' },
    selectors: { '[data-loading-messages] li': items, '[data-loading-message]': [line],
      '[data-loading-heading]': [heading] }
  });
  const button = element('BUTTON', { text: 'Prepare my detailed report' });
  const form = element('FORM', {
    selectors: { [BUTTONS]: [button] },
    checkValidity: options.checkValidity || (() => true)
  });
  const intro = element('SECTION');
  const card = element('SECTION', { children: [form] });
  const docSelectors = { '[data-loading-hide]': options.noHide ? [] : [intro, card], '[data-loading-replace]': [] };
  const doc = element('#document', { selectors: docSelectors });
  const time = clock();
  const controller = createLongRunningSubmit(form, {
    doc, panel, setTimer: time.setTimer, clearTimer: time.clearTimer, rotateMs: ROTATE_MS
  });
  return { doc, panel, line, heading, button, form, intro, card, time, controller };
}

function submitEvent(defaultPrevented = false) {
  return { defaultPrevented, prevented: false, preventDefault() { this.prevented = true; } };
}

test('a valid submission shows the panel, hides the form area and blocks the button without changing the request', () => {
  const p = page();
  const event = submitEvent();
  p.controller.onSubmit(event);

  assert.equal(event.prevented, false, 'the first submission is never cancelled');
  assert.equal(p.panel.hidden, false);
  assert.equal(p.intro.hidden, true);
  assert.equal(p.card.hidden, true);
  assert.equal(p.form.getAttribute('aria-busy'), 'true');
  assert.equal(p.button.getAttribute('aria-disabled'), 'true');
  assert.equal(p.button.textContent, 'Preparing…');
  // Not yet disabled: a control disabled during the submit event would be left out of the request.
  assert.equal(p.button.disabled, false);
  p.time.advance(0);
  assert.equal(p.button.disabled, true);
  assert.equal(p.line.textContent, REPORT_MESSAGES[0]);
  assert.equal(p.heading.focused, true);
});

test('a form the browser considers invalid keeps its normal state', () => {
  const p = page({ checkValidity: () => false });
  const event = submitEvent();
  p.controller.onSubmit(event);

  assert.equal(event.prevented, false, 'the browser keeps handling its own validation');
  assert.equal(p.panel.hidden, true);
  assert.equal(p.card.hidden, false);
  assert.equal(p.button.getAttribute('aria-disabled'), null);
  assert.equal(p.controller.isSubmitting(), false);
});

test('a submission another script already cancelled does not show the panel', () => {
  const p = page();
  p.controller.onSubmit(submitEvent(true));
  assert.equal(p.panel.hidden, true);
  assert.equal(p.controller.isSubmitting(), false);
});

test('a second submission, by click or Enter, is cancelled so the request is sent once', () => {
  const p = page();
  p.controller.onSubmit(submitEvent());
  const second = submitEvent();
  p.controller.onSubmit(second);
  const third = submitEvent();
  p.controller.onSubmit(third);

  assert.equal(second.prevented, true);
  assert.equal(third.prevented, true);
  assert.equal(p.panel.hidden, false);
});

test('the messages rotate in order and stay on the last one', () => {
  const p = page();
  p.controller.onSubmit(submitEvent());
  const seen = [p.line.textContent];
  for (let i = 1; i < REPORT_MESSAGES.length; i += 1) {
    p.time.advance(ROTATE_MS);
    seen.push(p.line.textContent);
  }
  assert.deepEqual(seen, REPORT_MESSAGES);
  p.time.advance(ROTATE_MS * 5);
  assert.equal(p.line.textContent, 'Almost there…');
  assert.equal(p.time.pending(), 0, 'nothing keeps running once the last message shows');
});

test('reset restores the page exactly, so a page restored from the back/forward cache is usable again', () => {
  const p = page();
  p.controller.onSubmit(submitEvent());
  p.time.advance(ROTATE_MS * 2);
  p.controller.reset();

  assert.equal(p.panel.hidden, true);
  assert.equal(p.intro.hidden, false);
  assert.equal(p.card.hidden, false);
  assert.equal(p.form.getAttribute('aria-busy'), null);
  assert.equal(p.button.disabled, false);
  assert.equal(p.button.getAttribute('aria-disabled'), null);
  assert.equal(p.button.textContent, 'Prepare my detailed report');
  assert.equal(p.line.textContent, REPORT_MESSAGES[0]);
  assert.equal(p.time.pending(), 0);
  // And it can be submitted again.
  const again = submitEvent();
  p.controller.onSubmit(again);
  assert.equal(again.prevented, false);
  assert.equal(p.panel.hidden, false);
});

test('a replace container hides its other children and keeps the panel and whatever holds it', () => {
  const p = page({ noHide: true });
  const heading = element('H2');
  const notice = element('P');
  const wrapper = element('DIV', { children: [p.panel] });
  const container = element('SECTION', { children: [heading, notice, p.form, wrapper] });
  p.doc.selectors['[data-loading-replace]'] = [container];
  p.controller.onSubmit(submitEvent());

  assert.equal(heading.hidden, true);
  assert.equal(notice.hidden, true);
  assert.equal(p.form.hidden, true);
  assert.equal(wrapper.hidden, false);
  assert.equal(p.panel.hidden, false);
});

test('without any marked area the submitted form itself is hidden', () => {
  const p = page({ noHide: true });
  p.controller.onSubmit(submitEvent());
  assert.equal(p.form.hidden, true);
  assert.equal(p.card.hidden, false);
});

test('init wires only marked forms, resets on a restored page and does nothing without a panel', () => {
  const marked = element('FORM', { checkValidity: () => true });
  const panel = element('SECTION', { hidden: true });
  const doc = element('#document', {
    selectors: { '[data-loading-panel]': [panel], 'form[data-long-running]': [marked] }
  });
  const win = { listeners: {}, setTimeout: () => 0, clearTimeout: () => {},
    addEventListener(type, fn) { this.listeners[type] = fn; } };
  const controllers = init(doc, win);

  assert.equal(controllers.length, 1);
  assert.equal(typeof marked.listeners.submit, 'function');
  marked.listeners.submit(submitEvent());
  assert.equal(panel.hidden, false);
  win.listeners.pageshow({ persisted: false });
  assert.equal(panel.hidden, false, 'an ordinary page load does not reset a submission under way');
  win.listeners.pageshow({ persisted: true });
  assert.equal(panel.hidden, true);
  assert.equal(controllers[0].isSubmitting(), false);

  const bare = element('#document', { selectors: { 'form[data-long-running]': [element('FORM')] } });
  assert.deepEqual(init(bare, win), []);
});
