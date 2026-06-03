import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const success      = new Counter('reservation_success');
const failCapacity = new Counter('reservation_fail_capacity');
const failOther    = new Counter('reservation_fail_other');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SLOT_ID  = __ENV.SLOT_ID  || '2';   // 시나리오 1: slot 2 (10석), 시나리오 2: slot 5 (50석)
const DATE     = __ENV.DATE     || (() => {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return d.toISOString().slice(0, 10);
})();

// 100 VU가 동시에 1회 요청 — 순간 경합 유도
export const options = {
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus: 100,
      iterations: 1,
      maxDuration: '30s',
    },
  },
};

export default function () {
  const vu    = __VU;
  const email = `customer${vu}@test.com`;

  const res = http.post(
    `${BASE_URL}/api/reservations`,
    JSON.stringify({
      slotId:    parseInt(SLOT_ID),
      date:      DATE,
      partySize: 1,
      note:      '',
    }),
    {
      headers: {
        'Content-Type':  'application/json',
        'X-User-Email':  email,
        'Idempotency-Key': uuidv4(),
      },
      timeout: '10s',
    }
  );

  if (res.status === 200 || res.status === 201) {
    success.add(1);
  } else if (res.status === 409) {
    failCapacity.add(1);
  } else {
    failOther.add(1);
    console.error(`[FAIL] vu=${vu} status=${res.status} body=${res.body}`);
  }

  check(res, {
    '200 or 409 (정상 처리)': (r) => r.status === 200 || r.status === 201 || r.status === 409,
  });
}
