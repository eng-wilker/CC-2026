'use strict';

const fs = require('node:fs');
const { performance } = require('node:perf_hooks');

const target = (process.env.TARGET || '').replace(/\/$/, '');
const usersFile = process.env.USERS_FILE || 'users.data';
const rounds = Number(process.env.ROUNDS || 10);
const concurrency = Number(process.env.CONCURRENCY || 10);

async function request(path, options = {}) {
  const start = performance.now();
  const response = await fetch(target + path, options);
  const text = await response.text();
  let body = text;
  try { body = JSON.parse(text); } catch {}
  return { status: response.status, body, headers: response.headers, ms: performance.now() - start };
}

async function login(user) {
  const result = await request('/user/auth', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ user: user.id, pwd: user.pwd })
  });
  const cookie = result.headers.get('set-cookie');
  if (result.status !== 200 || !cookie) throw new Error(`Login failed: HTTP ${result.status}`);
  return { id: user.id, cookie: cookie.split(';')[0] };
}

function percentile(values, percentileValue) {
  const sorted = [...values].sort((first, second) => first - second);
  return sorted.length ? sorted[Math.ceil(sorted.length * percentileValue) - 1] : null;
}

async function main() {
  if (!target || !Number.isInteger(rounds) || rounds < 1 || !Number.isInteger(concurrency)
      || concurrency < 2 || concurrency > 100) throw new Error('Set TARGET, ROUNDS and CONCURRENCY (2-100)');
  const users = JSON.parse(fs.readFileSync(usersFile, 'utf8'));
  if (!Array.isArray(users) || users.length < concurrency) throw new Error('Not enough test users');
  const sessions = await Promise.all(users.slice(0, concurrency).map(login));
  const results = [];
  for (let round = 0; round < rounds; round++) {
    const owner = sessions[0];
    const created = await request('/auction', {
      method: 'POST', headers: { 'Content-Type': 'application/json', Cookie: owner.cookie },
      body: JSON.stringify({ title: `Concurrency test ${round + 1}`, owner: owner.id,
        description: 'Concurrency test', endTime: new Date(Date.now() + 3600000).toISOString(), minimumPrice: 1 })
    });
    if (created.status !== 200 || !created.body.id) throw new Error(`Auction creation failed: HTTP ${created.status}`);
    const auctionId = created.body.id;
    const bids = await Promise.all(sessions.map(async (session, index) => {
      const value = 100 + index;
      const result = await request(`/auction/${encodeURIComponent(auctionId)}/bid`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', Cookie: session.cookie },
        body: JSON.stringify({ auctionId, user: session.id, value })
      });
      return { value, status: result.status, response: result.body, latencyMs: result.ms };
    }));
    const auction = await request(`/auction/${encodeURIComponent(auctionId)}`);
    const listed = await request(`/auction/${encodeURIComponent(auctionId)}/bid?st=0&len=100`);
    if (auction.status !== 200 || listed.status !== 200 || !Array.isArray(listed.body))
      throw new Error(`Final read failed: auction HTTP ${auction.status}, bids HTTP ${listed.status}`);
    const accepted = bids.filter(bid => bid.status >= 200 && bid.status < 300);
    const maximumAccepted = accepted.length ? Math.max(...accepted.map(bid => bid.value)) : null;
    const winner = auction.body.winnerBid == null ? null : auction.body.winnerBid.value;
    const persisted = listed.body.map(bid => bid.value);
    const acceptedMissing = accepted.filter(bid => !persisted.includes(bid.value)).map(bid => bid.value);
    results.push({ auctionId, bids, winner, maximumAccepted, persisted, acceptedMissing,
      inconsistent: maximumAccepted === null || winner !== maximumAccepted || acceptedMissing.length > 0 });
  }
  const latencies = results.flatMap(round => round.bids.map(bid => bid.latencyMs));
  const failures = results.filter(round => round.inconsistent).length;
  console.log(JSON.stringify({ rounds, concurrency, failures, failurePercent: failures * 100 / rounds,
    p50: percentile(latencies, 0.50), p95: percentile(latencies, 0.95), p99: percentile(latencies, 0.99), results }, null, 2));
  if (failures) process.exitCode = 1;
}

main().catch(error => { console.error(error.message); process.exitCode = 1; });