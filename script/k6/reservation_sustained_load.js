import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const successCounter = new Counter('reservation_success');
const failCounter = new Counter('reservation_fail');

const BASE_URL = 'http://localhost:8080';
const RESERVE_URL = `${BASE_URL}/api/reservations/test`;

const SLOT_ID = 1;      // slot1 고정
const PARTY_SIZE = 1;

export const options = {
  scenarios: {
    fcfs_sustained: {
      executor: 'constant-vus',
      vus: 100,            // 유저 100명 동일 유지
      duration: '60s',
      gracefulStop: '5s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<8000'],
  },
};

function getLoadTestDate() {
  const d = new Date();
  d.setDate(d.getDate() + 1 + (__ITER % 7));
  return d.toISOString().slice(0, 10);
}

export default function () {
  const userIndex = __VU; // 1..100
  const userEmail = `customer${userIndex}@test.com`;

  const requestId = `req-${userIndex}-${__ITER}-${Date.now()}`;

  const payload = JSON.stringify({
    slotId: SLOT_ID,
    date: getLoadTestDate(),
    partySize: PARTY_SIZE,
    note: 'pessimistic-lock-sustained-rolling-date',
    requestId: requestId,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-User-Email': userEmail,
    },
  };

  const res = http.post(RESERVE_URL, payload, params);

  const isSuccess = res.status === 200 || res.status === 201;

  if (isSuccess) successCounter.add(1);
  else failCounter.add(1);

  // 409/400도 정상 케이스로 인정(정원 초과/검증 실패 등)
  check(res, {
    'status is 200/201/400/409': (r) => [200, 201, 400, 409].includes(r.status),
  });

  if (!isSuccess && res.status >= 500) {
    console.error(`[5XX] user=${userEmail} iter=${__ITER} status=${res.status} body=${res.body}`);
  }

  sleep(0.02);
}
