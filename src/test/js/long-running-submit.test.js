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

/** A minimal element: attributes, classes, hidden, text, children, and querySelectorAll answers per selector. */
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
    classes: new Set(),
    get classList() {
      const classes = this.classes;
      return { add(name) { classes.add(name); }, remove(name) { classes.delete(name); }, contains: (name) => classes.has(name) };
    },
    setAttribute(name, value) { this.attrs[name] = String(value); },
    getAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attrs, name) ? this.attrs[name] : null; },
    removeAttribute(name) { delete this.attrs[name]; },
    contains(other) { return other === this || this.children.some((child) => child.contains(other)); },
    querySelectorAll(selector) { return this.selectors[selector] || []; },
    querySelector(selector) { return (this.selectors[selector] || [])[0] || null; },
    addEventListener(type, fn) { this.listeners[type] = fn; },
    appendChild(child) {
      if (child.parentNode) child.parentNode.children = child.parentNode.children.filter((other) => other !== child);
      this.children.push(child);
      child.parentNode = this;
      return child;
    },
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

/** A page: a header and a main holding a form with one submit button, and the overlay at the end of the body. */
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
  const header = element('HEADER', { attrs: options.headerAttrs });
  const main = element('MAIN', { children: [element('SECTION', { children: [form] })] });
  const body = element('BODY', { children: [header, main, panel] });
  const doc = element('#document');
  doc.body = body;
  doc.documentElement = element('HTML');
  const time = clock();
  const controller = createLongRunningSubmit(form, {
    doc, panel, setTimer: time.setTimer, clearTimer: time.clearTimer, rotateMs: ROTATE_MS
  });
  return { doc, panel, line, heading, button, form, header, main, time, controller };
}

function submitEvent(defaultPrevented = false) {
  return { defaultPrevented, prevented: false, preventDefault() { this.prevented = true; } };
}

test('a valid submission shows the overlay over an inert page and blocks the button without changing the request', () => {
  const p = page();
  const event = submitEvent();
  p.controller.onSubmit(event);

  assert.equal(event.prevented, false, 'the first submission is never cancelled');
  assert.equal(p.panel.hidden, false);
  assert.equal(p.panel.getAttribute('inert'), null, 'the overlay itself stays usable');
  assert.equal(p.header.getAttribute('inert'), '');
  assert.equal(p.main.getAttribute('inert'), '');
  // The page stays in view behind the overlay: nothing on it is hidden, so nothing moves.
  assert.equal(p.header.hidden, false);
  assert.equal(p.main.hidden, false);
  assert.equal(p.form.hidden, false);
  assert.equal(p.doc.documentElement.classList.contains('loading-active'), true);
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
  assert.equal(p.main.getAttribute('inert'), null);
  assert.equal(p.doc.documentElement.classList.contains('loading-active'), false);
  assert.equal(p.button.getAttribute('aria-disabled'), null);
  assert.equal(p.controller.isSubmitting(), false);
});

test('a submission another script already cancelled does not show the panel', () => {
  const p = page();
  p.controller.onSubmit(submitEvent(true));
  assert.equal(p.panel.hidden, true);
  assert.equal(p.main.getAttribute('inert'), null);
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
  assert.equal(p.header.getAttribute('inert'), null);
  assert.equal(p.main.getAttribute('inert'), null);
  assert.equal(p.doc.documentElement.classList.contains('loading-active'), false);
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

test('reset leaves an element that was already inert as it was', () => {
  const p = page({ headerAttrs: { inert: '' } });
  p.controller.onSubmit(submitEvent());
  p.controller.reset();
  assert.equal(p.header.getAttribute('inert'), '');
  assert.equal(p.main.getAttribute('inert'), null);
});

test('whatever holds the overlay is never made inert', () => {
  const p = page();
  const wrapper = element('DIV', { children: [p.panel] });
  p.doc.body.children = [p.header, p.main, wrapper];
  p.controller.onSubmit(submitEvent());
  assert.equal(wrapper.getAttribute('inert'), null);
  assert.equal(p.main.getAttribute('inert'), '');
});

test('init wires only marked forms, resets on a restored page and does nothing without a panel', () => {
  const marked = element('FORM', { checkValidity: () => true });
  const panel = element('SECTION', { hidden: true });
  const main = element('MAIN', { children: [marked, panel] });
  panel.parentNode = main;
  const doc = element('#document', {
    selectors: { '[data-loading-panel]': [panel], 'form[data-long-running]': [marked] }
  });
  doc.body = element('BODY', { children: [main] });
  doc.documentElement = element('HTML');
  const win = { listeners: {}, setTimeout: () => 0, clearTimeout: () => {},
    addEventListener(type, fn) { this.listeners[type] = fn; } };
  const controllers = init(doc, win);

  assert.equal(controllers.length, 1);
  assert.equal(panel.parentNode, doc.body, 'the overlay is moved to the end of the body, out of its card');
  assert.equal(doc.body.children.at(-1), panel);
  assert.equal(typeof marked.listeners.submit, 'function');
  marked.listeners.submit(submitEvent());
  assert.equal(panel.hidden, false);
  assert.equal(main.getAttribute('inert'), '');
  win.listeners.pageshow({ persisted: false });
  assert.equal(panel.hidden, false, 'an ordinary page load does not reset a submission under way');
  win.listeners.pageshow({ persisted: true });
  assert.equal(panel.hidden, true);
  assert.equal(main.getAttribute('inert'), null);
  assert.equal(controllers[0].isSubmitting(), false);

  const bare = element('#document', { selectors: { 'form[data-long-running]': [element('FORM')] } });
  assert.deepEqual(init(bare, win), []);
});
