-- 반환값
--   1 : 성공 (좌석 감소 완료)
--  -1 : 좌석 부족
--  -2 : 슬롯 미오픈 (카운터 없음)
--
-- KEYS[1]: reservation:remaining:{slotId}:{date}
-- ARGV[1]: partySize

local remaining = tonumber(redis.call('GET', KEYS[1]))

if remaining == nil then
    return -2
end

if remaining < tonumber(ARGV[1]) then
    return -1
end

redis.call('DECRBY', KEYS[1], ARGV[1])
return 1