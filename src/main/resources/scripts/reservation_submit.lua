-- 반환값
--   1  : 성공 (DECR + XADD 완료)
--  -1  : 좌석 없음 → 즉시 거절 (큐 진입 전 차단)
--  -2  : 카운터 미초기화 → 슬롯이 아직 오픈되지 않음

-- 잔여 좌석 수 조회
local remaining = tonumber(redis.call('GET', KEYS[1]))

-- Redis에 해당 키가 없으면 → 슬롯 미오픈 상태
if remaining == nil then
    return -2
end

-- 잔여 좌석이 0 이하면 즉시 거절 → Consumer 까지 메시지가 내려가지 않음
-- 이것이 핫스팟 문제를 해결하는 핵심: 불필요한 DB 조회 원천 차단
if remaining <= 0 then
    return -1
end

-- 잔여 좌석 1 차감
redis.call('DECR', KEYS[1])

-- 스트림에 메시지 적재
-- '*' : Redis가 타임스탬프 기반 ID 자동 생성 (ex. 1749123456789-0)
-- MAXLEN ~ N : 스트림 길이를 N 개로 제한
--   '~' 는 "대략적으로" 라는 의미로, 정확한 N 이 아닌 약간 초과를 허용
--   정확한 MAXLEN 보다 성능이 훨씬 좋다. (내부 노드 단위로 삭제하기 때문)
redis.call('XADD', KEYS[2], 'MAXLEN', '~', ARGV[6], '*',
    'userEmail', ARGV[1],
    'slotId',    ARGV[2],
    'date',      ARGV[3],
    'partySize', ARGV[4],
    'note',      ARGV[5])

return 1
