"use strict";

const fs = require("node:fs/promises");
const path = require("node:path");

const target = process.env.TARGET;
const count = Number.parseInt(process.argv[2] ?? "", 10);
const usersFile = path.join(__dirname, "users.data");
const categories = [
  { name: "astronomy", title: "Telescope for astronomy", description: "A precision telescope for astronomy and stargazing." },
  { name: "photography", title: "Camera for photography", description: "A mirrorless camera and lenses for photography." },
  { name: "cycling", title: "Bicycle for cycling", description: "A lightweight bicycle for cycling and touring." },
  { name: "cooking", title: "Cookware for cooking", description: "A professional cookware set for cooking." },
  { name: "music", title: "Guitar for music", description: "An electric guitar and amplifier for music." },
];

if (!target || !Number.isInteger(count) || count < 1) {
  throw new Error("Usage: TARGET=<functions-url> node seed-search-auctions.js <count>");
}

async function request(url, options = {}) {
  const response = await fetch(`${target}${url}`, {
    ...options,
    headers: {
      accept: "application/json",
      ...(options.headers ?? {}),
    },
  });
  const body = await response.text();
  if (!response.ok) {
    throw new Error(`${options.method ?? "GET"} ${url} returned HTTP ${response.status}: ${body.slice(0, 300)}`);
  }
  return { response, body };
}

async function main() {
  const users = JSON.parse(await fs.readFile(usersFile, "utf8"));
  if (!Array.isArray(users) || users.length === 0 || users.some(user => !user.id || !user.pwd)) {
    throw new Error("users.data must contain at least one test user with an id and password");
  }

  const endTime = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
  for (let i = 0; i < count; i++) {
    const user = users[i % users.length];
    const { response } = await request("/user/auth", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ user: user.id, pwd: user.pwd }),
    });
    const cookie = response.headers.get("set-cookie")?.split(";")[0];
    if (!cookie) throw new Error("Authentication succeeded without a session cookie");

    const category = categories[i % categories.length];
    const payload = {
      title: `${category.title} ${i}`,
      description: `cc26part4data ${category.description} category ${category.name}. Lot ${i}.`,
      owner: user.id,
      endTime,
      minimumPrice: 10 + (i % 90),
    };
    const { body } = await request("/auction", {
      method: "POST",
      headers: { "content-type": "application/json", cookie },
      body: JSON.stringify(payload),
    });
    const auction = JSON.parse(body);
    if (!auction.id) throw new Error("Auction creation succeeded without returning an id");
    if (i % 100 === 99 || i === count - 1) {
      console.log(`Created ${i + 1}/${count} Part 4 search auctions`);
    }
  }
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
