package com.reservation.tablereservationservice.application.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.application.notification.NotificationService;
import com.reservation.tablereservationservice.domain.outbox.OutboxRepository;
import com.reservation.tablereservationservice.domain.outbox.OutboxStatus;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacity;
import com.reservation.tablereservationservice.domain.reservation.DailySlotCapacityRepository;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.fixture.DailySlotCapacityFixture;
import com.reservation.tablereservationservice.fixture.ReservationFixture;
import com.reservation.tablereservationservice.fixture.RestaurantFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.fixture.UserFixture;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.global.transaction.TransactionHandler;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.CancelQueueMessage;
import com.reservation.tablereservationservice.infrastructure.redis.ReservationPublisher;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	private static final LocalDate BASE_DATE = LocalDate.of(2030, 1, 1);
	private static final LocalTime BASE_TIME = LocalTime.of(19, 0);

	@Mock
	private UserRepository userRepository;

	@Mock
	private RestaurantSlotRepository restaurantSlotRepository;

	@Mock
	private DailySlotCapacityRepository dailySlotCapacityRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private RestaurantRepository restaurantRepository;

	@Mock
	private ReservationPublisher reservationPublisher;

	@Mock
	private NotificationService notificationService;

	@Mock
	private TransactionHandler transactionHandler;

	@Mock
	private OutboxRepository outboxRepository;

	@Mock
	private ObjectMapper objectMapper;

	@InjectMocks
	private ReservationService reservationService;

	private User customer;
	private RestaurantSlot restaurantSlot;

	@BeforeEach
	void setUp() {
		customer = UserFixture
				.customer()
				.userId(1L)
				.build();

		restaurantSlot = RestaurantSlotFixture.slot()
				.slotId(100L)
				.time(BASE_TIME)
				.build();
	}

	@Test
	@DisplayName("예약 요청 성공 - PENDING 저장 + capacity 차감")
	void create_success() {
		LocalDate date = BASE_DATE;
		LocalDateTime visitAt = LocalDateTime.of(date, restaurantSlot.getTime());
		int partySize = 2;
		String idempotencyKey = "test-idempotency-key";

		DailySlotCapacity capacity = DailySlotCapacityFixture.capacity()
				.slotId(restaurantSlot.getSlotId())
				.date(date)
				.remainingCount(10)
				.build();

		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), date, partySize, "note");

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatusIn(
				eq(customer.getUserId()), eq(visitAt), anyList()
		)).willReturn(false);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), date))
				.willReturn(Optional.of(capacity));
		given(dailySlotCapacityRepository.decreaseRemainingCount(restaurantSlot.getSlotId(), date, partySize))
				.willReturn(1);
		given(reservationRepository.save(any(Reservation.class))).willAnswer(inv -> inv.getArgument(0));

		ReservationCreateResult result = reservationService.reserve(customer.getEmail(), req, idempotencyKey);

		assertThat(result.reservation().getUserId()).isEqualTo(customer.getUserId());
		assertThat(result.reservation().getStatus()).isEqualTo(ReservationStatus.PENDING);
		assertThat(result.depositAmount()).isEqualTo(restaurantSlot.depositAmount(partySize));

		verify(reservationRepository).save(any(Reservation.class));
		verify(dailySlotCapacityRepository).decreaseRemainingCount(restaurantSlot.getSlotId(), date, partySize);
	}

	@Test
	@DisplayName("예약 요청 실패 - 동일 시간대에 이미 예약이 있으면 409 예외가 발생한다.")
	void create_fail_duplicatedTime() {
		LocalDate date = BASE_DATE;
		LocalDateTime visitAt = LocalDateTime.of(date, restaurantSlot.getTime());
		String idempotencyKey = "test-idempotency-key";
		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), date, 2, "note");

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatusIn(
				eq(customer.getUserId()), eq(visitAt), anyList()
		)).willReturn(true);

		assertThatThrownBy(() -> reservationService.reserve(customer.getEmail(), req, idempotencyKey))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESERVATION_DUPLICATED_TIME));

		verifyNoInteractions(dailySlotCapacityRepository);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	@DisplayName("예약 요청 실패 - 슬롯이 오픈되지 않았으면 409 예외가 발생한다.")
	void create_fail_slotNotOpened() {
		LocalDate date = BASE_DATE;
		LocalDateTime visitAt = LocalDateTime.of(date, restaurantSlot.getTime());
		String idempotencyKey = "test-idempotency-key";
		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), date, 2, "note");

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatusIn(
				eq(customer.getUserId()), eq(visitAt), anyList()
		)).willReturn(false);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), date))
				.willReturn(Optional.empty());

		assertThatThrownBy(() -> reservationService.reserve(customer.getEmail(), req, idempotencyKey))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESERVATION_SLOT_NOT_OPENED));

		verify(reservationRepository, never()).save(any());
	}

	@Test
	@DisplayName("예약 요청 실패 - 좌석이 부족하면 409 예외가 발생한다.")
	void create_fail_capacityNotEnough() {
		LocalDate date = BASE_DATE;
		LocalDateTime visitAt = LocalDateTime.of(date, restaurantSlot.getTime());
		String idempotencyKey = "test-idempotency-key";

		DailySlotCapacity capacity = DailySlotCapacityFixture.capacity()
				.slotId(restaurantSlot.getSlotId())
				.date(date)
				.remainingCount(1)
				.build();

		ReservationRequestDto req = new ReservationRequestDto(restaurantSlot.getSlotId(), date, 2, "");

		given(userRepository.fetchByEmail(customer.getEmail())).willReturn(customer);
		given(restaurantSlotRepository.fetchById(restaurantSlot.getSlotId())).willReturn(restaurantSlot);
		given(reservationRepository.existsByUserIdAndVisitAtAndStatusIn(
				eq(customer.getUserId()), eq(visitAt), anyList()
		)).willReturn(false);
		given(dailySlotCapacityRepository.findBySlotIdAndDate(restaurantSlot.getSlotId(), date))
				.willReturn(Optional.of(capacity));

		assertThatThrownBy(() -> reservationService.reserve(customer.getEmail(), req, idempotencyKey))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESERVATION_CAPACITY_NOT_ENOUGH));

		verify(dailySlotCapacityRepository).decreaseRemainingCount(restaurantSlot.getSlotId(), date, 2);
		verify(reservationRepository, never()).save(any());
	}

	@Test
	@DisplayName("예약 취소 대기 설정 성공 - CONFIRMED → CANCEL_PENDING atomic UPDATE + outbox INSERT")
	void markCancelPending_success() throws Exception {
		Long reservationId = 999L;
		given(reservationRepository.updateStatusConditional(
				reservationId, ReservationStatus.CONFIRMED, ReservationStatus.CANCEL_PENDING)).willReturn(1);
		given(objectMapper.writeValueAsString(any())).willReturn("{\"reservationId\":999}");

		reservationService.markCancelPending(reservationId);

		verify(reservationRepository).updateStatusConditional(
				reservationId, ReservationStatus.CONFIRMED, ReservationStatus.CANCEL_PENDING);
		// 상태 전이와 같은 트랜잭션에서 outbox 이벤트가 PENDING으로 저장된다
		verify(outboxRepository).save(argThat(event ->
				event.getAggregateId().equals(reservationId)
						&& event.getEventType().equals(CancelQueueMessage.OUTBOX_EVENT_TYPE)
						&& event.getStatus() == OutboxStatus.PENDING));
	}

	@Test
	@DisplayName("예약 취소 대기 설정 실패 - 다른 요청이 선점 시 409, outbox 미저장")
	void markCancelPending_fail_concurrencyError() {
		Long reservationId = 999L;
		given(reservationRepository.updateStatusConditional(
				reservationId, ReservationStatus.CONFIRMED, ReservationStatus.CANCEL_PENDING)).willReturn(0);

		assertThatThrownBy(() -> reservationService.markCancelPending(reservationId))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESERVATION_CONCURRENCY_ERROR));

		verify(outboxRepository, never()).save(any());
	}

	@Test
	@DisplayName("예약 취소 확정 성공 - CANCELED 전이 + 좌석 복원 + post-commit 콜백 2회 등록")
	void confirmCancel_success() {
		LocalDateTime visitAt = LocalDateTime.of(2030, 6, 1, 19, 0);
		int partySize = 2;
		Long slotId = restaurantSlot.getSlotId();

		Reservation reservation = ReservationFixture.cancelPending()
				.reservationId(1L)
				.slotId(slotId)
				.visitAt(visitAt)
				.partySize(partySize)
				.build();

		Restaurant restaurant = RestaurantFixture.restaurant().build();

		given(reservationRepository.updateStatusConditional(
				1L, ReservationStatus.CANCEL_PENDING, ReservationStatus.CANCELED)).willReturn(1);
		given(restaurantSlotRepository.fetchById(slotId)).willReturn(restaurantSlot);
		given(restaurantRepository.findById(restaurantSlot.getRestaurantId())).willReturn(Optional.of(restaurant));

		reservationService.confirmCancel(reservation);

		verify(dailySlotCapacityRepository).incrementRemainingCount(slotId, visitAt.toLocalDate(), partySize);
		verify(transactionHandler, times(2)).runAfterCommit(any());
	}

	@Test
	@DisplayName("취소 확정 - 레스토랑 조회 실패 시 좌석 복원 콜백 1회만, 알림 생략")
	void confirmCancel_restaurantNotFound_onlySeatReleaseCallback() {
		LocalDateTime visitAt = LocalDateTime.of(2030, 6, 1, 19, 0);
		Long slotId = restaurantSlot.getSlotId();

		Reservation reservation = ReservationFixture.cancelPending()
				.reservationId(1L)
				.slotId(slotId)
				.visitAt(visitAt)
				.partySize(2)
				.build();

		given(reservationRepository.updateStatusConditional(
				1L, ReservationStatus.CANCEL_PENDING, ReservationStatus.CANCELED)).willReturn(1);
		given(restaurantSlotRepository.fetchById(slotId))
				.willThrow(new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "RestaurantSlot"));

		assertThatCode(() -> reservationService.confirmCancel(reservation)).doesNotThrowAnyException();

		verify(dailySlotCapacityRepository).incrementRemainingCount(slotId, visitAt.toLocalDate(), 2);
		verify(transactionHandler, times(1)).runAfterCommit(any());
	}

	@Test
	@DisplayName("취소 확정 중복 메시지 - 0 rows 시 좌석 복원 없이 조용히 종료 (ack)")
	void confirmCancel_duplicate_silentReturn() {
		Reservation reservation = ReservationFixture.cancelPending()
				.reservationId(1L)
				.build();

		given(reservationRepository.updateStatusConditional(
				1L, ReservationStatus.CANCEL_PENDING, ReservationStatus.CANCELED)).willReturn(0);

		assertThatCode(() -> reservationService.confirmCancel(reservation)).doesNotThrowAnyException();

		verify(dailySlotCapacityRepository, never()).incrementRemainingCount(anyLong(), any(), anyInt());
		verify(transactionHandler, never()).runAfterCommit(any());
	}
}
