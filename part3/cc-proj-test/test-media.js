'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');

const target = (process.env.TARGET || '').replace(/\/$/, '');

function hash(bytes) {
  let value = crypto.createHash('sha1').update(bytes).digest('hex').toUpperCase();
  while (value.length > 16 && value.startsWith('0')) value = value.slice(1);
  return value;
}

async function upload(bytes) {
  return fetch(`${target}/media`, { method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: bytes });
}

async function check(bytes, expectedType) {
  const first = await upload(bytes);
  assert.equal(first.status, 200);
  const id = await first.text();
  assert.equal(id, hash(bytes));
  const second = await upload(bytes);
  assert.equal(second.status, 200);
  assert.equal(await second.text(), id);
  const downloaded = await fetch(`${target}/media/${encodeURIComponent(id)}`);
  assert.equal(downloaded.status, 200);
  assert.equal(downloaded.headers.get('content-type').split(';')[0], expectedType);
  const result = Buffer.from(await downloaded.arrayBuffer());
  assert.deepEqual(result, bytes);
  assert.equal(hash(result), id);
  console.log(`OK: ${bytes.length} bytes, ${expectedType}`);
}

async function main() {
  if (!target) throw new Error('Set TARGET to the Function App URL including /rest');
  const images = fs.readdirSync('images').filter(name => name.toLowerCase().endsWith('.jpeg'));
  if (!images.length) throw new Error('No JPEG images found in images/');
  const image = fs.readFileSync(path.join('images', images[0]));
  await check(image, 'image/jpeg');
  await check(Buffer.from([1, 2, 3, 4, 5]), 'application/octet-stream');
  const empty = await upload(Buffer.alloc(0));
  assert.equal(empty.status, 400);
  const missing = await fetch(`${target}/media/${crypto.randomUUID()}`);
  assert.equal(missing.status, 404);
  console.log('OK: empty upload and missing media');
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });