SET @now := NOW();

-- 1. USERS (Owner 1명 + Recursive CTE를 이용한 Customer 100명 생성)
INSERT INTO users (email, password, name, phone, user_role, created_at, modified_at)
VALUES ('owner@test.com', '{bcrypt}$2a$10$4tHhbjhE34bJO6KAveAN0uzi8KXBT/Gm9CZo9CB83qvne44R78I/e', 'Owner 1', '010-9999-9999', 'OWNER', @now, @now);

-- Customer 1~100 생성
INSERT INTO users (email, password, name, phone, user_role, created_at, modified_at)
WITH RECURSIVE nums AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM nums WHERE n < 100
)
SELECT
    CONCAT('customer', n, '@test.com'),
    '{bcrypt}$2a$10$4tHhbjhE34bJO6KAveAN0uzi8KXBT/Gm9CZo9CB83qvne44R78I/e',
    CONCAT('Customer ', n),
    CONCAT('010-0000-', LPAD(n, 4, '0')),
    'CUSTOMER',
    @now,
    @now
FROM nums;

-- RESTAURANT (owner_id = 1 가정)
INSERT INTO restaurant
(owner_id, region_code, category_code, name, address, description, main_menu_name, main_menu_price, created_at, modified_at)
VALUES
(1, 'RG01', 'CT01', '강남 한상', '서울 강남', 'Load test dummy restaurant', 'Test Menu', 10000, @now, @now);


-- RESTAURANT_SLOT (restaurant_id=1)
-- slot 1~4: Consistent Hash Exchange 파티션 테스트용 (슬롯마다 다른 파티션 큐로 라우팅)
INSERT INTO restaurant_slot
(restaurant_id, time, max_capacity, created_at, modified_at)
VALUES
(1, '18:00:00', 10, @now, @now),
(1, '19:00:00', 10, @now, @now),
(1, '20:00:00', 10, @now, @now),
(1, '21:00:00', 10, @now, @now);


-- k6와 동일하게 UTC_DATE() 기준으로 날짜 계산 (KST/UTC 타임존 불일치 방지)
SET @load_start_date := DATE_ADD(UTC_DATE(), INTERVAL 1 DAY);

-- 지속 부하용 (slot 1~4 각 7일치 생성, version 초기값 0)
INSERT INTO daily_slot_capacity
(slot_id, date, remaining_count, version, created_at, modified_at)
VALUES
(1, DATE_ADD(@load_start_date, INTERVAL 0 DAY), 10, 0, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 1 DAY), 10, 0, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 2 DAY), 10, 0, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 3 DAY), 10, 0, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 4 DAY), 10, 0, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 5 DAY), 10, 0, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 6 DAY), 10, 0, @now, @now),
(2, DATE_ADD(@load_start_date, INTERVAL 0 DAY), 10, 0, @now, @now),
(2, DATE_ADD(@load_start_date, INTERVAL 1 DAY), 10, 0, @now, @now),
(2, DATE_ADD(@load_start_date, INTERVAL 2 DAY), 10, 0, @now, @now),
(2, DATE_ADD(@load_start_date, INTERVAL 3 DAY), 10, 0, @now, @now),
(2, DATE_ADD(@load_start_date, INTERVAL 4 DAY), 10, 0, @now, @now),
(2, DATE_ADD(@load_start_date, INTERVAL 5 DAY), 10, 0, @now, @now),
(2, DATE_ADD(@load_start_date, INTERVAL 6 DAY), 10, 0, @now, @now),
(3, DATE_ADD(@load_start_date, INTERVAL 0 DAY), 10, 0, @now, @now),
(3, DATE_ADD(@load_start_date, INTERVAL 1 DAY), 10, 0, @now, @now),
(3, DATE_ADD(@load_start_date, INTERVAL 2 DAY), 10, 0, @now, @now),
(3, DATE_ADD(@load_start_date, INTERVAL 3 DAY), 10, 0, @now, @now),
(3, DATE_ADD(@load_start_date, INTERVAL 4 DAY), 10, 0, @now, @now),
(3, DATE_ADD(@load_start_date, INTERVAL 5 DAY), 10, 0, @now, @now),
(3, DATE_ADD(@load_start_date, INTERVAL 6 DAY), 10, 0, @now, @now),
(4, DATE_ADD(@load_start_date, INTERVAL 0 DAY), 10, 0, @now, @now),
(4, DATE_ADD(@load_start_date, INTERVAL 1 DAY), 10, 0, @now, @now),
(4, DATE_ADD(@load_start_date, INTERVAL 2 DAY), 10, 0, @now, @now),
(4, DATE_ADD(@load_start_date, INTERVAL 3 DAY), 10, 0, @now, @now),
(4, DATE_ADD(@load_start_date, INTERVAL 4 DAY), 10, 0, @now, @now),
(4, DATE_ADD(@load_start_date, INTERVAL 5 DAY), 10, 0, @now, @now),
(4, DATE_ADD(@load_start_date, INTERVAL 6 DAY), 10, 0, @now, @now);
