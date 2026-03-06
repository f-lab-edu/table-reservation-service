# 🍽️ Table Reservation Service

> 선착순 보장을 위한 동시성 제어를 직접 설계하고 검증한 식당 예약 서비스입니다. <br>
> 동시성 제어 → 선착순 보장 → 핫스팟 개선까지 단계별로 문제를 정의하고, k6 부하 테스트로 각 방식의 한계를 수치로 검증했습니다.


<br>

## 기술 스택
- Java 21, Spring Boot, Spring Data JPA
- MySQL
- Docker
- k6, Grafana, Prometheus 


## 패키지 구조

```
tablereservationservice
│
├── presentation          # 표현 계층: 클라이언트 접점
│   └── {domain}
│       ├── controller    # HTTP 요청 및 응답 제어
│       └── dto           # API 스펙 (Request/Response DTO)
│
├── application           # 응용 계층: 비즈니스 흐름 제어
│   └── {domain}
│       ├── dto           # 서비스 간 데이터 전달 (Command/Result)
│       └── service       # 비즈니스 로직 및 트랜잭션 관리
│
├── domain                # 도메인 계층: 핵심 규칙 및 모델
│   └── {domain}
│       ├── model         # 순수 도메인 모델 (Pure Java Object)
│       ├── repository    # 저장소 인터페이스 (추상화)
│       └── {vo/enum}     # 도메인 전용 값 객체 및 상태 코드
│
├── infrastructure        # 인프라 계층: 기술적 세부 구현
│   ├── common
│   │   └── entity        # 공통 엔티티 (BaseTimeEntity 등)
│   └── {domain}
│       ├── entity        # DB 테이블 매핑 (JPA Entity)
│       └── repository    # Spring Data JPA를 이용한 저장소 구현체
│
└── global                # 전역 설정 및 공통 모듈
    ├── config            # 보안(Security), 인프라 연동 설정
    ├── exception         # 전역 예외 처리 (ExceptionHandler, ErrorCode)
    └── util              # 공통 유틸리티 (JWT, 공통 로직)
```

---

## 🎯 프로젝트 목표: 선착순 예약 동시성 제어

### 문제 정의

100명의 유저가 10개의 잔여 좌석을 동시에 예약하는 상황에서 두 가지 요구사항을 만족해야 했습니다.

1. **초과 예약 방지** — 10개 좌석에 정확히 10건만 예약 생성
2. **선착순 보장** — 먼저 요청한 유저가 먼저 예약 성공

### 문제 해결 과정

**Step 1. 동시성 제어 — 오버 부킹 방지**
- synchronized, CAS, 낙관적 락, 비관적 락을 순서대로 적용하며 각 방식의 성능과 한계를 검증했습니다.
- [k6 부하 테스트 결과 보고서 - 동시성 제어 및 시스템 한계 분석](https://jeondui.notion.site/k6-31b1b71577cb80319005f7d54bbd4620)

**Step 2. 선착순 보장**
- 락은 정합성 도구이지, 선착순 보장 도구가 아닙니다.
- 비관적 락은 "먼저 락을 획득한 트랜잭션"이, 낙관적 락은 "먼저 커밋에 성공한 트랜잭션"이 승자가 됩니다.
- 선착순을 보장하려면 입장 순서 자체를 시스템이 강제해야 합니다.
- [Processing Time 기준 선착순 보장을 위한 메시지 큐 도입](https://jeondui.notion.site/31b1b71577cb80d2bfffdea82e3e589d?pvs=74)


**Step 3. 핫스팟 개선 — 실패 확정 요청이 Consumer까지 도달하는 문제**
- MQ로 선착순은 보장됐지만 새로운 비효율이 발견됐습니다.
- 100명이 10석에 몰리면, 실패가 확실한 90건도 전부 Queue를 통과해 Consumer가 DB 조회 후 실패 처리해야 합니다.
- 이를 해결하기 위해 Redis INCR을 앞단 필터로 두어 수량 초과 요청을 Queue 진입 전에 차단하고, INCR과 Stream 적재를 Lua 스크립트로 묶어 원자성을 보장했습니다.
- [오픈런 예약 핫스팟 문제 개선 - Redis Stream 전환까지의 설계 과정](https://jeondui.notion.site/Redis-Stream-31b1b71577cb800baeb9d86fa4396841?pvs=74)

<br>
