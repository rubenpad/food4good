// After the burst: every accepted Order exists exactly once and is delivered, and stock stayed sane.
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL ?? "http://localhost:8000";
const TIMEOUT_SECONDS = 300;
const burst = JSON.parse(open("/load-tests/results/burst.json"));

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ["rate==1"] },
};

const stockRulesHold = (rows) =>
  rows.every((row) => row.onHand >= 0 && row.reserved >= 0 && row.reserved <= row.onHand);

export default function () {
  const started = Date.now();
  for (;;) {
    const kitchen = http.get(`${BASE_URL}/api/kitchen`).json();
    check(http.get(`${BASE_URL}/api/storage`).json(), { "stock rules hold while finishing": stockRulesHold });
    if (kitchen.ordersWaitingForIngredients + kitchen.ordersReady + kitchen.ordersPreparing === 0) break;
    if (Date.now() - started > TIMEOUT_SECONDS * 1000) {
      // A failed check, because k6's fail() would only end this iteration and the run would still pass.
      console.error(`Orders not delivered after ${TIMEOUT_SECONDS}s: ${JSON.stringify(kitchen)}`);
      check(null, { "every Order is delivered in time": () => false });
      return;
    }
    sleep(1);
  }

  const seen = new Set();
  let rows = 0;
  let delivered = 0;
  let cursor = null;
  do {
    const page = http.get(`${BASE_URL}/api/orders?limit=200${cursor ? `&cursor=${cursor}` : ""}`).json();
    for (const order of page.items) {
      rows++;
      seen.add(order.id);
      if (order.status === "DELIVERED") delivered++;
    }
    cursor = page.nextCursor;
  } while (cursor);

  console.log(
    `accepted=${burst.accepted} rows=${rows} distinct=${seen.size} delivered=${delivered} ` +
      `finished ${Math.round((Date.now() - started) / 1000)}s after the burst`,
  );
  check(null, {
    "every accepted Order exists": () => rows === burst.accepted,
    "no Order exists twice": () => seen.size === rows,
    "every Order was delivered": () => delivered === burst.accepted,
  });
}
