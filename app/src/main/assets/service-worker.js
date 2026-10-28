const CACHE="reel-series-app-v16";
const FILES=["./", "./index.html", "./style.css", "./firebase-config.js", "./manifest.json", "./icon-192.png", "./icon-512.png", "./app.js", "./Firebase_Completo.json", "./dados/Firebase_Completo.json"];
self.addEventListener("install",e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(FILES)).then(()=>self.skipWaiting())));
self.addEventListener("activate",e=>e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==CACHE).map(x=>caches.delete(x)))).then(()=>self.clients.claim())));
self.addEventListener("fetch",e=>{
 if(e.request.method!=="GET")return;
 const u=new URL(e.request.url);
 if(u.hostname.includes("firebaseio.com")||/\.(mp4|m3u8|ts|webm|mkv)(\?|$)/i.test(u.pathname)){e.respondWith(fetch(e.request).catch(()=>new Response(null,{status:503})));return;}
 if(e.request.mode==="navigate"){e.respondWith(fetch(e.request).then(r=>{const c=r.clone();caches.open(CACHE).then(x=>x.put("./index.html",c));return r}).catch(()=>caches.match("./index.html")));return;}
 e.respondWith(caches.match(e.request).then(c=>c||fetch(e.request).then(r=>{if(r&&r.ok){const x=r.clone();caches.open(CACHE).then(y=>y.put(e.request,x))}return r})));
});
