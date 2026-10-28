const test = require('node:test');
const assert = require('node:assert/strict');
const manifest = require('../package.json');
const lock = require('../package-lock.json');

function atLeast(actual, minimum) {
  assert.match(actual, /^\d+\.\d+\.\d+$/, 'Use a stable patched release');
  const current = actual.split('.').map(Number);
  const required = minimum.split('.').map(Number);
  for (let index = 0; index < current.length; index++) {
    if (current[index] !== required[index]) return current[index] > required[index];
  }
  return true;
}

test('happy-dom includes the GHSA-834v-8h66-3h79 fix', () => {
  const version = lock.packages['node_modules/happy-dom']?.version;
  assert.ok(version && atLeast(version, '21.0.4'), `Unpatched happy-dom: ${version}`);
});

test('KaTeX includes the GHSA-238p-pmpm-9mq7 fix', () => {
  const version = lock.packages['node_modules/katex']?.version;
  assert.ok(version && atLeast(version, '0.18.2'), `Unpatched KaTeX: ${version}`);
});

test('unused vulnerable PDF.js is absent, or a fixed release is locked', () => {
  const version = lock.packages['node_modules/pdfjs-dist']?.version;
  if (version) {
    assert.ok(atLeast(version, '6.2.108'), `Unpatched PDF.js: ${version}`);
  } else {
    assert.equal(manifest.dependencies['pdfjs-dist'], undefined);
    assert.equal(manifest.devDependencies['pdfjs-dist'], undefined);
  }
});

test('the lockfile root matches declared runtime and development dependencies', () => {
  assert.deepEqual(lock.packages[''].dependencies, manifest.dependencies);
  assert.deepEqual(lock.packages[''].devDependencies, manifest.devDependencies);
});
