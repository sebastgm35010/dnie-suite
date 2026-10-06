import { appendFileSync, existsSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const routes = new Set(['/ciudadanodigital/autorizar', '/ciudadanodigital/parametros/url-validacion',
  '/ciudadanodigital/parametros/url-inyeccion', '/ciudadanodigital/parametros/url-desbloqueo',
  '/ciudadanodigital/index.html']);

// Do not persist paths containing identities, query strings, headers, payloads or raw SDK text.
export function endpoint(raw) {
  try {
    const url = new URL(raw);
    if (!['http:', 'https:'].includes(url.protocol)) return null;
    const local = ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname);
    if (!local && url.hostname !== 'reniec.gob.pe' && !url.hostname.endsWith('.reniec.gob.pe')) return null;
    const route = routes.has(url.pathname) ? url.pathname :
      url.pathname.endsWith('.application') ? 'clickonce_manifest' :
      url.pathname.endsWith('.js') ? 'javascript' :
      url.pathname.endsWith('.css') ? 'stylesheet' : 'other_path_redacted';
    // Even subdomain labels can be arbitrary. Retain only explicitly known public hosts.
    const host = local ? 'loopback' : ['pki.reniec.gob.pe', 'identidad.reniec.gob.pe',
      'idperu.reniec.gob.pe'].includes(url.hostname) ? url.hostname : 'reniec_other';
    return { host, route, local, port: local ? (url.port || (url.protocol === 'https:' ? '443' : '80')) : undefined };
  } catch { return null; }
}

export class Cdp {
  constructor(socket, onEvent = () => {}) {
    this.socket = socket; this.pending = new Map(); this.next = 0;
    socket.addEventListener('message', e => {
      let message; try { message = JSON.parse(e.data); } catch { return; }
      if (message.id) {
        const item = this.pending.get(message.id); if (!item) return;
        this.pending.delete(message.id); clearTimeout(item.timer);
        if (message.error) item.reject(new Error('CDP command rejected')); else item.resolve(message.result);
      } else onEvent(message.method, message.params || {});
    });
    socket.addEventListener('close', () => {
      for (const item of this.pending.values()) { clearTimeout(item.timer); item.reject(new Error('CDP closed')); }
      this.pending.clear();
    });
  }
  static async connect(raw, onEvent) {
    const url = new URL(raw);
    if (url.protocol !== 'ws:' || !['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)) throw new Error('Nonlocal CDP rejected');
    const socket = new WebSocket(url);
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => { socket.close(); reject(new Error('CDP connection timeout')); }, 5000);
      socket.addEventListener('open', () => { clearTimeout(timer); resolve(); }, { once: true });
      socket.addEventListener('error', () => { clearTimeout(timer); reject(new Error('CDP connection failed')); }, { once: true });
    });
    return new Cdp(socket, onEvent);
  }
  send(method, params = {}, timeoutMs = 5000) {
    const id = ++this.next;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error('CDP command timeout')); }, timeoutMs);
      this.pending.set(id, { resolve, reject, timer });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }
  close() { this.socket.close(); }
}

export function recorder(write) {
  const requests = new Map();
  return (method, p) => {
    if (method === 'Network.requestWillBeSent') {
      const address = endpoint(p.request?.url); if (!address) return;
      if (requests.size >= 4096) requests.delete(requests.keys().next().value);
      requests.set(p.requestId, { address, timestamp: p.timestamp });
      const verb = ['GET', 'POST', 'PUT', 'DELETE', 'HEAD', 'OPTIONS', 'PATCH'].includes(p.request.method) ? p.request.method : 'other';
      write('request', { ...address, method: verb, redirect: Boolean(p.redirectResponse) });
    } else if (method === 'Network.responseReceived') {
      const item = requests.get(p.requestId); if (!item) return;
      write('response', { ...item.address, status: Number(p.response.status),
        durationMs: Math.max(0, Math.round((p.timestamp - item.timestamp) * 1000)) });
    } else if (method === 'Network.loadingFailed') {
      const item = requests.get(p.requestId); if (!item) return;
      write('request_failed', { ...item.address, cancelled: Boolean(p.canceled), blocked: Boolean(p.blockedReason) });
      requests.delete(p.requestId);
    } else if (method === 'Network.loadingFinished') {
      const item = requests.get(p.requestId); if (!item) return;
      write('request_finished', { ...item.address, bytes: Number(p.encodedDataLength),
        durationMs: Math.max(0, Math.round((p.timestamp - item.timestamp) * 1000)) });
      requests.delete(p.requestId);
    } else if (method === 'Page.frameNavigated') {
      const address = endpoint(p.frame?.url); if (address) write('navigation', address);
    } else if (method === 'Network.webSocketCreated') {
      // Schemes changed only for endpoint classification; never log frames or handshake headers.
      const address = endpoint(String(p.url).replace(/^ws:/, 'http:').replace(/^wss:/, 'https:'));
      if (address) write('websocket_created', address);
    } else if (method === 'Runtime.consoleAPICalled') {
      write('console_event', { severity: ['error', 'warning', 'assert'].includes(p.type) ? p.type : 'other' });
    } else if (method === 'Runtime.exceptionThrown') {
      write('javascript_exception', { line: Number(p.exceptionDetails?.lineNumber || 0) });
    } else if (method === 'Log.entryAdded') {
      write('browser_log', { severity: ['error', 'warning', 'info', 'verbose'].includes(p.entry?.level) ? p.entry.level : 'other' });
    }
  };
}

