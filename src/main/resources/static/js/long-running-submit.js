/*
 * The waiting state for a form whose request takes a while: a free assessment, or preparing a detailed report.
 * The request itself is the ordinary synchronous form post; this script never sends, cancels or inspects it. Once
 * the browser accepts a valid submission it lays the loading overlay over the page, makes the page underneath
 * inert, blocks a second submission and rotates reassurance messages. The messages are static text from the
 * template, not progress: the browser cannot know what the server is doing.
 *
 * Markup (fragments/loading.html):
 *   form[data-long-running]        a form whose submission shows the overlay
 *   [data-loading-panel]           the overlay, hidden until a submission starts; moved to the end of <body> so it
 *                                  covers the viewport and everything else on the page can be made inert
 *   [data-loading-message]         the line the messages rotate through
 *   [data-loading-messages] > li   the messages, in order; the last one stays
 *
 * Served from this application so the content security policy can stay at script-src 'self'.
 */
(function () {
  'use strict';

  var ROTATE_MS = 4000;
  var ACTIVE_CLASS = 'loading-active';

  function each(list, fn) {
    for (var i = 0; i < list.length; i += 1) fn(list[i], i);
  }

  /**
   * One form's submission guard and loading state, with its timers passed in so it can be tested without a browser.
   * deps: doc (body, documentElement), panel, setTimer(fn, ms), clearTimer(id), rotateMs.
   */
  function createLongRunningSubmit(form, deps) {
    var submitting = false;
    var timers = [];
    var restore = [];

    function messages() {
      var list = [];
      each(deps.panel.querySelectorAll('[data-loading-messages] li'), function (item) {
        var text = (item.textContent || '').trim();
        if (text) list.push(text);
      });
      return list;
    }

    function buttons() {
      return form.querySelectorAll('button[type="submit"], button:not([type]), input[type="submit"]');
    }

    function showMessage(text) {
      var line = deps.panel.querySelector('[data-loading-message]');
      if (line && text) line.textContent = text;
    }

    function rotate(list, index) {
      showMessage(list[index]);
      if (index + 1 < list.length) {
        timers.push(deps.setTimer(function () { rotate(list, index + 1); }, deps.rotateMs));
      }
    }

    /** Everything on the page except the overlay: it stays in view, blurred, but can no longer be used or read. */
    function background() {
      var list = [];
      var body = deps.doc.body;
      if (!body) return list;
      each(body.children, function (child) {
        if (child !== deps.panel && !(child.contains && child.contains(deps.panel))) list.push(child);
      });
      return list;
    }

    function activate() {
      // Not focusable, clickable or announced. The form being sent is unaffected: inert controls stay in its data.
      each(background(), function (element) {
        restore.push({ element: element, inert: element.getAttribute('inert') });
        element.setAttribute('inert', '');
      });
      var root = deps.doc.documentElement;
      if (root && root.classList) root.classList.add(ACTIVE_CLASS);
      form.setAttribute('aria-busy', 'true');
      var label = deps.panel.getAttribute('data-loading-label');
      each(buttons(), function (button) {
        restore.push({ button: button, text: button.textContent, value: button.value,
          ariaDisabled: button.getAttribute('aria-disabled') });
        button.setAttribute('aria-disabled', 'true');
        if (label) {
          if (button.tagName === 'INPUT') button.value = label;
          else button.textContent = label;
        }
      });
      // Disabled only after this event: a control disabled now would be left out of the request being sent.
      timers.push(deps.setTimer(function () {
        each(buttons(), function (button) { button.disabled = true; });
      }, 0));
      deps.panel.hidden = false;
      var list = messages();
      if (list.length) rotate(list, 0);
      // Focus moves once, out of the now inert page; the rotating line is announced without moving it again.
      var heading = deps.panel.querySelector('[data-loading-heading]');
      if (heading && typeof heading.focus === 'function') heading.focus();
    }

    function onSubmit(event) {
      if (submitting) {
        // A second click, or Enter, while the first request is under way: never send it twice.
        event.preventDefault();
        return;
      }
      if (event.defaultPrevented) return;
      // A form the browser would refuse keeps its normal state; the browser shows its own messages.
      if (typeof form.checkValidity === 'function' && !form.checkValidity()) return;
      submitting = true;
      activate();
    }

    /** Undo everything: for a page restored from the back/forward cache after the request had started. */
    function reset() {
      each(timers, function (id) { deps.clearTimer(id); });
      timers = [];
      each(restore, function (entry) {
        if (entry.element) {
          if (entry.inert === null) entry.element.removeAttribute('inert');
          else entry.element.setAttribute('inert', entry.inert);
        }
        if (entry.button) {
          entry.button.disabled = false;
          if (entry.button.tagName === 'INPUT') entry.button.value = entry.value;
          else entry.button.textContent = entry.text;
          if (entry.ariaDisabled === null) entry.button.removeAttribute('aria-disabled');
          else entry.button.setAttribute('aria-disabled', entry.ariaDisabled);
        }
      });
      restore = [];
      var root = deps.doc.documentElement;
      if (root && root.classList) root.classList.remove(ACTIVE_CLASS);
      form.removeAttribute('aria-busy');
      deps.panel.hidden = true;
      var list = messages();
      if (list.length) showMessage(list[0]);
      submitting = false;
    }

    return {
      onSubmit: onSubmit,
      reset: reset,
      isSubmitting: function () { return submitting; }
    };
  }

  function init(doc, win) {
    var panel = doc.querySelector('[data-loading-panel]');
    if (!panel) return [];
    // A direct child of <body>: fixed to the viewport whatever card it was written in, and outside what turns inert.
    if (doc.body && panel.parentNode !== doc.body) doc.body.appendChild(panel);
    var controllers = [];
    each(doc.querySelectorAll('form[data-long-running]'), function (form) {
      var controller = createLongRunningSubmit(form, {
        doc: doc,
        panel: panel,
        setTimer: function (fn, ms) { return win.setTimeout(fn, ms); },
        clearTimer: function (id) { win.clearTimeout(id); },
        rotateMs: ROTATE_MS
      });
      form.addEventListener('submit', controller.onSubmit);
      controllers.push(controller);
    });
    win.addEventListener('pageshow', function (event) {
      if (event.persisted) each(controllers, function (controller) { controller.reset(); });
    });
    return controllers;
  }

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { createLongRunningSubmit: createLongRunningSubmit, init: init, ROTATE_MS: ROTATE_MS };
  }
  else if (typeof document !== 'undefined') {
    init(document, window);
  }
})();
