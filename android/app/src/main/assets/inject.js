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

  /* ============ 4. 原生 HTTP 通道：拦截跨域请求，绕过 CORS ============ */
  try {
    installHttpBridge();
  } catch (e) {}

  function installHttpBridge() {
    if (!(window.QQBotNative && typeof QQBotNative.httpRequest === "function")) return;

    var seq = 0;
    var pending = {};

    function b64ToBytes(b64) {
      var bin = atob(b64), n = bin.length, out = new Uint8Array(n);
      for (var i = 0; i < n; i++) out[i] = bin.charCodeAt(i);
      return out;
    }
    function bytesToB64(bytes) {
      var CH = 0x8000, s = "";
      for (var i = 0; i < bytes.length; i += CH) {
        s += String.fromCharCode.apply(null, bytes.subarray(i, i + CH));
      }
      return btoa(s);
    }
    function utf8(str) { return new TextEncoder().encode(str); }

    // Android 原生请求完成后的回调入口
    window.__QQBOT_HTTP_DONE__ = function (id, resultJson) {
      var p = pending[id];
      if (!p) return;
      delete pending[id];
      var r = null;
      try {
        // 兼容两种传参：JSON 字符串，或直接传对象
        if (typeof resultJson === "string") r = JSON.parse(resultJson);
        else if (resultJson && typeof resultJson === "object") r = resultJson;
      } catch (e) { r = null; }
      if (!r || typeof r !== "object") { p.reject(new TypeError("bad bridge response")); return; }
      if (!r.ok) { p.reject(new TypeError(r.error || "network error")); return; }
      try {
        var status = r.status || 200;
        var nullBody = (status === 204 || status === 205 || status === 304);
        var body = nullBody ? null : b64ToBytes(r.body || "");
        var headers = new Headers();
        var h = r.headers || {};
        for (var k in h) {
          if (Object.prototype.hasOwnProperty.call(h, k)) {
            try { headers.append(k, h[k]); } catch (e) {}
          }
        }
        p.resolve(new Response(body, {
          status: status,
          statusText: r.statusText || "",
          headers: headers
        }));
      } catch (e) { p.reject(e); }
    };

    // 把已知 CORS 代理地址还原成真实目标地址，直连不再依赖代理
    function unwrapProxy(u) {
      try {
        var m;
        if ((m = /^https?:\/\/proxy\.cors\.sh\/(.+)$/i.exec(u))) return m[1];
        if ((m = /^https?:\/\/api\.allorigins\.win\/raw\?url=(.+)$/i.exec(u))) return decodeURIComponent(m[1]);
        if ((m = /^https?:\/\/corsproxy\.io\/\?(.+)$/i.exec(u))) return decodeURIComponent(m[1]);
        if ((m = /^https?:\/\/api\.allorigins\.win\/get\?url=(.+)$/i.exec(u))) return decodeURIComponent(m[1]);
      } catch (e) {}
      return u;
    }

    function normHeaders(h) {
      var out = {};
      if (!h) return out;
      try {
        if (typeof Headers !== "undefined" && h instanceof Headers) {
          h.forEach(function (v, k) { out[k] = v; });
        } else if (Object.prototype.toString.call(h) === "[object Array]") {
          h.forEach(function (pair) { if (pair && pair.length >= 2) out[pair[0]] = pair[1]; });
        } else {
          for (var k in h) {
            if (Object.prototype.hasOwnProperty.call(h, k) && h[k] != null) out[k] = String(h[k]);
          }
        }
      } catch (e) {}
      return out;
    }

    function sendNative(url, method, headers, body) {
      return new Promise(function (resolve, reject) {
        var id = ++seq;
        pending[id] = { resolve: resolve, reject: reject };
        var payload = { url: url, method: method, headers: headers, bodyBase64: null };
        function go(b64) {
          payload.bodyBase64 = b64;
          try { QQBotNative.httpRequest(String(id), JSON.stringify(payload)); }
          catch (e) { delete pending[id]; reject(new TypeError("native bridge unavailable")); }
        }
        try {
          if (body === undefined || body === null) return go(null);
          if (typeof body === "string") return go(bytesToB64(utf8(body)));
          if (typeof URLSearchParams !== "undefined" && body instanceof URLSearchParams) return go(bytesToB64(utf8(body.toString())));
          if (typeof ArrayBuffer !== "undefined" && body instanceof ArrayBuffer) return go(bytesToB64(new Uint8Array(body)));
          if (ArrayBuffer.isView(body)) return go(bytesToB64(new Uint8Array(body.buffer, body.byteOffset, body.byteLength)));
          if (typeof Blob !== "undefined" && body instanceof Blob) {
            var fr = new FileReader();
            fr.onload = function () { go(bytesToB64(new Uint8Array(fr.result))); };
            fr.onerror = function () { delete pending[id]; reject(new TypeError("body read failed")); };
            fr.readAsArrayBuffer(body);
            return;
          }
          return go(bytesToB64(utf8(String(body))));
        } catch (e) { delete pending[id]; reject(e); }
      });
    }

    var origin = (location && location.origin) || "";
    var origFetch = window.fetch ? window.fetch.bind(window) : null;

    window.fetch = function (input, init) {
      try {
        var url = null;
        if (typeof input === "string") url = input;
        else if (typeof URL !== "undefined" && input instanceof URL) url = input.href;
        if (url === null) return origFetch ? origFetch(input, init) : Promise.reject(new TypeError("unsupported input"));

        var method = "GET", headers = {}, body;
        if (init) {
          if (init.method) method = String(init.method).toUpperCase();
          if (init.headers) {
            var ih = normHeaders(init.headers);
            for (var k in ih) headers[k] = ih[k];
          }
          if ("body" in init) body = init.body;
        }

        var abs = new URL(url, location.href);
        if (abs.protocol !== "http:" && abs.protocol !== "https:") {
          return origFetch ? origFetch(input, init) : Promise.reject(new TypeError("unsupported scheme"));
        }
        // 同源请求交给原生 fetch；所有跨域请求走 App 本地通道
        if (abs.origin === origin) {
          return origFetch ? origFetch(input, init) : Promise.reject(new TypeError("no fetch"));
        }
        return sendNative(unwrapProxy(abs.href), method, headers, body);
      } catch (e) {}
      return origFetch ? origFetch(input, init) : Promise.reject(new TypeError("no fetch"));
    };
  }
})();
