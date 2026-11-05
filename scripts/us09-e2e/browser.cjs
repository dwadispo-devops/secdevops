// Node 24 + Chrome DevTools Protocol. No browser library or real secret is required.
// Chrome must already be running headless with remote debugging on a loopback port.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const base = process.env.US09_FRONTEND_URL || 'http://127.0.0.1:24200';
const debuggerUrl = process.env.US09_DEBUGGER_URL || 'http://127.0.0.1:29222';
const out = path.resolve(__dirname, '../../pharmacy-service/target/devops87-evidence');
const results = [];
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

async function until(check, label) {
  for (let i = 0; i < 100; i++) {
    const value = await check();
    if (value) return value;
    await delay(200);
  }
  throw new Error('Timed out: ' + label);
}

function sql(query) {
  return execFileSync('docker', ['exec', 'medidispo-devops87-db', 'psql', '-X', '-U',
    'e2e_owner', '-d', 'pharmacy_e2e', '-At', '-v', 'ON_ERROR_STOP=1', '-c', query],
    { encoding: 'utf8' }).trim();
}

(async () => {
  fs.mkdirSync(out, { recursive: true });
  await until(async () => { try { return (await fetch(debuggerUrl + '/json/version')).ok; } catch { return false; } }, 'Chrome DevTools readiness');
  const target = await (await fetch(debuggerUrl + '/json/new?' + encodeURIComponent(base + '/demande-disponibilite'),
    { method: 'PUT' })).json();
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
  let sequence = 0;
  const pending = new Map();
  const responses = [];
  ws.onmessage = event => {
    const message = JSON.parse(event.data);
    if (message.id) {
      const promise = pending.get(message.id);
      if (!promise) return;
      clearTimeout(promise.timer);
      pending.delete(message.id);
      if (message.error) promise.reject(new Error(message.error.message));
      else promise.resolve(message.result);
    }
    if (message.method === 'Network.responseReceived') {
      const response = message.params.response;
      if (response.url === base + '/api/availability-requests') responses.push(response.status);
    }
  };
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout: ' + method)); }, 20000);
    pending.set(id, { resolve, reject, timer });
    ws.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.exceptionDetails) throw new Error('Browser JavaScript failed');
    return result.result.value;
  };
  const record = (name, detail) => { results.push({ name, result: 'PASS', ...detail }); console.log('PASS ' + name); };
  await send('Network.enable');
  await send('Runtime.enable');
  await send('Browser.grantPermissions', { origin: base, permissions: ['geolocation'] });
  await send('Emulation.setGeolocationOverride', { latitude: 31.63, longitude: -8, accuracy: 1 });
  await until(() => evaluate('!!document.querySelector("app-availability-request form")'), 'Angular route');
  record('Angular form rendered');

  const account = { email: 'devops87-' + crypto.randomUUID() + '@example.test',
    password: 'Qa87-' + crypto.randomBytes(24).toString('hex'), firstName: 'Patient', lastName: 'Fictif' };
  // No credentials or token are included in console output, CDP traces or the JSON report.
  fs.writeFileSync(path.join(out, 'patient.env'), 'EMAIL=' + account.email + '\nPASSWORD=' + account.password + '\n');
  const registration = await evaluate(`(async()=>{
    const r=await fetch('/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(${JSON.stringify(account)})});
    const b=await r.json(); return {status:r.status,id:b.id,role:b.role};})()`);
  assert.equal(registration.status, 201);
  assert.equal(registration.role, 'PATIENT');
  assert.match(registration.id, /^[0-9a-f-]{36}$/i);
  record('Real patient registration', registration);
  const login = await evaluate(`(async()=>{
    const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(${JSON.stringify({ email: account.email, password: account.password })})});
    const b=await r.json(); if(r.status===200){sessionStorage.setItem('access_token',b.accessToken);window.__us09Token=b.accessToken;}
    return {status:r.status,tokenType:b.tokenType,tokenReceived:!!b.accessToken};})()`);
  assert.equal(login.status, 200);
  assert.equal(login.tokenReceived, true);
  record('Real patient login and access token', login);

  const click = text => evaluate(`Array.from(document.querySelectorAll('app-availability-request button')).find(b=>b.textContent.includes(${JSON.stringify(text)})).click()`);
  const input = (selector, value) => evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.value=${JSON.stringify(String(value))};e.dispatchEvent(new Event('input',{bubbles:true}));})()`);
  await click('Ajouter');
  await until(() => evaluate('document.querySelectorAll(".medication").length===2'), 'additional medicine row');
  await input('.medication:nth-of-type(1) input[placeholder]', 'DEVOPS87 Paracetamol');
  await input('.medication:nth-of-type(1) input[type="number"]', 2);
  await input('.medication:nth-of-type(2) input[placeholder]', 'DEVOPS87 Vitamine C');
  await input('.medication:nth-of-type(2) input[type="number"]', 3);
  await input('input[name="radius"]', 2000);
  await click('position GPS');
  await until(() => evaluate('document.querySelector("app-availability-request").textContent.includes("Position GPS récupérée")'), 'controlled GPS');
  record('Controlled browser GPS and multiple medicine inputs', { latitude: 31.63, longitude: -8, radiusM: 2000 });
  await click('Envoyer');
  await until(() => evaluate('document.querySelector("app-availability-request [role=status]")?.textContent.includes("succès")'), 'success confirmation');
  await until(() => responses.length, 'POST response');
  assert.equal(responses.at(-1), 201);
  record('Browser form POST through Angular proxy and confirmation', { status: 201 });
  const rows = JSON.parse(sql(`SELECT row_to_json(q) FROM (
    SELECT id,patient_id,status,latitude,longitude,radius_m FROM availability_requests
    WHERE patient_id='${registration.id}') q;`));
  assert.equal(rows.status, 'PENDING');
  assert.equal(rows.radius_m, 2000);
  assert.equal(rows.latitude, 31.63);
  assert.equal(rows.longitude, -8);
  const medicines = JSON.parse(sql(`SELECT json_agg(q ORDER BY q.medication_name) FROM
    (SELECT medication_name,quantity FROM availability_request_items WHERE request_id='${rows.id}') q;`));
  assert.deepEqual(medicines, [{ medication_name: 'DEVOPS87 Paracetamol', quantity: 2 },
    { medication_name: 'DEVOPS87 Vitamine C', quantity: 3 }]);
  const associations = JSON.parse(sql(`SELECT json_agg(q) FROM
    (SELECT p.name,p.status AS pharmacy_status,a.status FROM availability_request_pharmacies a
    JOIN pharmacies p ON p.id=a.pharmacy_id WHERE a.request_id='${rows.id}') q;`));
  assert.deepEqual(associations, [{ name: 'DEVOPS87 APPROVED proche', pharmacy_status: 'APPROVED', status: 'PENDING' }]);
  record('SQL patient, PENDING, medicines and APPROVED-only radius association', { request: rows, medicines, associations });

  const counts = () => sql('SELECT (SELECT count(*) FROM availability_requests), (SELECT count(*) FROM availability_request_items), (SELECT count(*) FROM availability_request_pharmacies);');
  const before = counts();
  const badInput = await evaluate(`(async()=>{const r=await fetch('/api/availability-requests',{
    method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+sessionStorage.getItem('access_token')},
    body:JSON.stringify({latitude:31.63,longitude:-8,radiusM:99,medications:[{name:'DEVOPS87 Invalid',quantity:1}]})});return r.status;})()`);
  assert.equal(badInput, 400);
  assert.equal(counts(), before);
  record('Browser API invalid input without writes', { status: 400 });

  await evaluate('sessionStorage.setItem("access_token","invalid.jwt.token")');
  let responseCount = responses.length;
  await click('Envoyer');
  await until(() => evaluate('document.querySelector("app-availability-request [role=status]")?.textContent.includes("expirée")'), '401 Angular message');
  await until(() => responses.length > responseCount, '401 response');
  assert.equal(responses.at(-1), 401);
  assert.equal(counts(), before);
  record('Browser form invalid JWT and Angular session error without writes', { status: 401 });

  await evaluate('sessionStorage.setItem("access_token",window.__us09Token)');
  await input('.medication:nth-of-type(1) input[placeholder]', 'DEVOPS87_FORCE_500');
  responseCount = responses.length;
  await click('Envoyer');
  await until(() => evaluate('document.querySelector("app-availability-request [role=status]")?.textContent.includes("Échec")'), '500 Angular message');
  await until(() => responses.length > responseCount, '500 response');
  assert.equal(responses.at(-1), 500);
  assert.equal(counts(), before);
  record('Controlled real backend failure, Angular error and transaction rollback', { status: 500 });
  await evaluate('sessionStorage.clear();delete window.__us09Token');
  await send('Page.close');
  ws.close();
  fs.writeFileSync(path.join(out, 'e2e-results.json'), JSON.stringify({ browser: 'Chrome headless / CDP', results,
    summary: { passed: results.length, failed: 0, skipped: 0 } }, null, 2));
})().catch(error => {
  fs.mkdirSync(out, { recursive: true });
  fs.writeFileSync(path.join(out, 'e2e-results.json'), JSON.stringify({ results,
    summary: { passed: results.length, failed: 1, skipped: 0 }, error: error.message }, null, 2));
  console.error('FAIL ' + error.message);
  process.exit(1);
});
