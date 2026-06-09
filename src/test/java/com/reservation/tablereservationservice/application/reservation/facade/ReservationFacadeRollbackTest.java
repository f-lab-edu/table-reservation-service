package com.reservation.tablereservationservice.application.reservation.facade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.reservation.tablereservationservice.application.reservation.service.ReservationCreateResult;
import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.fixture.ReservationFixture;
import com.reservation.tablereservationservice.global.config.ReservationStreamProperties;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueuePublisher;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

@SpringBootTest
@ActiveProfiles("test")
class ReservationFacadeRollbackTest {

	private static final Long SLOT_ID = 999L;
	private static final LocalDate DATE = LocalDate.of(2030, 6, 1);
	private static final int PARTY_SIZE = 2;
	private static final int INITIAL_SEAT = 10;

	@Autowired private ReservationFacade facade;
	@Autowired private StringRedisTemplate redisTemplate;
	@Autowired private ReservationStreamProperties streamProperties;

	@MockitoBean private ReservationService reservationService;
	@MockitoBean private ReservationRepository reservationRepository;
	@MockitoBean private RestaurantSlotRepository restaurantSlotRepository;
	@MockitoBean private CancelQueuePublisher cancelQueuePublisher;

	private String seatKey;

	@BeforeEach
	void setUp() {
		seatKey = streamProperties.remainingKey(SLOT_ID, DATE.toString());
		redisTemplate.opsForValue().set(seatKey, String.valueOf(INITIAL_SEAT));
	}

	@AfterEach
	void tearDown() {
		redisTemplate.delete(seatKey);
		Set<String> idempotencyKeys = redisTemplate.keys("reservation:idempotency:*");
		if (idempotencyKeys != null && !idempotencyKeys.isEmpty()) {
			redisTemplate.delete(idempotencyKeys);
		}
	}

	@Test
	@DisplayName("reserve() 비즈니스 예외 → holdSeat으로 차감된 Redis 좌석이 복원된다")
	void reserve_businessException_restoresSeat() {
		given(reservationService.reserve(any(), any(), any()))
			.willThrow(new ReservationException(ErrorCode.RESERVATION_DUPLICATED_TIME));

		assertThatThrownBy(() ->
			facade.reserve("user@test.com", request(), UUID.randomUUID().toString()))
			.isInstanceOf(ReservationException.class)
			.extracting(e -> ((ReservationException) e).getErrorCode())
			.isEqualTo(ErrorCode.RESERVATION_DUPLICATED_TIME);

		assertThat(redisTemplate.opsForValue().get(seatKey))
			.isEqualTo(String.valueOf(INITIAL_SEAT));
	}

	@Test
	@DisplayName("reserve() DB 예외 → holdSeat으로 차감된 Redis 좌석이 복원된다")
	void reserve_dbException_restoresSeat() {
		given(reservationService.reserve(any(), any(), any()))
			.willThrow(new RuntimeException("DB connection failed"));

		assertThatThrownBy(() ->
			facade.reserve("user@test.com", request(), UUID.randomUUID().toString()))
			.isInstanceOf(RuntimeException.class);

		assertThat(redisTemplate.opsForValue().get(seatKey))
			.isEqualTo(String.valueOf(INITIAL_SEAT));
	}

	@Test
	@DisplayName("holdSeat 실패(좌석 부족) → reserve()가 호출되지 않는다")
	void holdSeat_capacityExhausted_doesNotCallService() {
		redisTemplate.opsForValue().set(seatKey, "0");

		assertThatThrownBy(() ->
			facade.reserve("user@test.com", request(), UUID.randomUUID().toString()))
			.isInstanceOf(ReservationException.class)
			.extracting(e -> ((ReservationException) e).getErrorCode())
			.isEqualTo(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH);

		verify(reservationService, never()).reserve(any(), any(), any());
	}

	@Test
	@DisplayName("정상 성공 → Redis 좌석이 partySize만큼 차감된 채로 유지된다")
	void normalSuccess_seatDecrementedAndNotRestored() {
		Reservation reservation = ReservationFixture.pending()
			.reservationId(1L).slotId(SLOT_ID).partySize(PARTY_SIZE).build();
		given(reservationService.reserve(any(), any(), any()))
			.willReturn(new ReservationCreateResult(reservation, 10_000));
		given(reservationRepository.fetchById(1L)).willReturn(reservation);

		facade.reserve("user@test.com", request(), UUID.randomUUID().toString());

		assertThat(redisTemplate.opsForValue().get(seatKey))
			.isEqualTo(String.valueOf(INITIAL_SEAT - PARTY_SIZE));
	}

	private ReservationRequestDto request() {

		return new ReservationRequestDto(SLOT_ID, DATE, PARTY_SIZE, "note");
	}
}
