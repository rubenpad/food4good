// The burst from PRD NFR-1: 1,000 Orders as 100 concurrent requests of 10. Warm-up bursts run first
// and aren't measured, unless WARMUP_BURSTS=0, which measures a just-started JVM.
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL ?? "http://localhost:8000";
const WARMUP_BURSTS = Number(__ENV.WARMUP_BURSTS ?? 3);
const P99_TARGET_MS = Number(__ENV.P99_TARGET_MS ?? 200);
const SECONDS_BETWEEN_BURSTS = 8;

const placeMs = new Trend("place_orders_ms", true);
const accepted = new Counter("orders_accepted");

function burstAt(startSeconds, phase) {
  return {
    executor: "shared-iterations",
    vus: 100,
    iterations: 100,
    startTime: `${startSeconds}s`,
    maxDuration: "60s",
    exec: "placeOrders",
    tags: { phase },
  };
}

const scenarios = {};
for (let i = 0; i < WARMUP_BURSTS; i++) {
  scenarios[`warmup_${i}`] = burstAt(i * SECONDS_BETWEEN_BURSTS, "warmup");
}
const measuredAt = WARMUP_BURSTS * SECONDS_BETWEEN_BURSTS;
scenarios.measured = burstAt(measuredAt, "measured");
scenarios.watch_stock = {
  executor: "constant-vus",
  vus: 1,
  duration: `${measuredAt + SECONDS_BETWEEN_BURSTS}s`,
  exec: "watchStock",
};

export const options = {
  scenarios,
  thresholds: {
    [`place_orders_ms{phase:measured}`]: [`p(99)<${P99_TARGET_MS}`],
    checks: ["rate==1"],
  },
  summaryTrendStats: ["p(50)", "p(95)", "p(99)", "max"],
};

export function setup() {
  for (let i = 0; i < 180; i++) {
    if (http.get(`${BASE_URL}/api/kitchen`, { tags: { name: "ready" } }).status === 200) return;
    sleep(1);
  }
  throw new Error("The app did not become ready");
}

export function placeOrders() {
  const response = http.post(`${BASE_URL}/api/orders`, JSON.stringify({ quantity: 10 }), {
    headers: { "Content-Type": "application/json" },
    tags: { name: "place" },
  });
  placeMs.add(response.timings.duration);
  const ok = check(response, {
    "placing Orders succeeds": (r) => r.status === 200,
    "all 10 Orders are accepted": (r) => r.status === 200 && r.json("accepted") === 10,
  });
  if (ok) accepted.add(10);
}

export function watchStock() {
  const stock = http.get(`${BASE_URL}/api/storage`, { tags: { name: "stock" } }).json();
  check(stock, {
    "stock rules hold": (rows) =>
      rows.every(
        (row) => row.onHand >= 0 && row.reserved >= 0 && row.reserved <= row.onHand && row.available === row.onHand - row.reserved,
      ),
  });
  sleep(0.2);
}

export function handleSummary(data) {
  const measured = data.metrics["place_orders_ms{phase:measured}"]?.values ?? {};
  const round = (value) => Math.round(value ?? NaN);
  const line =
    `${WARMUP_BURSTS === 0 ? "cold" : `warm (after ${WARMUP_BURSTS} bursts)`}: ` +
    `p50=${round(measured["p(50)"])}ms p95=${round(measured["p(95)"])}ms ` +
    `p99=${round(measured["p(99)"])}ms max=${round(measured.max)}ms, target p99<${P99_TARGET_MS}ms\n` +
    `Orders accepted in all bursts: ${data.metrics.orders_accepted?.values.count ?? 0}\n` +
    `checks passed: ${data.metrics.checks?.values.passes ?? 0}, failed: ${data.metrics.checks?.values.fails ?? 0}\n`;
  return {
    stdout: line,
    "/load-tests/results/burst.json": JSON.stringify({
      warmupBursts: WARMUP_BURSTS,
      accepted: data.metrics.orders_accepted?.values.count ?? 0,
      measured,
    }),
  };
}
