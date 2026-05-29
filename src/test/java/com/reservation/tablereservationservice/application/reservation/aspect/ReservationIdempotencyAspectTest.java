package com.reservation.tablereservationservice.application.reservation.aspect;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.reservation.tablereservationservice.application.reservation.facade.ReservationFacade;
import com.reservation.tablereservationservice.application.reservation.service.ReservationCreateResult;
import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.fixture.ReservationFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueuePublisher;
import com.reservation.tablereservationservice.infrastructure.redis.ReservationPublisher;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

@SpringBootTest
@ActiveProfiles("test")
class ReservationIdempotencyAspectTest {

	private static final Long RESERVATION_ID = 1L;
	private static final Long SLOT_ID = 10L;
	private static final LocalDate BASE_DATE = LocalDate.of(2030, 1, 1);
	private static final int PARTY_SIZE = 2;
	private static final int DEPOSIT_PER_PERSON = 5000;
	private static final String KEY_PREFIX = "reservation:idempotency:";

	@Autowired
	private ReservationFacade reservationFacade;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@MockitoBean
	private ReservationService reservationService;

	@MockitoBean
	private ReservationPublisher reservationPublisher;

	@MockitoBean
	private ReservationRepository reservationRepository;

	@MockitoBean
	private RestaurantSlotRepository restaurantSlotRepository;

	@MockitoBean
	private CancelQueuePublisher cancelQueuePublisher;

	private Reservation reservation;
	private RestaurantSlot slot;
	private ReservationRequestDto req;

	@BeforeEach
	void setUp() {
		cleanupRedis();

		reservation = ReservationFixture.pending()
				.reservationId(RESERVATION_ID)
				.slotId(SLOT_ID)
				.partySize(PARTY_SIZE)
				.build();

		slot = RestaurantSlotFixture.slot()
				.slotId(SLOT_ID)
				.depositPerPerson(DEPOSIT_PER_PERSON)
				.build();

		req = new ReservationRequestDto(SLOT_ID, BASE_DATE, PARTY_SIZE, "note");
	}

	@AfterEach
	void tearDown() {
		cleanupRedis();
	}

	private void cleanupRedis() {
		Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}

	@Test
	@DisplayName("첫 요청 - proceed 실행 후 Redis에 결과가 캐싱된다")
	void firstRequest_proceeds_andCachesResult() {
		String idempotencyKey = UUID.randomUUID().toString();
		given(reservationService.reserve(anyString(), any(), eq(idempotencyKey)))
				.willReturn(new ReservationCreateResult(reservation, DEPOSIT_PER_PERSON * PARTY_SIZE));

		ReservationCreateResult result = reservationFacade.reserve("user@test.com", req, idempotencyKey);

		assertThat(result.reservation().getReservationId()).isEqualTo(RESERVATION_ID);
		verify(reservationService, times(1)).reserve(anyString(), any(), eq(idempotencyKey));
		// PROCESSING → JSON 결과로 덮어써졌는지 확인
		String cached = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
		assertThat(cached).isNotNull().isNotEqualTo("PROCESSING");
	}

	@Test
	@DisplayName("중복 요청 (캐시 hit) - 서비스 재호출 없이 캐시에서 결과 반환")
	void duplicateRequest_cacheHit_returnsWithoutCallingService() {
		String idempotencyKey = UUID.randomUUID().toString();

		// 첫 요청으로 캐시 생성
		given(reservationService.reserve(anyString(), any(), eq(idempotencyKey)))
				.willReturn(new ReservationCreateResult(reservation, DEPOSIT_PER_PERSON * PARTY_SIZE));
		reservationFacade.reserve("user@test.com", req, idempotencyKey);

		// 캐시 복원 경로에서 사용하는 조회 mock
		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(reservation);
		given(restaurantSlotRepository.fetchById(SLOT_ID)).willReturn(slot);

		// 두 번째 요청 (같은 key)
		ReservationCreateResult result = reservationFacade.reserve("user@test.com", req, idempotencyKey);

		assertThat(result.reservation().getReservationId()).isEqualTo(RESERVATION_ID);
		// 서비스는 첫 요청 때만 호출되어야 함
		verify(reservationService, times(1)).reserve(anyString(), any(), eq(idempotencyKey));
		// 두 번째 요청은 proceed 전에 반환되므로 holdSeat도 1회만 호출
		verify(reservationPublisher, times(1)).holdSeat(anyLong(), any(), anyInt());
	}

	@Test
	@DisplayName("DataIntegrityViolationException - DB에서 기존 예약 조회 후 정상 반환")
	void dataIntegrityViolation_resolvesFromDb() {
		String idempotencyKey = UUID.randomUUID().toString();

		given(reservationService.reserve(anyString(), any(), eq(idempotencyKey)))
				.willThrow(new DataIntegrityViolationException("Duplicate entry for idempotency_key"));
		given(reservationRepository.findByIdempotencyKey(idempotencyKey))
				.willReturn(Optional.of(reservation));
		given(restaurantSlotRepository.fetchById(SLOT_ID)).willReturn(slot);

		ReservationCreateResult result = reservationFacade.reserve("user@test.com", req, idempotencyKey);

		assertThat(result.reservation().getReservationId()).isEqualTo(RESERVATION_ID);
		verify(reservationRepository).findByIdempotencyKey(idempotencyKey);
		// 예외 발생 시 facade의 catch block이 좌석을 복원
		verify(reservationPublisher).releaseSeat(anyLong(), any(), anyInt());
	}

	@Test
	@DisplayName("기타 예외 - Redis 키 삭제 후 예외 전파")
	void otherException_deletesKeyAndRethrows() {
		String idempotencyKey = UUID.randomUUID().toString();
		RuntimeException cause = new RuntimeException("DB connection failed");

		given(reservationService.reserve(anyString(), any(), eq(idempotencyKey)))
				.willThrow(cause);

		assertThatThrownBy(() -> reservationFacade.reserve("user@test.com", req, idempotencyKey))
				.isSameAs(cause);

		// 예외 시 Redis 키가 삭제되어야 함 (다음 요청이 재시도 가능하도록)
		assertThat(redisTemplate.hasKey(KEY_PREFIX + idempotencyKey)).isFalse();
		verify(reservationPublisher).releaseSeat(anyLong(), any(), anyInt());
	}
}
