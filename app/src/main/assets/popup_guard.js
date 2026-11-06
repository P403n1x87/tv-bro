// Popup guard: document-start interception of window.open.
//
// Registered through WebViewCompat.addDocumentStartJavaScript() for "*" (all
// frames) so it runs before any page script. It complements the engine-level
// popup policy (WebEngineCallback.shouldBlockPopupRequest / shouldBlockNewWindow):
//   * a window.open without a recent user gesture is short-circuited here,
//     so no WebView transport / new-tab request is ever created for it;
//   * a click that lands on a detected click trap (transparent max-z layer,
//     off-screen anchor, shadow ad box) is marked "suspicious" and any
//     window.open it triggers is blocked too.
// Blocking here means window.open returns null and no popup tab is created.
(function () {
  "use strict";
  try {
    if (window.__tvbroPopupGuard) return;
    window.__tvbroPopupGuard = true;

    var GESTURE_WINDOW_MS = 1500;
    var lastGestureTime = 0;
    var suspicious = false; // set when the last gesture hit a click trap

    var HIGH_Z = 1000000;
    var AD_HREF = /\/(ad|ads|adv|advert)\/|[\?&]ad=|visit\.php|popunder|clickunder|onclickads|adserv|adserver|doubleclick|googlesyndication|adnxs|adsterra|propeller/i;
    var AD_TEXT = /close\s*ad|skip\s*ad|learn\s*more|sponsored|advertisement/i;

    function isTransparent(cs) {
      var bg = cs && cs.backgroundColor;
      if (!bg || bg === "transparent") return true;
      return /^rgba\(\s*0\s*,\s*0\s*,\s*0\s*,\s*0\s*\)$/.test(bg);
    }

    function isClickTrap(el) {
      var tag = el.tagName;
      if (tag !== "DIV" && tag !== "INS" && tag !== "IFRAME" && tag !== "SECTION" && tag !== "A") return false;
      var cs = window.getComputedStyle(el);
      if (!cs || cs.display === "none" || cs.visibility === "hidden") return false;
      if (cs.position !== "absolute" && cs.position !== "fixed") return false;
      var z = parseInt(cs.zIndex, 10);
      if (!(z >= HIGH_Z)) return false;
      if (!isTransparent(cs)) return false;
      if (el.childElementCount > 0 || (el.textContent || "").trim()) return false;
      var r = el.getBoundingClientRect();
      return r.width >= 40 && r.height >= 40;
    }

    function isSuspiciousTarget(target) {
      if (!target || !(target instanceof Element)) return false;
      // Walk up from the clicked element: the trap may wrap the visible bait.
      var el = target;
      while (el && el !== document.documentElement) {
        if (isClickTrap(el)) return true;
        el = el.parentElement;
      }
      return false;
    }

    function recordGesture(target) {
      lastGestureTime = Date.now();
      suspicious = isSuspiciousTarget(target);
    }

    function gestureFresh() {
      return Date.now() - lastGestureTime <= GESTURE_WINDOW_MS && !suspicious;
    }

    function isPopunderAnchor(el) {
      if (el.tagName !== "A") return false;
      var cs = window.getComputedStyle(el);
      if (!cs || (cs.display !== "none" && cs.visibility !== "hidden")) return false;
      var href = el.getAttribute("href") || "";
      if (href && AD_HREF.test(href)) return true;
      if (href && href !== "#") return false;
      if (el.childElementCount > 0 || (el.textContent || "").trim()) return false;
      var r = el.getBoundingClientRect();
      return r.left <= -200 || r.top <= -200 || r.right <= -200 || r.bottom <= -200;
    }

    function findShadowAdBox(root) {
      var els = root.querySelectorAll("*");
      for (var i = 0; i < els.length; i++) {
        var el = els[i];
        var cs = window.getComputedStyle(el);
        if (!cs || (cs.position !== "fixed" && cs.position !== "absolute")) continue;
        var z = parseInt(cs.zIndex, 10);
        if (!(z >= HIGH_Z)) continue;
        var txt = (el.textContent || "").replace(/\s+/g, " ").trim();
        if (AD_TEXT.test(txt)) return el;
        if (el.querySelector && el.querySelector("video, iframe")) return el;
      }
      return null;
    }

    function remove(el) {
      try { el.remove(); } catch (e) {}
    }

    // Continuous cleanup of the patterns the zapper removes, so a popup-triggering
    // trap does not survive long enough to be clicked.
    function sweep() {
      try {
        var candidates = document.querySelectorAll("div,ins,iframe,section,a");
        for (var i = 0; i < candidates.length; i++) {
          var el = candidates[i];
          try {
            if (isClickTrap(el) || isPopunderAnchor(el)) { remove(el); continue; }
          } catch (e) {}
        }
        var all = document.getElementsByTagName("*");
        for (var j = 0; j < all.length; j++) {
          var host = all[j];
          var sr = host.shadowRoot;
          if (sr && findShadowAdBox(sr)) remove(host);
        }
      } catch (e) {}
    }

    try {
      new MutationObserver(sweep).observe(document, { childList: true, subtree: true });
    } catch (e) {}
    setInterval(sweep, 2000);

    var originalOpen = window.open;
    window.open = function (url, target, features) {
      try {
        if (!gestureFresh()) {
          // No genuine, recent, non-trap user gesture: refuse to open anything.
          return null;
        }
      } catch (e) {}
      suspicious = false;
      lastGestureTime = 0;
      try {
        return originalOpen.apply(window, arguments);
      } catch (e) {
        return null;
      }
    };

    document.addEventListener("mousedown", function (ev) { recordGesture(ev.target); }, true);
    document.addEventListener("keydown", function (ev) { recordGesture(ev.target); }, true);

    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", sweep, { once: true });
    }
    sweep();
  } catch (e) {}
})();
