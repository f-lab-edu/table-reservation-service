CREATE TABLE users (
    user_id     BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 PK',
    email       VARCHAR(100) NOT NULL UNIQUE      COMMENT '이메일',
    password    VARCHAR(255) NOT NULL             COMMENT '비밀번호',
    name        VARCHAR(50)  NOT NULL             COMMENT '이름',
    phone       VARCHAR(20)                       COMMENT '전화번호',
    user_role   VARCHAR(20)  NOT NULL             COMMENT '사용자 역할',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP                   COMMENT '생성일시',
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자';


CREATE TABLE restaurant (
    restaurant_id   BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '식당 PK',
    owner_id        BIGINT       NOT NULL              COMMENT '점주 사용자 FK',
    region_code     VARCHAR(10)  NOT NULL              COMMENT '지역 코드',
    category_code   VARCHAR(10)  NOT NULL              COMMENT '카테고리 코드',
    name            VARCHAR(100) NOT NULL              COMMENT '식당명',
    address         VARCHAR(255) NOT NULL              COMMENT '주소',
    description     TEXT                              COMMENT '설명',
    main_menu_name  VARCHAR(100)                       COMMENT '대표 메뉴명',
    main_menu_price INT                               COMMENT '대표 메뉴 가격',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP                   COMMENT '생성일시',
    modified_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    CONSTRAINT fk_restaurant_owner
        FOREIGN KEY (owner_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='식당';


CREATE TABLE restaurant_slot (
    slot_id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '슬롯 PK',
    restaurant_id      BIGINT NOT NULL                   COMMENT '식당 FK',
    time               TIME   NOT NULL                   COMMENT '예약 시간',
    max_capacity       INT    NOT NULL                   COMMENT '슬롯 최대 수용 인원',
    deposit_per_person INT    NOT NULL DEFAULT 0          COMMENT '인당 예약금',
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP                   COMMENT '생성일시',
    modified_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    CONSTRAINT fk_slot_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurant(restaurant_id),
    CONSTRAINT uq_restaurant_time UNIQUE (restaurant_id, time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='식당 예약 슬롯';


CREATE TABLE daily_slot_capacity (
    capacity_id     BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '일자별 슬롯 수량 PK',
    slot_id         BIGINT NOT NULL                   COMMENT '슬롯 FK',
    date            DATE   NOT NULL                   COMMENT '날짜',
    remaining_count INT    NOT NULL                   COMMENT '잔여 수량',
    version         BIGINT NOT NULL DEFAULT 0          COMMENT '낙관적 락 버전',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP                   COMMENT '생성일시',
    modified_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    CONSTRAINT fk_capacity_slot
        FOREIGN KEY (slot_id) REFERENCES restaurant_slot(slot_id),
    CONSTRAINT uq_slot_date UNIQUE (slot_id, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='일자별 슬롯 잔여 수량';


CREATE TABLE reservation (
    reservation_id  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '예약 PK',
    user_id         BIGINT       NOT NULL              COMMENT '예약자 FK',
    slot_id         BIGINT       NOT NULL              COMMENT '슬롯 FK',
    visit_at        DATETIME     NOT NULL              COMMENT '방문 일시',
    party_size      INT          NOT NULL              COMMENT '예약 인원',
    note            VARCHAR(255)                       COMMENT '요청 사항',
    status          VARCHAR(20)  NOT NULL              COMMENT '예약 상태',
    idempotency_key VARCHAR(36)  UNIQUE                COMMENT '멱등성 키',
    payment_key     VARCHAR(200)                       COMMENT 'Toss 결제 키 (approve 요청 시 저장)',
    version         BIGINT       NOT NULL DEFAULT 0    COMMENT '낙관적 락 버전',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP                   COMMENT '생성일시',
    modified_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    CONSTRAINT fk_reservation_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_reservation_slot
        FOREIGN KEY (slot_id) REFERENCES restaurant_slot(slot_id),
    INDEX idx_reservation_status_created_at (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='예약';


CREATE TABLE payment (
    payment_id      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '결제 PK',
    reservation_id  BIGINT       NOT NULL UNIQUE         COMMENT '예약 FK',
    idempotency_key VARCHAR(36)  NOT NULL UNIQUE         COMMENT '멱등성 키',
    payment_key     VARCHAR(200) NOT NULL              COMMENT 'Toss 결제 키',
    amount          INT          NOT NULL              COMMENT '결제 금액',
    status          VARCHAR(10)  NOT NULL              COMMENT '결제 상태',
    approved_at     DATETIME     NOT NULL              COMMENT '결제 승인 일시',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP                   COMMENT '생성일시',
    modified_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    CONSTRAINT fk_payment_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation(reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='결제';


CREATE TABLE notification (
    notification_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '알림 PK',
    receiver_id     BIGINT       NOT NULL              COMMENT '수신자 FK',
    reservation_id  BIGINT       NOT NULL              COMMENT '예약 FK',
    type            VARCHAR(20)  NOT NULL              COMMENT '알림 유형 (CONFIRM/CANCEL/REMINDER)',
    title           VARCHAR(100) NOT NULL              COMMENT '알림 제목',
    content         VARCHAR(500) NOT NULL              COMMENT '알림 내용',
    is_read         TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '읽음 여부',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP                   COMMENT '생성일시',
    modified_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    CONSTRAINT fk_notification_receiver
        FOREIGN KEY (receiver_id) REFERENCES users(user_id),
    CONSTRAINT fk_notification_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation(reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='알림';