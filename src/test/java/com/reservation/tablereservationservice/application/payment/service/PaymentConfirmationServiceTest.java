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
	@DisplayName("결제 확정 성공 - 예약 상태가 CONFIRMED로 변경되고 Payment가 저장된다")
	void confirm_success() {
		// given
		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(pendingReservation);
		given(restaurantSlotRepository.fetchById(SLOT_ID)).willReturn(slot);
		given(restaurantRepository.findById(RESTAURANT_ID)).willReturn(Optional.of(restaurant));
		given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

		// when
		paymentConfirmationService.confirm(queueMessage);

		// then - 예약 상태
		assertThat(pendingReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		verify(reservationRepository).updateStatus(pendingReservation);

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

		// then - 알림 예약
		verify(transactionHandler).runAfterCommit(any());
	}

	@Test
	@DisplayName("결제 확정 실패 - 예약을 찾을 수 없으면 예외가 발생한다")
	void confirm_fail_reservationNotFound() {
		// given
		given(reservationRepository.fetchById(RESERVATION_ID))
				.willThrow(new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "예약"));

		// when & then
		assertThatThrownBy(() -> paymentConfirmationService.confirm(queueMessage))
				.isInstanceOf(ReservationException.class)
				.satisfies(ex -> assertThat(((ReservationException) ex).getErrorCode())
						.isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

		verifyNoInteractions(paymentRepository, notificationService);
	}

	@Test
	@DisplayName("레이스 컨디션 - PAYMENT_FAILED 예약은 조용히 종료한다 (ack)")
	void confirm_raceCondition_paymentFailed_silentlyReturns() {
		// given — 스케줄러가 먼저 PAYMENT_FAILED로 바꾼 상황
		Reservation failedReservation = ReservationFixture.paymentFailed()
				.reservationId(RESERVATION_ID)
				.slotId(SLOT_ID)
				.idempotencyKey(IDEMPOTENCY_KEY)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(failedReservation);

		// when & then
		assertThatCode(() -> paymentConfirmationService.confirm(queueMessage)).doesNotThrowAnyException();
		verifyNoInteractions(paymentRepository, notificationService);
	}

	@Test
	@DisplayName("레이스 컨디션 - CONFIRMED 예약은 조용히 종료한다 (ack)")
	void confirm_raceCondition_alreadyConfirmed_silentlyReturns() {
		// given — 이미 다른 컨슈머가 처리 완료한 상황
		Reservation confirmedReservation = ReservationFixture.confirmed()
				.reservationId(RESERVATION_ID)
				.slotId(SLOT_ID)
				.idempotencyKey(IDEMPOTENCY_KEY)
				.build();

		given(reservationRepository.fetchById(RESERVATION_ID)).willReturn(confirmedReservation);

		// when & then
		assertThatCode(() -> paymentConfirmationService.confirm(queueMessage)).doesNotThrowAnyException();
		verifyNoInteractions(paymentRepository, notificationService);
	}
}
