# 🏃 OpenRun

오픈런 상황의 대규모 동시 요청 환경에서 인기 식당의 좌석 예약과 결제 기능을 제공하는 API 서버입니다.

<br>

### 💡 대규모 트래픽이 몰리는 식당 예약 서비스는 어떻게 만들어진 것일까요?

- 순간적으로 몰리는 동시 요청 속에서 어떻게 병목 없이 예약을 처리할까요?
- 외부 결제 API 장애나 메시지 유실 상황에서도 어떻게 데이터 정합성을 보장할까요?

이러한 호기심을 시작으로 좌석 예약 및 비동기 결제 파이프라인을 구현하는 프로젝트를 진행하게 되었습니다.

<br>

## 프로젝트 주요 관심사

**공통 사항**

- 변화에 유연하고 가독성 높은 코드 베이스 유지

**성능 최적화 및 인프라 설계**

- 대용량 트래픽 상황에서의 자원 관리
- 부하 테스트 & 모니터링을 통한 지속적인 성능 개선 및 인프라 복구 탄력성 검증
- 외부 시스템 연동 시 트랜잭션 경계 분리 및 장애 격리

**데이터 정합성 및 안정성**

- 분산 비동기 환경에서의 메시지 유실 및 중복 인입 리스크 관리
- 네트워크 재시도, 클라이언트의 중복 요청 상황에서 멱등성을 보장하여 데이터 오염 방지

<br>

## 시스템 아키텍처
<img width="574" height="711" alt="Image" src="https://github.com/user-attachments/assets/4dee9374-6c53-4be1-a6a9-6ac463a9c780" />

<br>

<br>

## 기술 스택

- **Language/Framework:** Java 21, Spring Boot, Spring Data JPA
- **Database:** MySQL, Redis
- **Message Queue:** RabbitMQ
- **Test**: JUnit 5, Mockito, k6
- **Monitoring**: Prometheus, Grafana

<br>

## ERD
<img width="2790" height="3885" alt="Image" src="https://github.com/user-attachments/assets/88c89c5c-eafb-4987-af12-fb916e0e94ab" />

## 🏃 OpenRun WIKI
- [기능 설계서](https://github.com/f-lab-edu/table-reservation-service/wiki/기능-설계서)
- [화면 프로토타입](https://github.com/f-lab-edu/table-reservation-service/wiki/화면-프로토타입-설계서)
