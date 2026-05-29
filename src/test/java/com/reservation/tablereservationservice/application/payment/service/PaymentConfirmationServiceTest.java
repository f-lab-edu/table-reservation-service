package com.reservation.tablereservationservice.application.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reservation.tablereservationservice.application.notification.NotificationService;
import com.reservation.tablereservationservice.application.payment.PaymentResult;
import com.reservation.tablereservationservice.domain.payment.Payment;
import com.reservation.tablereservationservice.domain.payment.PaymentRepository;
import com.reservation.tablereservationservice.domain.payment.PaymentStatus;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.fixture.ReservationFixture;
import com.reservation.tablereservationservice.fixture.RestaurantFixture;
import com.reservation.tablereservationservice.fixture.RestaurantSlotFixture;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.global.transaction.TransactionHandler;
import com.reservation.tablereservationservice.infrastructure.payment.messaging.PaymentQueueMessage;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationServiceTest {

	private static final Long RESERVATION_ID = 100L;
	private static final Long SLOT_ID = 10L;
	private static final Long RESTAURANT_ID = 200L;
	private static final Long OWNER_ID = 2L;
	private static final String IDEMPOTENCY_KEY = "order-uuid-1234";
	private static final String PAYMENT_KEY = "toss-payment-key";
	private static final int AMOUNT = 10000;
	private static final OffsetDateTime APPROVED_AT = OffsetDateTime.of(2030, 1, 1, 19, 0, 0, 0, ZoneOffset.UTC);

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private RestaurantSlotRepository restaurantSlotRepository;

	@Mock
	private RestaurantRepository restaurantRepository;

	@Mock
	private NotificationService notificationService;

	@Mock
	private TransactionHandler transactionHandler;

	@InjectMocks
	private PaymentConfirmationService paymentConfirmationService;

	private Reservation pendingReservation;
	private Reservation confirmedReservation;
	private RestaurantSlot slot;
	private Restaurant restaurant;
	private PaymentQueueMessage queueMessage;

	@BeforeEach
	void setUp() {
		pendingReservation = ReservationFixture.pending()
				.reservationId(RESERVATION_ID)
				.userId(1L)
				.slotId(SLOT_ID)
				.partySize(2)
				.idempotencyKey(IDEMPOTENCY_KEY)
				.build();

		confirmedReservation = ReservationFixture.confirmed()
				.reservationId(RESERVATION_ID)
				.userId(1L)
				.slotId(SLOT_ID)
				.partySize(2)
				.idempotencyKey(IDEMPOTENCY_KEY)
				.build();

		slot = RestaurantSlotFixture.slot()
				.slotId(SLOT_ID)
				.restaurantId(RESTAURANT_ID)
				.build();

		restaurant = RestaurantFixture.restaurant()
				.restaurantId(RESTAURANT_ID)
				.ownerId(OWNER_ID)
				.name("강남 한상")
				.build();

		PaymentResult result = new PaymentResult(PAYMENT_KEY, IDEMPOTENCY_KEY, AMOUNT, APPROVED_AT, "DONE");
		queueMessage = PaymentQueueMessage.ofConfirmed(pendingReservation, IDEMPOTENCY_KEY, result);
	}

	@Test
	@DisplayName("결제 확정 성공 - PENDING → CONFIRMED atomic UPDATE, Payment 저장, 알림 콜백 등록")
	void confirm_success() {
		// given
		given(reservationRepository.updateStatusConditional(
				RESERVATION_ID,
				ReservationStatus.PENDING,
				ReservationStatus.CONFIRMED
		)).willReturn(1);
		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(confirmedReservation);
		given(restaurantSlotRepository.fetchById(SLOT_ID)).willReturn(slot);
		given(restaurantRepository.findById(RESTAURANT_ID)).willReturn(Optional.of(restaurant));
		given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

		// when
		paymentConfirmationService.confirm(queueMessage);

		// then - atomic UPDATE 호출
		verify(reservationRepository).updateStatusConditional(RESERVATION_ID, ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

		// then - 결제 레코드
		ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
		verify(paymentRepository).save(paymentCaptor.capture());
		Payment saved = paymentCaptor.getValue();
		assertThat(saved.getReservationId()).isEqualTo(RESERVATION_ID);
		assertThat(saved.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
		assertThat(saved.getPaymentKey()).isEqualTo(PAYMENT_KEY);
		assertThat(saved.getAmount()).isEqualTo(AMOUNT);
		assertThat(saved.getStatus()).isEqualTo(PaymentStatus.DONE);
		assertThat(saved.getApprovedAt()).isEqualTo(queueMessage.getApprovedAt());

		// then - 알림 콜백
		verify(transactionHandler).runAfterCommit(any());
	}

	@Test
	@DisplayName("결제 확정 실패 - fetchById 실패 시 예외 전파 (UPDATE 성공 후 SELECT 실패)")
	void confirm_fail_reservationNotFound() {
		// given — UPDATE는 성공했지만 이후 fetchById가 실패하는 방어적 시나리오
		given(reservationRepository.updateStatusConditional(
				RESERVATION_ID,
				ReservationStatus.PENDING,
				ReservationStatus.CONFIRMED
		)).willReturn(1);
		given(reservationRepository.fetchById(RESERVATION_ID))
				.willThrow(new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "예약"));

		// when & then
		assertThatThrownBy(() -> paymentConfirmationService.confirm(queueMessage))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

		verifyNoInteractions(paymentRepository, notificationService, transactionHandler);
	}

	@Test
	@DisplayName("결제 확정 실패 - 레스토랑 없음 시 예외 전파 (Payment 저장 불가)")
	void confirm_fail_restaurantNotFound() {
		given(reservationRepository.updateStatusConditional(
				RESERVATION_ID,
				ReservationStatus.PENDING,
				ReservationStatus.CONFIRMED
		)).willReturn(1);
		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(confirmedReservation);
		given(restaurantSlotRepository.fetchById(SLOT_ID)).willReturn(slot);
		given(restaurantRepository.findById(RESTAURANT_ID)).willReturn(Optional.empty());
		given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

		assertThatThrownBy(() -> paymentConfirmationService.confirm(queueMessage))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException)ex).getErrorCode())
						.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

		verifyNoInteractions(transactionHandler);
	}

	@Test
	@DisplayName("레이스 컨디션 - 0 rows (PAYMENT_FAILED 선점 또는 중복 메시지) → 조용히 종료 (ack)")
	void confirm_raceCondition_silentlyReturns() {
		given(reservationRepository.updateStatusConditional(
				RESERVATION_ID,
				ReservationStatus.PENDING,
				ReservationStatus.CONFIRMED
		)).willReturn(0);

		assertThatCode(() -> paymentConfirmationService.confirm(queueMessage)).doesNotThrowAnyException();
		verify(reservationRepository, never()).fetchById(any());
		verifyNoInteractions(paymentRepository, notificationService, transactionHandler);
	}
}
