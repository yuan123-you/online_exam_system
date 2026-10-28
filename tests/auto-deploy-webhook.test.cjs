const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'auto-deploy-webhook.js'), 'utf8');
const secret = 'local-webhook-regression-secret';
const payload = JSON.stringify({ ref: 'refs/heads/main' });
const signature = `sha256=${crypto.createHmac('sha256', secret).update(payload).digest('hex')}`;

function verifier(configuredSecret = secret) {
  return vm.runInNewContext(`${source}\nverifySignature;`, {
    require(name) {
      if (name === 'http') return { createServer: () => ({ listen() {} }) };
      if (name === 'fs') return { appendFileSync() {} };
      if (name === 'child_process') return { exec() { assert.fail('Tests must never run a deployment'); } };
      if (name === 'crypto') return crypto;
      throw new Error(`Unexpected dependency: ${name}`);
    },
    process: { env: { WEBHOOK_SECRET: configuredSecret } },
    Buffer,
    console: { log() {} },
  });
}

test('accepts a correctly signed payload', () => {
  assert.equal(verifier()(payload, signature), true);
});

test('rejects altered payloads', () => {
  assert.equal(verifier()(`${payload} `, signature), false);
});

test('rejects unsigned requests', () => {
  assert.equal(verifier()(payload, undefined), false);
});

test('missing webhook secret fails closed', () => {
  assert.equal(verifier('')(payload, signature), false);
});

test('rejects a truncated signature without throwing', () => {
  assert.equal(verifier()(payload, 'sha256=bad'), false);
});

test('rejects unexpected array-valued signature headers', () => {
  assert.equal(verifier()(payload, [signature]), false);
});

test('rejects a non-ASCII signature even when its character count matches', () => {
  assert.equal(verifier()(payload, 'sha256=' + 'é'.repeat(64)), false);
});
