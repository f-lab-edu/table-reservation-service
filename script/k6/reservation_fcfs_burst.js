import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const successCounter = new Counter('reservation_success');
const failCounter = new Counter('reservation_fail');

const BASE_URL = 'http://localhost:8080';
const RESERVE_URL = `${BASE_URL}/api/reservations/test`;

const SLOT_ID = 1;
const PARTY_SIZE = 1; // 10개 좌석이므로 10명 성공 기대

export const options = {
  scenarios: {
    fcfs_burst: {
      executor: 'per-vu-iterations',
      vus: 100,        // 100명 동시 접속
      iterations: 1,   // 각 유저당 1회 실행
      maxDuration: '30s',
    },
  },
  thresholds: {
    // 경합 중에도 95%의 요청은 5초 이내에 완료되어야 함 (비관적 락 대기 시간 고려)
    http_req_duration: ['p(95)<5000'],
  },
};

// 고정된 테스트 날짜 (DB에 입력한 @fcfs_date와 일치해야 함)
function getTestDate() {
  return new Date().toISOString().slice(0, 10);
}

export default function () {
  const userIndex = __VU; // 1..100
  const userEmail = `customer${userIndex}@test.com`;
  const requestId = `req-${userIndex}-${Date.now()}`;

  const payload = JSON.stringify({
    slotId: SLOT_ID,
    date: getTestDate(),
    partySize: PARTY_SIZE,
    note: 'optimistic-lock-test',
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

  if (isSuccess) {
    successCounter.add(1);
  } else {
    failCounter.add(1);
  }

  check(res, {
     'status is 200/201/409/400': (r) => [200, 201, 400, 409].includes(r.status),
   });

  // 선착순 결과 확인을 위한 로그
  if (isSuccess) {
    console.log(`[SUCCESS] User: ${userEmail}, Status: ${res.status}`);
  }
  if (res.status !== 200 && res.status !== 201) {
    console.error(`[ERROR] Status: ${res.status}, Body: ${res.body}`);
  }

  sleep(0.1);
}
