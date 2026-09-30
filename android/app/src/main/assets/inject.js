(function () {
  "use strict";
  if (window.__QQBOT_SHELL__) {
    try { if (window.__QQBOT_REHOOK__) window.__QQBOT_REHOOK__(); } catch (e) {}
    return;
  }
  window.__QQBOT_SHELL__ = true;

  /* ============ 1. 深色/浅色跟随系统（实时同步媒体查询） ============ */
  function applyScheme(dark) {
    try {
      var sheets = document.styleSheets || [];
      for (var i = 0; i < sheets.length; i++) {
        var rules = null;
        try { rules = sheets[i].cssRules || sheets[i].rules; } catch (e) { continue; }
        if (rules) walk(rules, dark);
      }
    } catch (e) {}
  }
  function walk(rules, dark) {
    for (var j = 0; j < rules.length; j++) {
      var r = rules[j];
      if (!r) continue;
      var t = r.type;
      if (t === 4) { /* CSSMediaRule */
        var cond = (r.conditionText || (r.media && r.media.mediaText) || "");
        if (cond.indexOf("prefers-color-scheme") >= 0) {
          var want = cond.indexOf("dark") >= 0 ? dark
                   : (cond.indexOf("light") >= 0 ? !dark : null);
          if (want !== null) {
            try { r.media.mediaText = want ? "all" : "not all"; } catch (e) {}
          }
        }
        try { if (r.cssRules) walk(r.cssRules, dark); } catch (e) {}
      } else if (t === 12) { /* CSSSupportsRule */
        try { if (r.cssRules) walk(r.cssRules, dark); } catch (e) {}
      }
    }
  }
  window.__QQBOT_APPLY_SCHEME__ = applyScheme;
  try {
    var dark0 = !!(window.QQBotNative && QQBotNative.isDark && QQBotNative.isDark());
    applyScheme(dark0);
  } catch (e) {}

  /* ============ 2. 运行状态回传（联动前台通知） ============ */
  function txt(id) {
    var el = document.getElementById(id);
    return el ? String(el.textContent || "").replace(/\s+/g, " ").trim() : "";
  }
  function snapshot() {
    var card = document.getElementById("statCard");
    var dot = document.getElementById("dot");
    var st = "off";
    if (card) {
      if (card.classList.contains("on")) st = "on";
      else if (card.classList.contains("connecting")) st = "connecting";
      else st = "off";
    } else if (dot && dot.classList.contains("on")) {
      st = "on";
    }
    return { state: st, text: txt("statusText"), bot: txt("botName"), count: txt("msgCount") };
  }
  var lastKey = "";
  function push() {
    try {
      var s = snapshot();
      var key = s.state + "|" + s.text + "|" + s.bot + "|" + s.count;
      if (key === lastKey) return;
      lastKey = key;
      if (window.QQBotNative && QQBotNative.onStatus) {
        QQBotNative.onStatus(JSON.stringify(s));
      }
    } catch (e) {}
  }
  var observer = null;
  function hook() {
    push();
    try {
      if (observer) observer.disconnect();
      if (window.MutationObserver) {
        observer = new MutationObserver(function () { push(); });
        ["statusText", "statCard", "dot", "botName", "msgCount"].forEach(function (id) {
          var el = document.getElementById(id);
          if (el) observer.observe(el, { attributes: true, childList: true, characterData: true, subtree: true });
        });
      }
    } catch (e) {}
  }
  window.__QQBOT_REHOOK__ = hook;
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", hook, { once: true });
  } else {
    hook();
  }
  try { setInterval(push, 2500); } catch (e) {}

  /* ============ 3. 通知 API 桥接（页面新回复通知转为原生通知） ============ */
  try {
    if (!("Notification" in window)) {
      function NativeNotification(title, opts) {
        opts = opts || {};
        try {
          if (window.QQBotNative && QQBotNative.notify) {
            QQBotNative.notify(String(title || "QQBot AI"), String(opts.body || ""));
          }
        } catch (e) {}
      }
      NativeNotification.permission = "granted";
      NativeNotification.requestPermission = function (cb) {
        try { if (typeof cb === "function") cb("granted"); } catch (e) {}
        return Promise.resolve("granted");
      };
      NativeNotification.maxActions = 0;
      window.Notification = NativeNotification;
    }
  } catch (e) {}
})();
