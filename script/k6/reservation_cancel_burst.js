// CONFIRMED 예약(id 100001~100060)을 동시에 취소하는 k6 부하 스크립트.
// 각 취소는 같은 트랜잭션에서 outbox에 PENDING 행을 남긴다.
//
// 실행(네이티브 k6):   k6 run script/k6/reservation_cancel_burst.js
// 실행(도커 k6, mac):  docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run - < script/k6/reservation_cancel_burst.js
import http from 'k6/http';
import exec from 'k6/execution';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
	scenarios: {
		cancel60: {
			executor: 'shared-iterations', // 전체 60회를 VU들이 나눠 1회씩 실행
			vus: 20,
			iterations: 60,
			maxDuration: '30s',
		},
	},
};

export default function () {
	const id = 100001 + exec.scenario.iterationInTest; // 0..59 → 100001..100060 (각 id 정확히 1회)
	http.post(`${BASE}/api/reservations/${id}/cancel`, null, {
		headers: { 'X-User-Email': 'customer1@test.com' },
	});
}