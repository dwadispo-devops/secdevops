// Real Chrome + real Angular/Auth/Pharmacy/Prescription. No mocked API or OCR.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const accountsPath = path.join(root, '.us10/accounts.json');
const accounts = JSON.parse(fs.readFileSync(accountsPath, 'utf8'));
const base = process.env.US10_FRONTEND_URL || 'http://localhost:4201';
const debuggerUrl = 'http://127.0.0.1:29223';
const results = [];
const delay = ms => new Promise(r => setTimeout(r, ms));
async function until(check, label) {
  for (let i = 0; i < 150; i++) { const value = await check(); if (value) return value; await delay(200); }
  throw new Error('Timeout: ' + label);
}
(async () => {
  const target = await (await fetch(debuggerUrl + '/json/new?' + encodeURIComponent(base + '/pharmacien/connexion'), { method: 'PUT' })).json();
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  let sequence = 0;
  const pending = new Map();
  ws.onmessage = event => {
    const message = JSON.parse(event.data);
    if (!message.id) return;
    const p = pending.get(message.id); if (!p) return;
    clearTimeout(p.timer); pending.delete(message.id);
    if (message.error) p.reject(new Error(message.error.message)); else p.resolve(message.result);
  };
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout')); }, 20000);
    pending.set(id, { resolve, reject, timer }); ws.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.exceptionDetails) throw new Error('Browser script error (details suppressed to protect credentials)');
    return result.result.value;
  };
  const input = (selector, value) => evaluate(`(()=>{const el=document.querySelector(${JSON.stringify(selector)});el.value=${JSON.stringify(value)};el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));})()`);
  const record = name => { results.push({ name, result: 'PASS' }); console.log('PASS ' + name); };
  try {
    await send('Runtime.enable');
    await until(() => evaluate('!!document.querySelector("app-pharmacist-login form")'), 'login form');
    await input('#email', accounts.pharmacist.email); await input('#password', accounts.pharmacist.password);
    await evaluate('document.querySelector("form button[type=submit]").click()');
    await until(() => evaluate('!!document.querySelector("app-pharmacist-requests .request")'), 'authenticated inbox');
    record('Real login form and assigned inbox through Angular proxy');
    await evaluate('[...document.querySelectorAll(".request")].find(b=>b.textContent.includes("En attente")).click()');
    await until(() => evaluate(`document.querySelector('.reference')?.textContent.includes(${JSON.stringify(accounts.pendingRequestId)})`), 'selected detail');
    await until(() => evaluate('!!document.querySelector("app-pharmacist-requests img")?.naturalWidth'), 'decrypted original image');
    assert.equal(await evaluate('document.querySelector("app-pharmacist-requests img").naturalWidth'), 300);
    assert.equal(await evaluate('document.querySelector("app-pharmacist-requests").textContent.includes("Aucun texte OCR disponible")'), true);
    record('Original image displayed and missing OCR explicitly explained');
    await input('#price', '-1');
    assert.equal(await evaluate('document.querySelector("form button[type=submit]").disabled'), true);
    record('Negative price blocked by rendered form');
    await input('#price', '19.50'); await input('#comment', 'Synthetic browser test');
    await evaluate('document.querySelector("form button[type=submit]").click()');
    await until(() => evaluate('document.querySelector(".success")?.textContent.includes("enregistrée")'), 'saved confirmation');
    assert.equal(await evaluate('!!document.querySelector("app-pharmacist-requests form")'), false);
    record('Available response saved and repeated-response form removed');
    const login = await fetch('http://127.0.0.1:18081/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: accounts.patient.email, password: accounts.patient.password }) });
    assert.equal(login.status, 200);
    const { accessToken } = await login.json();
    const headers = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + accessToken };
    const response = await fetch(`http://127.0.0.1:18082/api/availability-requests/${accounts.pendingRequestId}/responses`, { headers });
    assert.equal(response.status, 200);
    const responses = await response.json();
    assert.equal(responses.find(r => r.pharmacyId === accounts.pharmacyId).price, 19.5);
    record('Patient reads the exact price submitted in Chrome');
    const create = await fetch('http://127.0.0.1:18082/api/availability-requests', { method: 'POST', headers,
      body: JSON.stringify({ latitude: 31.63, longitude: -8, radiusM: 1000, medications: [{ name: 'Synthetic professor demonstration', quantity: 1 }] }) });
    assert.equal(create.status, 201); const created = await create.json();
    const link = await fetch(`http://127.0.0.1:18082/api/availability-requests/${created.id}/prescription`, { method: 'PUT', headers,
      body: JSON.stringify({ prescriptionId: accounts.prescriptionId }) });
    assert.equal(link.status, 204); accounts.pendingRequestId = created.id;
    fs.writeFileSync(accountsPath, JSON.stringify(accounts, null, 2));
    await send('Page.captureScreenshot').then(r => fs.writeFileSync(path.join(root, '.us10/browser-success.png'), Buffer.from(r.data, 'base64')));
    record('Fresh pending request preserved for the professor demonstration');
    fs.writeFileSync(path.join(root, '.us10/browser-results.json'), JSON.stringify(results, null, 2));
  } finally { ws.close(); await fetch(debuggerUrl + '/json/close/' + target.id).catch(() => {}); }
})().catch(() => { console.error('US10 browser test failed; inspect local services and test assertions. Credentials were not logged.'); process.exitCode = 1; });
