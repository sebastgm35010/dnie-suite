import test from 'node:test';
import assert from 'node:assert/strict';
import { endpoint, recorder } from './edge-capture.mjs';

test('drops identity paths, tokens, userinfo and unrelated hosts', () => {
  assert.deepEqual(endpoint('https://pki.reniec.gob.pe/ciudadanodigital/parametros/url-validacion?token=SECRET'),
    { host: 'pki.reniec.gob.pe', route: '/ciudadanodigital/parametros/url-validacion', local: false, port: undefined });
  const serialized = JSON.stringify(endpoint('https://user:SECRET@pki.reniec.gob.pe/person/DNI123?pin=SECRET#SECRET'));
  assert(!serialized.includes('SECRET')); assert(!serialized.includes('DNI123'));
  assert.equal(endpoint('https://reniec.gob.pe.evil.example/?token=SECRET'), null);
  assert.equal(endpoint('fido:/123456'), null);
  assert.equal(endpoint('https://private.example/'), null);
});

test('records timings/status without headers, bodies, console text or SSE payload', () => {
  const rows = []; const record = recorder((event, fields) => rows.push({ event, fields }));
  record('Network.requestWillBeSent', { requestId: 'x', timestamp: 1,
    request: { url: 'https://pki.reniec.gob.pe/private/SECRET?token=SECRET', method: 'POST', postData: 'SECRET', headers: { Cookie: 'SECRET' } } });
  record('Network.responseReceived', { requestId: 'x', timestamp: 1.2, response: { status: 401, headers: { Authorization: 'SECRET' } } });
  record('Runtime.consoleAPICalled', { type: 'error', args: [{ value: 'SECRET' }] });
  record('Runtime.exceptionThrown', { exceptionDetails: { text: 'SECRET', lineNumber: 5 } });
  record('Network.eventSourceMessageReceived', { data: 'SECRET' });
  record('Network.webSocketFrameReceived', { response: { payloadData: 'SECRET' } });
  record('Network.loadingFinished', { requestId: 'x', timestamp: 1.5, encodedDataLength: 7 });
  assert.equal(rows.length, 5); assert.equal(rows[1].fields.status, 401); assert.equal(rows[1].fields.durationMs, 200);
  assert(!JSON.stringify(rows).includes('SECRET'));
});
