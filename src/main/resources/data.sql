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
INSERT INTO restaurant_slot
(restaurant_id, time, max_capacity, created_at, modified_at)
VALUES
(1, '18:00:00', 10, @now, @now);


-- 날짜 변수 정의
SET @fcfs_date := CURDATE();
SET @load_start_date := DATE_ADD(@fcfs_date, INTERVAL 1 DAY);

-- 정합성 테스트용 (Hot Row 1개, 수량 10개)
INSERT INTO daily_slot_capacity
(slot_id, date, remaining_count, created_at, modified_at)
VALUES
(1, @fcfs_date, 10, @now, @now);


-- 지속 부하용 (7일치 생성)
INSERT INTO daily_slot_capacity
(slot_id, date, remaining_count, created_at, modified_at)
VALUES
(1, DATE_ADD(@load_start_date, INTERVAL 0 DAY), 10, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 1 DAY), 10, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 2 DAY), 10, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 3 DAY), 10, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 4 DAY), 10, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 5 DAY), 10, @now, @now),
(1, DATE_ADD(@load_start_date, INTERVAL 6 DAY), 10, @now, @now);