async function main() {
  const args = process.argv.slice(2);
  const option = (name, fallback) => { const i = args.indexOf(name); return i < 0 ? fallback : args[i + 1]; };
  const port = Number(option('--port', '19371'));
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid port');
  const base = `http://127.0.0.1:${port}`;
  const get = async path => { const r = await fetch(base + path, { signal: AbortSignal.timeout(3000) }); if (!r.ok) throw new Error('CDP endpoint failed'); return r.json(); };
  const version = await get('/json/version');
  if (args.includes('--probe')) {
    const browser = await Cdp.connect(version.webSocketDebuggerUrl);
    const tabs = await get('/json/list'); const tab = tabs.find(t => t.type === 'page' && t.url === 'about:blank');
    if (!tab) throw new Error('Probe blank page absent');
    const rows = [];
    const page = await Cdp.connect(tab.webSocketDebuggerUrl, recorder((event, fields) => rows.push({ event, fields })));
    await page.send('Page.enable'); await page.send('Network.enable'); await page.send('Runtime.enable');
    // Control is tested only on a blank page, not a signed-in site or personal data.
    await page.send('Page.navigate', { url: 'about:blank' });
    const state = await page.send('Runtime.evaluate', { expression: 'console.error("synthetic diagnostic probe"); document.readyState', returnByValue: true });
    const captured = rows.some(row => row.event === 'console_event' && row.fields.severity === 'error');
    if (!captured) throw new Error('CDP event capture probe failed');
    console.log(JSON.stringify({ probe: 'ok', browser: String(version.Browser).replace(/[^A-Za-z0-9./-]/g, ''),
      dom: ['loading', 'interactive', 'complete'].includes(state.result?.value), captureEvents: captured, domains: ['Page', 'Network', 'Runtime'] }));
    page.close(); await browser.send('Browser.close').catch(() => {}); browser.close(); return;
  }
  const output = option('--output'); if (!output) throw new Error('Output required');
  const seconds = Number(option('--seconds', '1800'));
  if (!Number.isInteger(seconds) || seconds < 1 || seconds > 3600) throw new Error('Invalid duration');
  const stop = option('--stop');
  const write = (event, fields) => appendFileSync(output, JSON.stringify({ time: Date.now(), category: 'edge', event, fields }) + '\n');
  const clients = new Map(); const starting = Date.now();
  write('capture_started', { limitSeconds: seconds });
  try {
    while (Date.now() - starting < seconds * 1000 && !(stop && existsSync(stop))) {
      const tabs = await get('/json/list').catch(() => []);
      const live = new Set(tabs.map(t => t.id));
      for (const [id, client] of clients) if (!live.has(id)) { client.close(); clients.delete(id); }
      for (const tab of tabs) {
        if (tab.type !== 'page' || clients.has(tab.id)) continue;
        // Attach blank launch tab before navigation; avoid unrelated or signed-in browsing tabs.
        if (tab.url !== 'about:blank' && !endpoint(tab.url)) continue;
        const client = await Cdp.connect(tab.webSocketDebuggerUrl, recorder(write)).catch(() => null);
        if (!client) continue;
        try {
          await client.send('Page.enable'); await client.send('Network.enable');
          await client.send('Runtime.enable'); await client.send('Log.enable');
          clients.set(tab.id, client); write('tab_attached', {});
        } catch { client.close(); write('attach_failed', {}); }
      }
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
  } finally {
    for (const client of clients.values()) client.close();
    write('capture_stopped', {});
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(() => { console.error('Edge capture failed; no raw error or request data printed.'); process.exitCode = 1; });
}
