/* FitOS service worker: keeps the app on the phone so it opens instantly and works offline. */
const VERSION = "fitos-3.8.0";
const APP = ["./", "./index.html", "./manifest.webmanifest", "./icons/icon-192.png", "./icons/icon-512.png", "./icons/icon-maskable-512.png", "./icons/apple-touch-icon.png", "./icons/favicon-32.png"];
const FONTS = "fitos-fonts";

self.addEventListener("install", e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(APP)));
});
self.addEventListener("message", e => { if (e.data === "skip") self.skipWaiting(); });
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== VERSION && k !== FONTS).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", e => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  // fonts: use the saved copy, refresh it in the background
  if (url.hostname === "fonts.googleapis.com" || url.hostname === "fonts.gstatic.com") {
    e.respondWith(caches.open(FONTS).then(c => c.match(req).then(hit => {
      const net = fetch(req).then(r => { if (r && (r.ok || r.type === "opaque")) c.put(req, r.clone()); return r; }).catch(() => hit);
      return hit || net;
    })));
    return;
  }
  if (url.origin !== location.origin) return;            // food lookups etc. always go to the internet
  // the app itself: instant from the phone, works offline
  e.respondWith(caches.match(req, { ignoreSearch: true }).then(hit => hit || fetch(req).then(r => {
    if (r && r.ok) caches.open(VERSION).then(c => c.put(req, r.clone()));
    return r;
  }).catch(() => req.mode === "navigate" ? caches.match("./index.html") : undefined)));
});
