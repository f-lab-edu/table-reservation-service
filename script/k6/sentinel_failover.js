import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const success      = new Counter('reservation_success');
const failCapacity = new Counter('reservation_fail_capacity');
const failOther    = new Counter('reservation_fail_other');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SLOT_ID  = __ENV.SLOT_ID  || '5';

// 날짜 풀 — VU마다 다른 날짜를 써서 좌석 고갈 방지
const DATES = [
  '2026-06-04', '2026-06-05', '2026-06-06',
  '2026-06-07', '2026-06-08', '2026-06-09', '2026-06-10',
];

// 10 VU × 30초 — failover 구간(3~5초)을 커버하는 지속 부하
export const options = {
  scenarios: {
    constant: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
    },
  },
};

export default function () {
  const vu   = __VU;
  const iter = __ITER;
  const date = DATES[(vu + iter) % DATES.length];
  const email = `customer${vu}@test.com`;

  const res = http.post(
    `${BASE_URL}/api/reservations`,
    JSON.stringify({
      slotId:    parseInt(SLOT_ID),
      date:      date,
      partySize: 1,
      note:      '',
    }),
    {
      headers: {
        'Content-Type':    'application/json',
        'X-User-Email':    email,
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
    console.error(`[FAIL] vu=${vu} iter=${iter} date=${date} status=${res.status}`);
  }

  check(res, {
    '2xx or 409': (r) => r.status === 200 || r.status === 201 || r.status === 409,
  });

  sleep(0.5);
}
